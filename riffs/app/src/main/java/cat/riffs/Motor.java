package cat.riffs;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

import java.util.Map;

/**
 * Reprodueix la barreja d'un projecte en temps real. Les edicions es noten mentre sona
 * (cada tros es barreja amb l'estat actual del projecte).
 */
public final class Motor {

    private static final int MOSTREIG = Mesclador.MOSTREIG;
    private static final int TROS = 1024;

    private final Projecte projecte;
    private final Map<String, short[]> audio;
    private AudioTrack track;
    private Thread fil;
    private volatile boolean sona = false;
    private volatile long mostraInici;       // on comença a la línia de temps
    private volatile long mostresEscrites;
    private final AudioTimestamp marca = new AudioTimestamp();
    private boolean teMarca = false;
    private long nanoInici;

    // clic del metrònom (per gravar des de l'Estudi); només ha de sonar amb auriculars
    private volatile boolean clic = false;
    private volatile float volumClic = 1f;
    // silenci total (el motor continua corrent per marcar el temps, però no surt cap so)
    private volatile boolean silenci = false;
    // vúmetres: pic de cada pista al darrer tros barrejat
    private volatile float[] nivells = new float[0];
    private short[] clicFort, clicFluix;

    // Bucle (2.36): quan arriba a bucleFi torna a bucleIni (en mostres de la línia de temps; -1 = sense bucle)
    private volatile long bucleIni = -1, bucleFi = -1;
    private long linia;                       // la mostra de la línia de temps que s'escriurà ara
    /** Salts: {mostres escrites, mostra de la línia de temps} cada cop que la reproducció torna a l'inici del bucle. */
    private final java.util.ArrayList<long[]> salts = new java.util.ArrayList<>();
    /** Estat dels efectes de cada pista (2.37); es buida en començar a sonar. */
    private final java.util.Map<Projecte.Pista, Insercions.Proces> fx = new java.util.HashMap<>();

    /** Els efectes de la pista que sonen ara (per veure quant comprimeix el compressor), o null. */
    public Insercions.Proces proces(Projecte.Pista t) {
        synchronized (projecte) {
            return fx.get(t);
        }
    }

    public Motor(Projecte projecte, Map<String, short[]> audio) {
        this.projecte = projecte;
        this.audio = audio;
    }

    public boolean sona() {
        return sona;
    }

    /** Pic (0..1 o més) de la pista {@code i} al que s'acaba de barrejar, per al vúmetre. */
    public float nivell(int i) {
        float[] nv = nivells;
        return sona && !silenci && i >= 0 && i < nv.length ? nv[i] : 0f;
    }

    /** Silencia tot el que surt (pistes i clic). Per gravar sense auriculars: per l'altaveu es gravaria. */
    public void posaSilenci(boolean s) {
        silenci = s;
    }

    /** Volum del clic: 0 (res) a 1,5. */
    public void posaVolumClic(float v) {
        volumClic = Math.max(0f, Math.min(1.5f, v));
    }

    /** Fa sonar (o no) el clic del metrònom al tempo del projecte, barrejat amb les pistes. */
    public void posaClic(boolean actiu) {
        if (actiu && clicFort == null) {
            clicFort = Ritme.clic(Latencia.FREQ_CLIC, 0.55, MOSTREIG);
            clicFluix = Ritme.clic(1175, 0.38, MOSTREIG);
        }
        clic = actiu;
    }

    /**
     * Bucle de la reproducció, en ms de la línia de temps ({@code iniMs} &lt; 0 = sense bucle). Es pot canviar
     * mentre sona: el canvi es nota en arribar al final del bucle nou.
     */
    public void posaBucle(long iniMs, long fiMs) {
        if (iniMs < 0 || fiMs <= iniMs) {
            bucleIni = -1;
            bucleFi = -1;
        } else {
            bucleIni = Mesclador.msAMostra(iniMs);
            bucleFi = Mesclador.msAMostra(fiMs);
        }
    }

