package cat.riffs;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

import java.util.Locale;

/**
 * La sortida de so per tocar en directe (sintes i MIDI amb el teclat), feta perquè el so surti tan aviat com es pugui
 * (2.38.3): sona a la freqüència pròpia del mòbil (normalment 48.000 Hz; el sinte fa el so a 44.100 i aquí es converteix),
 * perquè Android només dona la via ràpida si coincideixen; i la cua de so és la més curta possible (dos «paquets» del
 * mòbil), i només creix si el so es talla. Mesura el retard de sortida de debò i el deixa per al diagnòstic.
 */
final class SortidaViva {

    /** El que s'ha vist l'últim cop (per al diagnòstic). */
    static volatile String info = "encara no s'ha tocat cap instrument en directe";
    /** 2.44: el darrer retard de sortida mesurat (ms; −1 = encara no), per a la pantalla de la veu en directe. */
    static volatile double darrerRetardMs = -1;

    private static int natiu = 0, paquet = 0;

    /** Llegeix la freqüència i la mida de paquet del mòbil (es crida en obrir l'Estudi). */
    static void prepara(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            String sr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            String fb = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            if (sr != null) natiu = Integer.parseInt(sr.trim());
            if (fb != null) paquet = Integer.parseInt(fb.trim());
        } catch (RuntimeException ignorat) {
        }
    }

    private final int origen;          // freqüència del so que arriba (44.100)
    private AudioTrack track;
    private int sr, burst, capacitat, memoria;
    private double pas, fase = 0;
    private int anterior = 0;
    private short[] sortida = new short[0];
    private long escrites = 0;
    private int talls = 0;
    private long darreraMesura = 0;
    private double retardMs = -1;
    private final AudioTimestamp marca = new AudioTimestamp();

    SortidaViva(int origen) {
        this.origen = origen;
    }

    boolean obre() {
        sr = natiu > 0 ? natiu : AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        if (sr < 8000 || sr > 192000) sr = origen;
        burst = paquet > 16 && paquet < 4096 ? paquet : Math.max(64, sr / 250);   // sense dada: uns 4 ms
        int min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return false;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, burst * 2 * 4))
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (RuntimeException e) {
            return false;
        }
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            track = null;
            return false;
        }
        capacitat = track.getBufferCapacityInFrames();
        memoria = track.setBufferSizeInFrames(Math.min(capacitat, burst * 2));   // la cua més curta
        if (memoria <= 0) memoria = track.getBufferSizeInFrames();
        pas = origen / (double) sr;
        track.play();
        apunta();
        return true;
    }

    /** Escriu {@code n} mostres (a la freqüència d'origen). Torna false si la sortida ja no funciona. */
    boolean escriu(short[] x, int n) {
        int maxim = (int) Math.ceil(n / pas) + 2;
        if (sortida.length < maxim) sortida = new short[maxim];
        int k = 0;
        // conversió de freqüència (interpolació lineal); amb la mateixa freqüència, passa igual
        while (fase < n - 1) {
            int i = (int) Math.floor(fase);
            double f = fase - i;
            int a = i < 0 ? anterior : x[i], b = x[i + 1];
            sortida[k++] = (short) Math.round(a + (b - a) * f);
            fase += pas;
        }
        fase -= n;
        anterior = x[n - 1];
        int w;
        try {
            w = track.write(sortida, 0, k);
        } catch (RuntimeException e) {
            return false;
        }
        if (w < 0) return false;
        escrites += w;
        // si el so es talla (el mòbil no arriba a temps), la cua creix un paquet
        int t = track.getUnderrunCount();
        if (t > talls) {
            talls = t;
            if (memoria + burst <= capacitat) {
                int nova = track.setBufferSizeInFrames(memoria + burst);
                if (nova > 0) memoria = nova;
            }
        }
        long ara = System.nanoTime();
        if (ara - darreraMesura > 500_000_000L) {
            darreraMesura = ara;
            mesura(ara);
            apunta();
        }
        return true;
    }

    /** El retard de sortida de debò: el que ja s'ha escrit i encara no ha sortit per l'altaveu. */
    private void mesura(long ara) {
        try {
            if (!track.getTimestamp(marca)) return;
        } catch (RuntimeException e) {
            return;
        }
        double sortit = marca.framePosition + (ara - marca.nanoTime) * sr / 1e9;
        double ms = (escrites - sortit) * 1000.0 / sr;
        if (ms > 0 && ms < 2000) retardMs = retardMs < 0 ? ms : retardMs * 0.7 + ms * 0.3;
        if (retardMs > 0) darrerRetardMs = retardMs;
    }

    private void apunta() {
        if (track == null) return;
        String via = track.getPerformanceMode() == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY ? "ràpida" : "normal (Android no ha donat la ràpida)";
        String disp = "?";
        try {
            AudioDeviceInfo d = track.getRoutedDevice();
            if (d != null) disp = nomDispositiu(d.getType());
        } catch (RuntimeException ignorat) {
        }
        info = String.format(Locale.ROOT, "%d Hz (paquet %d) · via %s · cua %d mostres (%.0f ms) · talls %d · sortida %s · retard mesurat %s",
                sr, burst, via, memoria, memoria * 1000.0 / sr, talls, disp,
                retardMs < 0 ? "encara no" : String.format(Locale.ROOT, "%.0f ms", retardMs)).replace('.', ',');
    }

    static String nomDispositiu(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "altaveu";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "auriculars amb cable";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "Bluetooth";
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return "USB";
            default:
                return "tipus " + t;
        }
    }

    void tanca() {
        if (track == null) return;
        apunta();
        try {
            track.stop();
        } catch (RuntimeException ignorat) {
        }
        track.release();
        track = null;
    }
}
