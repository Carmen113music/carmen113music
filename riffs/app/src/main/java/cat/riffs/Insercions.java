package cat.riffs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Efectes d'inserció d'una pista de l'Estudi (2.37), com les insercions del Cubase, en aquest ordre:
 * filtre de greus → EQ de 3 bandes → compressor → delay → reverb. Van abans del fader (pre-fader).
 * Una pista amb tot apagat sona exactament igual que sense efectes (no es processa).
 * No depèn d'Android: es prova amb Java d'escriptori.
 */
public final class Insercions {

    public static final int SR = Mesclador.MOSTREIG;

    // ---------- Paràmetres (es desen al projecte) ----------
    public boolean hpf = false;
    public double hpfHz = 80;              // filtre de greus (passa-alts, 12 dB/oct)
    public boolean eq = false;
    public double greusDb = 0, greusHz = 120;      // prestatge de greus
    public double mitjosDb = 0, mitjosHz = 1000;   // campana del mig (Q 1)
    public double agutsDb = 0, agutsHz = 8000;     // prestatge d'aguts
    public boolean comp = false;
    public double llindarDb = -18, ratio = 3, atacMs = 10, deixaMs = 150, guanyDb = 0;
    public boolean delay = false;
    public double delayQuant = 0.25, delayTemps = 0.75, delayRep = 0.35;
    public boolean reverb = false;
    public double reverbQuant = 0.25, reverbSala = 0.5;

    /** Augmenta cada cop que canvia algun valor (el processador refà els seus càlculs). */
    public volatile int versio = 0;

    public void canviat() {
        versio++;
    }

    /** Hi ha algun efecte encès? */
    public boolean actiu() {
        return hpf || eq || comp || delay || reverb;
    }

    /** «Greus · EQ · Comp · Rev» (per a la tira de la taula). */
    public String resum() {
        List<String> r = new ArrayList<>();
        if (hpf) r.add("Greus");
        if (eq) r.add("EQ");
        if (comp) r.add("Comp");
        if (delay) r.add("Delay");
        if (reverb) r.add("Rev");
        return String.join(" · ", r);
    }

    public Insercions copia() {
        Insercions c = new Insercions();
        c.deText(text());
        return c;
    }

    // ---------- Text (per desar-ho al projecte en una sola línia) ----------

    public String text() {
        StringBuilder sb = new StringBuilder();
        afegeix(sb, "hpf", hpf ? 1 : 0);
        afegeix(sb, "hpfHz", hpfHz);
        afegeix(sb, "eq", eq ? 1 : 0);
        afegeix(sb, "gDb", greusDb);
        afegeix(sb, "gHz", greusHz);
        afegeix(sb, "mDb", mitjosDb);
        afegeix(sb, "mHz", mitjosHz);
        afegeix(sb, "aDb", agutsDb);
        afegeix(sb, "aHz", agutsHz);
        afegeix(sb, "comp", comp ? 1 : 0);
        afegeix(sb, "ll", llindarDb);
        afegeix(sb, "rat", ratio);
        afegeix(sb, "at", atacMs);
        afegeix(sb, "de", deixaMs);
        afegeix(sb, "gu", guanyDb);
        afegeix(sb, "dl", delay ? 1 : 0);
        afegeix(sb, "dlQ", delayQuant);
        afegeix(sb, "dlT", delayTemps);
        afegeix(sb, "dlR", delayRep);
        afegeix(sb, "rv", reverb ? 1 : 0);
        afegeix(sb, "rvQ", reverbQuant);
        afegeix(sb, "rvS", reverbSala);
        return sb.toString();
    }

    private static void afegeix(StringBuilder sb, String k, double v) {
        if (sb.length() > 0) sb.append(';');
        sb.append(k).append('=').append(String.format(Locale.ROOT, "%.4f", v));
    }