    /** Comença a sonar des de {@code ms} de la línia de temps. */
    public boolean comenca(long ms) {
        atura();
        int min = AudioTrack.getMinBufferSize(MOSTREIG, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return false;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(MOSTREIG)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min * 2, TROS * 8))
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
        mostraInici = Math.max(0, Mesclador.msAMostra(ms));
        synchronized (this) {
            teMarca = false;
        }
        mostresEscrites = 0;
        teMarca = false;
        linia = mostraInici;
        synchronized (projecte) {
            fx.clear();
        }
        synchronized (salts) {
            salts.clear();
            salts.add(new long[]{0, mostraInici});
        }
        nanoInici = System.nanoTime();
        sona = true;
        track.play();
        fil = new Thread(this::bucle, "motor");
        fil.setPriority(Thread.MAX_PRIORITY);
        fil.start();
        return true;
    }

    private void bucle() {
        float[] mescla = new float[TROS];
        float[] resta = new float[TROS];
        short[] tros = new short[TROS];
        short[] clics = new short[TROS];
        while (sona) {
            double mostresPerTemps;
            int compas;
            // fins on es pot escriure abans de tornar a l'inici del bucle
            long ini = bucleIni, fi = bucleFi;
            if (ini >= 0 && fi > ini + TROS && linia >= fi) {
                // el bucle s'ha marcat per darrere del que sona: hi salta de seguida
                synchronized (salts) {
                    salts.add(new long[]{mostresEscrites, ini});
                    while (salts.size() > 16) salts.remove(0);
                }
                linia = ini;
            }
            boolean ambBucle = ini >= 0 && fi > ini + TROS && linia < fi;
            int n1 = ambBucle ? (int) Math.min(TROS, fi - linia) : TROS;
            int n2 = TROS - n1;
            synchronized (projecte) {
                float[] nv = nivells.length == projecte.pistes.size() ? nivells : new float[projecte.pistes.size()];
                Mesclador.mescla(projecte, audio, linia, mescla, n1, nv, null, fx);
                if (n2 > 0) {
                    Mesclador.mescla(projecte, audio, ini, resta, n2, nv, null, fx);
                    System.arraycopy(resta, 0, mescla, n1, n2);
                }
                nivells = nv;
                mostresPerTemps = MOSTREIG * 60.0 / projecte.bpm;
                compas = projecte.compas;
            }
            if (clic) {
                if (n2 == 0) {
                    java.util.Arrays.fill(clics, (short) 0);
                    Ritme.ompleClics(clics, linia, mostresPerTemps, compas, clicFort, clicFluix);
                } else {
                    short[] a = new short[n1], b = new short[n2];
                    Ritme.ompleClics(a, linia, mostresPerTemps, compas, clicFort, clicFluix);
                    Ritme.ompleClics(b, ini, mostresPerTemps, compas, clicFort, clicFluix);
                    System.arraycopy(a, 0, clics, 0, n1);
                    System.arraycopy(b, 0, clics, n1, n2);
                }
                float vc = volumClic / 32768f;
                for (int i = 0; i < TROS; i++) mescla[i] += clics[i] * vc;
            }
            Mesclador.a16bits(mescla, tros, TROS);
            if (silenci) java.util.Arrays.fill(tros, (short) 0);
            int w = track.write(tros, 0, TROS);
            if (w <= 0) break;
            if (n2 > 0) {
                synchronized (salts) {
                    salts.add(new long[]{mostresEscrites + n1, ini});
                    while (salts.size() > 16) salts.remove(0);
                }
                linia = ini + n2;
            } else {
                linia += w;
            }
            mostresEscrites += w;
            if (!teMarca || mostresEscrites % (MOSTREIG / 2) < TROS) actualitzaMarca();
        }
    }

    /** La mostra de la línia de temps que correspon a {@code escrites} mostres des que ha començat a sonar. */
    private long liniaDe(long escrites) {
        synchronized (salts) {
            for (int i = salts.size() - 1; i >= 0; i--) {
                long[] s = salts.get(i);
                if (escrites >= s[0]) return s[1] + (escrites - s[0]);
            }
        }
        return mostraInici + escrites;
    }

    private synchronized void actualitzaMarca() {
        try {
            if (track != null && track.getTimestamp(marca) && marca.nanoTime > 0) teMarca = true;
        } catch (IllegalStateException ignored) {
        }
    }

    /** Què s'està sentint ara mateix, en ms de la línia de temps. */
    public synchronized long posicioMs() {
        double mostra;
        long ara = System.nanoTime();
        if (teMarca) {
            mostra = marca.framePosition + (ara - marca.nanoTime) * MOSTREIG / 1e9;
        } else {
            mostra = (ara - nanoInici) * MOSTREIG / 1e9 - MOSTREIG * 0.08;
        }
        mostra = Math.max(0, Math.min(mostra, mostresEscrites));
        return Mesclador.mostraAMs(liniaDe((long) mostra));
    }

    /**
     * Moment (System.nanoTime) en què surt per l'altaveu o els auriculars el moment {@code ms}
     * de la línia de temps (si sona des d'abans d'aquest moment).
     */
    public synchronized long nanoDeLinia(long ms) {
        long mostra = Mesclador.msAMostra(ms) - mostraInici;   // dins el que ha sonat
        if (teMarca) {
            return marca.nanoTime + Math.round((mostra - marca.framePosition) * 1e9 / MOSTREIG);
        }
        return nanoInici + 80_000_000L + Math.round(mostra * 1e9 / MOSTREIG);
    }

    /** Per on surt el so: «altaveu», «bluetooth», «cable» o «altre» (null si encara no se sap). */
    public synchronized String tipusSortida() {
        AudioDeviceInfo d = track != null ? track.getRoutedDevice() : null;
        return d == null ? null : Metronom.tipus(d);
    }

    /** Nom del dispositiu de sortida (per exemple el dels auriculars), si el mòbil el diu. */
    public synchronized String nomSortida() {
        AudioDeviceInfo d = track != null ? track.getRoutedDevice() : null;
        if (d == null || d.getProductName() == null) return "";
        return d.getProductName().toString();
    }

    public void atura() {
        sona = false;
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
