package cat.riffs;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

/**
 * Metrònom (claqueta): genera els clics amb precisió de mostra i sap
 * en quin moment real (rellotge del sistema) sona cada temps.
 * Encara que el clic no s'hagi de sentir, segueix funcionant en silenci per marcar el temps.
 */
public final class Metronom {

    public static final int MOSTREIG = 44100;

    public static final String SO_AURICULARS = "auriculars";
    public static final String SO_MAI = "mai";

    // ---------- Configuració (desada a les preferències) ----------

    public static final class Config {
        public boolean actiu = false;
        public double bpm = 100;
        public int compas = 4;
        public int compteEnrere = 1;   // compassos
        public String so = SO_AURICULARS;

        public static Config llegeix(Context c) {
            SharedPreferences p = Ui.prefs(c);
            Config k = new Config();
            k.actiu = p.getBoolean("met_actiu", false);
            k.bpm = Ritme.limita(p.getFloat("met_bpm", 100f));
            k.compas = p.getInt("met_compas", 4);
            k.compteEnrere = p.getInt("met_compte", 1);
            // «sempre» ja no existeix: el clic per l'altaveu es gravaria
            k.so = SO_MAI.equals(p.getString("met_so", SO_AURICULARS)) ? SO_MAI : SO_AURICULARS;
            return k;
        }

        public void desa(Context c) {
            Ui.prefs(c).edit()
                    .putBoolean("met_actiu", actiu)
                    .putFloat("met_bpm", (float) bpm)
                    .putInt("met_compas", compas)
                    .putInt("met_compte", compteEnrere)
                    .putString("met_so", so)
                    .apply();
        }

        /** «92 BPM · 4/4 · compte d'1 compàs» */
        public String resum() {
            String s = Math.round(bpm) + " BPM · " + Ritme.nomCompas(compas);
            if (compteEnrere == 1) s += " · compte d'1 compàs";
            else if (compteEnrere > 1) s += " · compte de " + compteEnrere + " compassos";
            return s;
        }
    }

    /** Si hi ha auriculars connectats (amb cable, USB o Bluetooth). */
    public static boolean auriculars(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    /** Si amb aquesta configuració el clic ha de sonar ara. */
    public static boolean ambSo(Context c, Config k) {
        if (SO_MAI.equals(k.so)) return false;
        return auriculars(c);
    }

    // ---------- Funcionament ----------

    private final double bpm;
    private final int compas;
    private final int tempsCompte;     // temps del compte enrere
    private volatile boolean soActiu;
    private final double mostresPerTemps;
    private final short[] clicFort, clicFluix;

    private AudioTrack track;
    private Thread fil;
    private volatile boolean actiu = false;
    private long mostresEscrites = 0;
    private long nanoInici;            // quan es va engegar (per si no hi ha marca de temps)
    private final AudioTimestamp marca = new AudioTimestamp();
    private boolean teMarca = false;

    public Metronom(double bpm, int compas, int compteEnrereCompassos, boolean ambSo) {
        this.bpm = Ritme.limita(bpm);
        this.compas = Math.max(1, compas);
        this.tempsCompte = Math.max(0, compteEnrereCompassos) * this.compas;
        this.soActiu = ambSo;
        this.mostresPerTemps = MOSTREIG * 60.0 / this.bpm;
        clicFort = clic(1760, 0.55);
        clicFluix = clic(1175, 0.38);
    }

    /** Apaga el clic a l'instant (per exemple, si es desconnecten els auriculars). */
    public void silencia() {
        soActiu = false;
    }

    public boolean ambSo() {
        return soActiu;
    }

    public double bpm() {
        return bpm;
    }

    public int compas() {
        return compas;
    }

    public int tempsCompte() {
        return tempsCompte;
    }

    /** Un clic curt: un to que s'apaga en ~35 ms. */
    private static short[] clic(double freq, double volum) {
        int n = (int) (MOSTREIG * 0.035);
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            double env = Math.exp(-i / (MOSTREIG * 0.007));
            double atac = Math.min(1.0, i / 30.0);
            s[i] = (short) (32767 * volum * env * atac * Math.sin(2 * Math.PI * freq * i / MOSTREIG));
        }
        return s;
    }

