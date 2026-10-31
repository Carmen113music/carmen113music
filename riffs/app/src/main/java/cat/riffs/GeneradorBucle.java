package cat.riffs;

import java.util.ArrayList;
import java.util.List;

/**
 * La frase d'una finestra de sinte sonant en bucle, feta en directe (2.32, editor de so): a diferència del bucle del
 * piano roll (un WAV fet d'entrada), aquí el so es fa mentre sona, i per això els botons del so que es mouen se
 * senten a l'instant ({@link #canviat()}). Serveix per al sinte monofònic i el polifònic. No depèn d'Android.
 */
public final class GeneradorBucle {

    private final double bpm;
    private final int compas;
    private Melodia m;
    private Sinte.So so;
    private boolean poli;
    private int n;
    /** {mostra, 1 = encén / 0 = apaga, nota, força, lligada (mono)}, ordenats. */
    private int[][] ev;
    private int k, p;
    private Poli.Motor motor;
    private Sinte.Veu veu;
    private Efectes fx;
    private volatile boolean refresca;
    private volatile Melodia nova;

    public GeneradorBucle(Melodia m, double bpm, int compas, int pos) {
        this.bpm = bpm;
        this.compas = compas;
        posa(m);
        p = Math.floorMod(pos, n);
        k = primerDes(p);
    }

    public int llargada() {
        return n;
    }

    /** La mostra de la frase que es farà ara. */
    public int posicio() {
        return p;
    }

    /** S'han mogut botons del so (el mateix objecte): es tornen a llegir. */
    public void canviat() {
        refresca = true;
    }

    /** Frase nova (un altre preset, desfer…): es canvia al principi del tros següent. */
    public void posaMelodia(Melodia m) {
        nova = m;
    }

    private void posa(Melodia mel) {
        m = mel;
        n = mel.mostres(bpm, compas);
        ev = esdeveniments(mel, n);
        if (so != mel.so || poli != mel.poliSinte || (motor == null && veu == null)) {
            so = mel.so;
            poli = mel.poliSinte;
            if (poli) {
                motor = new Poli.Motor(so, bpm, true);
                veu = null;
                fx = null;
            } else {
                motor = null;
                veu = new Sinte.Veu(so);
                fx = new Efectes(so, bpm, true);
                if (!mel.notes.isEmpty()) {
                    veu.notaAra = mel.notes.get(0).nota;
                    veu.notaObjectiu = veu.notaAra;
                }
            }
        }
    }

    private int primerDes(int pos) {
        int i = 0;
        while (i < ev.length && ev[i][0] < pos) i++;
        return i;
    }

    static int[][] esdeveniments(Melodia mel, int n) {
        int total = mel.passos();
        List<int[]> r = new ArrayList<>();
        List<Melodia.Nota> ns = mel.notes;
        if (mel.poliSinte) {
            for (Melodia.Nota x : ns) {
                int ini = (int) Math.round((double) x.pas * n / total);
                int fi = Math.max(ini + 1, (int) Math.round((double) x.fi() * n / total));
                r.add(new int[]{ini, 1, x.nota, x.forca, 0});
                r.add(new int[]{Math.min(fi, n - 1), 0, x.nota, 0, 0});
            }
        } else {
            // com Sinte.toca: si la nota següent comença quan l'anterior encara sona, es lliga (glide) i no s'apaga
            for (int i = 0; i < ns.size(); i++) {
                Melodia.Nota x = ns.get(i);
                int ini = (int) Math.round((double) x.pas * n / total);
                int fi = Math.max(ini + 1, (int) Math.round((double) x.fi() * n / total));
                int fiAbans = i == 0 ? -1 : Math.max(1 + (int) Math.round((double) ns.get(i - 1).pas * n / total),
                        (int) Math.round((double) ns.get(i - 1).fi() * n / total));
                r.add(new int[]{ini, 1, x.nota, x.forca, fiAbans >= ini ? 1 : 0});
                int iniSeg = i + 1 < ns.size() ? (int) Math.round((double) ns.get(i + 1).pas * n / total) : Integer.MAX_VALUE;
                if (iniSeg > fi) r.add(new int[]{Math.min(fi, n - 1), 0, x.nota, 0, 0});
            }
        }
        r.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        return r.toArray(new int[0][]);
    }

    /** Fa {@code quants} mostres (amb el volum del so, sense limitar). */
    public void omple(float[] out, int quants) {
        Melodia nv = nova;
        if (nv != null) {
            nova = null;
            posa(nv);
            p = Math.floorMod(p, n);
            k = primerDes(p);
        }
        if (refresca) {
            refresca = false;
            if (motor != null) motor.refresca();
            if (veu != null) veu.refresca();
            if (fx != null) fx.refresca();
        }
        float g = (float) (0.7 * Math.pow(10, so.volumDb / 20));
        for (int i = 0; i < quants; i++) {
            while (k < ev.length && ev[k][0] <= p) {
                int[] e = ev[k++];
                if (motor != null) {
                    if (e[1] == 1) motor.encen(e[2], e[3]);
                    else motor.apaga(e[2]);
                } else {
                    if (e[1] == 1) veu.toca(e[2], e[3], e[4] == 1);
                    else veu.deixa();
                }
            }
            float x;
            if (motor != null) {
                x = motor.mostra();
            } else {
                x = veu.callada() ? 0f : veu.mostra();
                x = fx.processa(x);
            }
            out[i] = x * g;
            if (++p >= n) {
                p = 0;
                k = 0;
            }
        }
    }
}