    public void deText(String s) {
        if (s == null) return;
        for (String part : s.split(";")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            String k = part.substring(0, i).trim();
            double v;
            try {
                v = Double.parseDouble(part.substring(i + 1).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            switch (k) {
                case "hpf": hpf = v != 0; break;
                case "hpfHz": hpfHz = limita(v, 20, 500); break;
                case "eq": eq = v != 0; break;
                case "gDb": greusDb = limita(v, -15, 15); break;
                case "gHz": greusHz = limita(v, 40, 400); break;
                case "mDb": mitjosDb = limita(v, -15, 15); break;
                case "mHz": mitjosHz = limita(v, 200, 6000); break;
                case "aDb": agutsDb = limita(v, -15, 15); break;
                case "aHz": agutsHz = limita(v, 2000, 14000); break;
                case "comp": comp = v != 0; break;
                case "ll": llindarDb = limita(v, -48, 0); break;
                case "rat": ratio = limita(v, 1, 20); break;
                case "at": atacMs = limita(v, 0.5, 100); break;
                case "de": deixaMs = limita(v, 20, 1000); break;
                case "gu": guanyDb = limita(v, 0, 24); break;
                case "dl": delay = v != 0; break;
                case "dlQ": delayQuant = limita(v, 0, 1); break;
                case "dlT": delayTemps = limita(v, 0.25, 2); break;
                case "dlR": delayRep = limita(v, 0, 0.9); break;
                case "rv": reverb = v != 0; break;
                case "rvQ": reverbQuant = limita(v, 0, 1); break;
                case "rvS": reverbSala = limita(v, 0, 1); break;
                default: break;
            }
        }
        canviat();
    }

    private static double limita(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    // ---------- Processador (l'estat que es manté mentre sona) ----------

    /** Filtre biquad (fórmules del «cookbook» de R. Bristow-Johnson). */
    static final class Biquad {
        double b0 = 1, b1, b2, a1, a2;
        double x1, x2, y1, y2;

        float processa(float x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return (float) y;
        }

        void posa(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        void passaAlts(double hz, double q) {
            double w = 2 * Math.PI * hz / SR, cs = Math.cos(w), al = Math.sin(w) / (2 * q);
            posa((1 + cs) / 2, -(1 + cs), (1 + cs) / 2, 1 + al, -2 * cs, 1 - al);
        }

        void campana(double hz, double db, double q) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * hz / SR, cs = Math.cos(w), al = Math.sin(w) / (2 * q);
            posa(1 + al * A, -2 * cs, 1 - al * A, 1 + al / A, -2 * cs, 1 - al / A);
        }

        void prestatgeGreus(double hz, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * hz / SR, cs = Math.cos(w), sn = Math.sin(w);
            double al = sn / 2 * Math.sqrt(2), ra = 2 * Math.sqrt(A) * al;
            posa(A * ((A + 1) - (A - 1) * cs + ra), 2 * A * ((A - 1) - (A + 1) * cs), A * ((A + 1) - (A - 1) * cs - ra),
                    (A + 1) + (A - 1) * cs + ra, -2 * ((A - 1) + (A + 1) * cs), (A + 1) + (A - 1) * cs - ra);
        }

        void prestatgeAguts(double hz, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * hz / SR, cs = Math.cos(w), sn = Math.sin(w);
            double al = sn / 2 * Math.sqrt(2), ra = 2 * Math.sqrt(A) * al;
            posa(A * ((A + 1) + (A - 1) * cs + ra), -2 * A * ((A - 1) + (A + 1) * cs), A * ((A + 1) + (A - 1) * cs - ra),
                    (A + 1) - (A - 1) * cs + ra, 2 * ((A - 1) - (A + 1) * cs), (A + 1) - (A - 1) * cs - ra);
        }
    }

    /** L'estat dels efectes d'una pista mentre sona (els filtres i les cues de reverb i delay). */
    public static final class Proces {
        private final Insercions p;
        private int versio = -1;
        private double bpm = -1;
        private final Biquad hp = new Biquad(), g = new Biquad(), m = new Biquad(), a = new Biquad();
        private double env = 0;          // detector del compressor (dB per sobre del llindar)
        private double atac, deixa, guany;
        private final Sinte.So so = new Sinte.So();
        private Efectes efectes;
        /** Quant redueix el compressor ara mateix (dB, 0 o negatiu), per al panell. */
        public volatile float reduccioDb = 0;

        public Proces(Insercions p) {
            this.p = p;
        }

        private void refresca(double bpmProjecte) {
            if (p.hpf) hp.passaAlts(p.hpfHz, 0.707);
            if (p.eq) {
                g.prestatgeGreus(p.greusHz, p.greusDb);
                m.campana(p.mitjosHz, p.mitjosDb, 1.0);
                a.prestatgeAguts(p.agutsHz, p.agutsDb);
            }
            atac = Math.exp(-1.0 / (p.atacMs / 1000.0 * SR));
            deixa = Math.exp(-1.0 / (p.deixaMs / 1000.0 * SR));
            guany = Math.pow(10, p.guanyDb / 20);
            so.chorus = 0;
            so.delay = p.delay ? p.delayQuant : 0;
            so.delayTemps = p.delayTemps;
            so.delayRepeticio = p.delayRep;
            so.reverb = p.reverb ? p.reverbQuant : 0;
            so.reverbMida = p.reverbSala;
            if (efectes == null || bpmProjecte != bpm) {
                efectes = new Efectes(so, bpmProjecte, true);
                bpm = bpmProjecte;
            } else {
                efectes.refresca();
            }
            versio = p.versio;
        }

        /** Processa {@code n} mostres de {@code buf} al seu lloc. */
        public void processa(float[] buf, int n, double bpmProjecte) {
            if (versio != p.versio || bpmProjecte != bpm) refresca(bpmProjecte);
            boolean fxCua = p.delay || p.reverb;
            float red = 0;
            for (int i = 0; i < n; i++) {
                float x = buf[i];
                if (p.hpf) x = hp.processa(x);
                if (p.eq) x = a.processa(m.processa(g.processa(x)));
                if (p.comp) {
                    double nivell = 20 * Math.log10(Math.abs(x) + 1e-9);
                    double sobre = nivell - p.llindarDb;
                    // genoll suau de 6 dB
                    double obj;
                    if (sobre <= -3) obj = 0;
                    else if (sobre >= 3) obj = sobre * (1 - 1 / p.ratio);
                    else obj = (1 - 1 / p.ratio) * (sobre + 3) * (sobre + 3) / 12;
                    env = obj > env ? atac * env + (1 - atac) * obj : deixa * env + (1 - deixa) * obj;
                    x = (float) (x * Math.pow(10, -env / 20) * guany);
                    if (-env < red) red = (float) -env;
                }
                if (fxCua) x = efectes.processa(x);
                buf[i] = x;
            }
            reduccioDb = red;
        }
    }
}