    /** Engega el metrònom. Torna false si el mòbil no deixa obrir la sortida de so. */
    public boolean comenca() {
        int min = AudioTrack.getMinBufferSize(MOSTREIG, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return false;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(MOSTREIG)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, 4096))
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
        mostresEscrites = 0;
        teMarca = false;
        actiu = true;
        nanoInici = System.nanoTime();
        track.play();
        fil = new Thread(this::bucle, "metronom");
        fil.setPriority(Thread.MAX_PRIORITY);
        fil.start();
        return true;
    }

    private void bucle() {
        short[] tros = new short[512];
        while (actiu) {
            long inici = mostresEscrites;
            java.util.Arrays.fill(tros, (short) 0);
            if (soActiu) Ritme.ompleClics(tros, inici, mostresPerTemps, compas, clicFort, clicFluix);
            int w = track.write(tros, 0, tros.length);
            if (w <= 0) {
                if (!actiu) break;
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }
            mostresEscrites += w;
            if (!teMarca || mostresEscrites % (MOSTREIG / 2) < 512) actualitzaMarca();
        }
    }

    private synchronized void actualitzaMarca() {
        try {
            if (track != null && track.getTimestamp(marca) && marca.nanoTime > 0) teMarca = true;
        } catch (IllegalStateException ignored) {
        }
    }

    /** Moment (System.nanoTime) en què sona la mostra {@code mostra} pels altaveus o auriculars. */
    public synchronized long nanoDeMostra(long mostra) {
        if (teMarca) {
            return marca.nanoTime + Math.round((mostra - marca.framePosition) * 1e9 / MOSTREIG);
        }
        // sense marca: suposa que sona en el moment que s'engega, amb un retard típic de 60 ms
        return nanoInici + 60_000_000L + Math.round(mostra * 1e9 / MOSTREIG);
    }

    /** Moment (System.nanoTime) del temps número {@code temps} (0 = el primer clic). */
    public long nanoDelTemps(long temps) {
        return nanoDeMostra(Math.round(temps * mostresPerTemps));
    }

    /** Moment del primer temps de debò (just després del compte enrere). */
    public long nanoPrimerTemps() {
        return nanoDelTemps(tempsCompte);
    }

    /** Quin temps està sonant ara mateix (pot ser negatiu si encara no ha començat). */
    public long tempsAra() {
        long ara = System.nanoTime();
        long zero = nanoDeMostra(0);
        if (ara < zero) return -1;
        double mostra = (ara - zero) * MOSTREIG / 1e9;
        return (long) Math.floor(mostra / mostresPerTemps);
    }

    public boolean sona() {
        return actiu;
    }

    private AudioManager gestorSo;
    private android.media.AudioDeviceCallback vigilant;

    /** Si es desconnecten els auriculars mentre sona, apaga el clic perquè no passi a l'altaveu. */
    public void vigilaAuriculars(Context c, Runnable enSilenciar) {
        gestorSo = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (gestorSo == null) return;
        vigilant = new android.media.AudioDeviceCallback() {
            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] trets) {
                if (soActiu && !auriculars(c)) {
                    silencia();
                    if (enSilenciar != null) enSilenciar.run();
                }
            }
        };
        gestorSo.registerAudioDeviceCallback(vigilant, new android.os.Handler(android.os.Looper.getMainLooper()));
    }

    public void atura() {
        if (gestorSo != null && vigilant != null) {
            gestorSo.unregisterAudioDeviceCallback(vigilant);
            vigilant = null;
        }
        actiu = false;
        if (fil != null) {
            try {
                fil.join(500);
            } catch (InterruptedException ignored) {
            }
            fil = null;
        }
        if (track != null) {
            try {
                track.pause();
                track.flush();
                track.stop();
            } catch (IllegalStateException ignored) {
            }
            track.release();
            track = null;
        }
    }
}
