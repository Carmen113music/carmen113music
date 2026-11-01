package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;
import java.util.function.Supplier;

/**
 * Les peces pròpies de cada editor de so (2.38): el diagrama de l'algorisme FM, la taula d'ones, els tirants de
 * l'orgue i la corda. Cada una edita uns paràmetres de {@link SoParams} i avisa com els botons rodons.
 */
final class VistesSinte {

    private VistesSinte() {
    }

    /** Base comuna: el so, els paràmetres, el color i l'oient. */
    abstract static class Base extends View {
        final Supplier<Sinte.So> so;
        final List<SoParams.Param> params;
        final int color;
        final EditorSo.Oient oient;
        final float d;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        int textSuau = Ui.SUAU, textFort = Ui.TEXT, fons = 0xFF0E0E10;
        float x0, y0;
        boolean decidit, cedit, mou;

        Base(Context c, Supplier<Sinte.So> so, List<SoParams.Param> params, int color, EditorSo.Oient oient) {
            super(c);
            this.so = so;
            this.params = params;
            this.color = color;
            this.oient = oient;
            d = c.getResources().getDisplayMetrics().density;
            p.setTypeface(Ui.MITJA);
            setHapticFeedbackEnabled(true);
        }

        void posaColors(int textSuau, int textFort, int fons) {
            this.textSuau = textSuau;
            this.textFort = textFort;
            this.fons = fons;
        }

        abstract int ampleDp();

        int altDp() {
            return 86;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(resolveSize((int) (ampleDp() * d), w), resolveSize((int) (altDp() * d), h));
        }

        double val(int i) {
            return params.get(i).llegeix(so.get());
        }

        void posa(int i, double v, boolean acabat) {
            params.get(i).posa(so.get(), v);
            invalidate();
            if (oient != null) oient.canvi(acabat);
        }

        void text(Canvas c, String t, float x, float y, float mida, int col, boolean dreta) {
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(mida * d);
            p.setColor(col);
            c.drawText(t, dreta ? x - p.measureText(t) : x, y, p);
        }
    }

    // ---------- FM: l'algorisme ----------

    /** Els quatre operadors i com es modulen: els que sonen, a baix i plens; tocar = algorisme següent (a l'esquerra, l'anterior). */
    static final class Algorisme extends Base {
        private static final float[][][] POS = {
                {{0, 0}, {0, 1}, {0, 2}, {0, 3}},
                {{0.5f, 0}, {0.5f, 1}, {0, 2}, {1, 2}},
                {{0.5f, 0}, {0, 1}, {0, 2}, {1, 1}},
                {{0.5f, 0}, {0, 1}, {1, 1}, {1, 2}},
                {{0, 0}, {0, 1}, {1, 0}, {1, 1}},
                {{0, 0}, {1, 0}, {2, 0}, {1, 1}},
                {{0, 0}, {1, 0}, {2, 0}, {2, 1}},
                {{0, 0}, {1, 0}, {2, 0}, {3, 0}},
        };
        private final Path cami = new Path();

        Algorisme(Context c, Supplier<Sinte.So> so, List<SoParams.Param> params, int color, EditorSo.Oient oient) {
            super(c, so, params, color, oient);
            setContentDescription("Algorisme FM: toca per canviar-lo");
        }

        @Override
        int ampleDp() {
            return 150;
        }

        @Override
        protected void onDraw(Canvas c) {
            Sinte.So o = so.get();
            int al = Math.max(1, Math.min(8, o.fmAlg));
            float w = getWidth(), h = getHeight();
            float bw = 24 * d, bh = 15 * d, sx = 32 * d, sy = 18.5f * d, bus = h - 4 * d, base = h - 17 * d;
            float[][] pos = POS[al - 1];
            float maxC = 0;
            for (float[] q : pos) maxC = Math.max(maxC, q[0]);
            float ox = (w - maxC * sx) / 2;
            float[] cx = new float[4], cy = new float[4];
            for (int i = 0; i < 4; i++) {
                cx[i] = ox + pos[i][0] * sx;
                cy[i] = base - pos[i][1] * sy;
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Ui.alfa(color, 170));
            // moduladors → modulats
            int[] mods = Sinte.FM_MODS[al - 1];
            for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
                if ((mods[i] & (1 << j)) == 0) continue;
                cami.reset();
                cami.moveTo(cx[j], cy[j] + bh / 2);
                cami.lineTo(cx[j], (cy[j] + bh / 2 + cy[i] - bh / 2) / 2);
                cami.lineTo(cx[i], (cy[j] + bh / 2 + cy[i] - bh / 2) / 2);
                cami.lineTo(cx[i], cy[i] - bh / 2);
                c.drawPath(cami, p);
            }
            // portadors → sortida
            float bx0 = Float.MAX_VALUE, bx1 = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) if (o.fmPortador(i)) {
                c.drawLine(cx[i], cy[i] + bh / 2, cx[i], bus, p);
                bx0 = Math.min(bx0, cx[i]);
                bx1 = Math.max(bx1, cx[i]);
            }
            c.drawLine(bx0 - 6 * d, bus, bx1 + 6 * d, bus, p);
            // realimentació de l'operador 4
            float fx = cx[3] + bw / 2, fy = cy[3];
            cami.reset();
            cami.moveTo(fx, fy);
            cami.lineTo(fx + 5 * d, fy);
            cami.lineTo(fx + 5 * d, fy - bh / 2 - 4 * d);
            cami.lineTo(cx[3], fy - bh / 2 - 4 * d);
            cami.lineTo(cx[3], fy - bh / 2);
            p.setColor(Ui.alfa(color, o.fmRealim > 0 ? 220 : 70));
            c.drawPath(cami, p);
            // caixes
            for (int i = 0; i < 4; i++) {
                boolean car = o.fmPortador(i);
                r.set(cx[i] - bw / 2, cy[i] - bh / 2, cx[i] + bw / 2, cy[i] + bh / 2);
                p.setStyle(Paint.Style.FILL);
                p.setColor(car ? color : fons);
                c.drawRoundRect(r, 3 * d, 3 * d, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.4f * d);
                p.setColor(color);
                c.drawRoundRect(r, 3 * d, 3 * d, p);
                String n = Integer.toString(i + 1);
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(11 * d);
                p.setColor(car ? 0xFF0B0F08 : color);
                c.drawText(n, cx[i] - p.measureText(n) / 2, cy[i] + 4 * d, p);
            }
            text(c, "ALG " + al, w - 2 * d, 11 * d, 12, color, true);
            text(c, "‹", 2 * d, h / 2, 16, textSuau, false);
            text(c, "›", w - 2 * d, h / 2, 16, textSuau, true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = ev.getX();
                    y0 = ev.getY();
                    cedit = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(ev.getX() - x0) > 8 * d || Math.abs(ev.getY() - y0) > 8 * d) {
                        cedit = true;
                        return false;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (cedit) return true;
                    int al = Math.max(1, Math.min(8, so.get().fmAlg));
                    al = ev.getX() < getWidth() / 3f ? (al + 6) % 8 + 1 : al % 8 + 1;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    posa(0, al, true);
                    return true;
                default:
                    return true;
            }
        }
    }

    // ---------- Wavetable: la taula ----------

    /** Les formes de la taula una darrere l'altra (en perspectiva) i la que sona ara, ressaltada. Arrossegar = posició; tocar = taula següent. */
    static final class Taula extends Base {
        private final Path cami = new Path();
        private int taulaFeta = -1;
        private float[][] fons12;
        private float posInici;

        Taula(Context c, Supplier<Sinte.So> so, List<SoParams.Param> params, int color, EditorSo.Oient oient) {
            super(c, so, params, color, oient);
            setContentDescription("Taula d'ones: arrossega per canviar la posició, toca per canviar de taula");
        }

        @Override
        int ampleDp() {
            return 190;
        }

        private void dibuixa(Canvas c, float[] f, float ox, float oy, float w, float a) {
            cami.reset();
            for (int i = 0; i < f.length; i++) {
                float x = ox + w * i / (f.length - 1), y = oy - f[i] * a;
                if (i == 0) cami.moveTo(x, y);
                else cami.lineTo(x, y);
            }
            c.drawPath(cami, p);
        }

        @Override
        protected void onDraw(Canvas c) {
            Sinte.So o = so.get();
            int t = o.wtTaula;
            if (t != taulaFeta) {
                taulaFeta = t;
                fons12 = new float[12][];
                for (int i = 0; i < 12; i++) fons12[i] = Taules.forma(t, i / 11.0);
            }
            float w = getWidth(), h = getHeight();
            float pw = w * 0.62f, a = h * 0.16f, dx = (w - pw - 8 * d) / 11f, dy = -(h * 0.32f) / 11f;
            float bx = 4 * d, by = h * 0.72f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            for (int i = 11; i >= 0; i--) {
                p.setStrokeWidth(1 * d);
                p.setColor(Ui.alfa(color, 40 + 4 * (11 - i)));
                dibuixa(c, fons12[i], bx + dx * i, by + dy * i, pw, a);
            }
            double pos = o.wtPos;
            float fp = (float) (pos * 11);
            p.setStrokeWidth(2.2f * d);
            p.setColor(color);
            dibuixa(c, Taules.forma(t, pos), bx + dx * fp, by + dy * fp, pw, a);
            text(c, Taules.NOMS[t].toUpperCase(java.util.Locale.ROOT), 4 * d, 11 * d, 11, color, false);
            text(c, Math.round(pos * 100) + " %", w - 2 * d, 11 * d, 11, textFort, true);
            String aj = Taules.AJUDA[t];
            p.setTextSize(9 * d);
            while (aj.length() > 4 && p.measureText(aj + "\u2026") > w - 8 * d) aj = aj.substring(0, aj.length() - 1).trim();
            if (!aj.equals(Taules.AJUDA[t])) aj = aj + "\u2026";
            text(c, aj, 4 * d, h - 2 * d, 9, textSuau, false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    x0 = ev.getX();
                    posInici = (float) so.get().wtPos;
                    mou = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getX() - x0;
                    if (!mou && Math.abs(dx) < 4 * d) return true;
                    mou = true;
                    posa(1, Math.max(0, Math.min(1, posInici + dx / (getWidth() * 0.9f))), false);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (mou) {
                        oient.canvi(true);
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        posa(0, (so.get().wtTaula + 1) % Taules.NOMS.length, true);
                    }
                    mou = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (mou) oient.canvi(true);
                    mou = false;
                    return true;
                default:
                    return true;
            }
        }
    }

    // ---------- Orgue: els tirants ----------

    /** Nou tirants com els d'un orgue de rodes: s'estiren cap avall (0 a 8). Colors de debò: marrons, blancs i negres. */
    static final class Tirants extends Base {
        private static final int[] COLOR = {0xFF8B5A2B, 0xFF8B5A2B, 0xFFEDE6D6, 0xFFEDE6D6, 0xFF2A2A2E, 0xFFEDE6D6, 0xFF2A2A2E, 0xFF2A2A2E, 0xFFEDE6D6};
        private int col = -1;

        Tirants(Context c, Supplier<Sinte.So> so, List<SoParams.Param> params, int color, EditorSo.Oient oient) {
            super(c, so, params, color, oient);
            setContentDescription("Tirants de l'orgue: arrossega'ls amunt o avall");
        }

        @Override
        int ampleDp() {
            return 9 * 32;
        }

        @Override
        int altDp() {
            return 104;
        }

        private float dalt() {
            return 16 * d;
        }

        private float pas() {
            return (getHeight() - dalt() - 14 * d) / 8f;
        }

        @Override
        protected void onDraw(Canvas c) {
            float cw = getWidth() / 9f, top = dalt(), st = pas();
            for (int i = 0; i < 9; i++) {
                float cx = cw * i + cw / 2;
                int v = (int) Math.round(val(i));
                String n = params.get(i).nom;
                text(c, n, cx - measure(n, 10) / 2, 11 * d, 10, i == col ? color : textSuau, false);
                // ranura
                r.set(cx - 5 * d, top, cx + 5 * d, top + 8 * st + 12 * d);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF0A0806);
                c.drawRoundRect(r, 3 * d, 3 * d, p);
                // la barra estirada, amb les marques 1–8
                float fi = top + v * st;
                r.set(cx - 3.5f * d, top, cx + 3.5f * d, fi);
                p.setColor(0xFFB8A27E);
                if (v > 0) c.drawRect(r, p);
                p.setColor(0xFF3B2E1E);
                for (int k = 1; k <= v; k++) c.drawRect(cx - 3.5f * d, top + k * st - 0.6f * d, cx + 3.5f * d, top + k * st + 0.6f * d, p);
                // el pom
                r.set(cx - cw * 0.36f, fi, cx + cw * 0.36f, fi + 12 * d);
                p.setColor(COLOR[i]);
                c.drawRoundRect(r, 3 * d, 3 * d, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth((i == col ? 2 : 1) * d);
                p.setColor(i == col ? color : 0xFF000000);
                c.drawRoundRect(r, 3 * d, 3 * d, p);
                String t = Integer.toString(v);
                boolean fosc = COLOR[i] == 0xFF2A2A2E || COLOR[i] == 0xFF8B5A2B;
                text(c, t, cx - measure(t, 10) / 2, fi + 9.5f * d, 10, fosc ? 0xFFF2F1F5 : 0xFF1A1A1A, false);
            }
        }

        private float measure(String t, float mida) {
            p.setTextSize(mida * d);
            return p.measureText(t);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = ev.getX();
                    y0 = ev.getY();
                    decidit = false;
                    cedit = false;
                    col = Math.max(0, Math.min(8, (int) (ev.getX() / (getWidth() / 9f))));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getX() - x0, dy = ev.getY() - y0;
                    if (!decidit) {
                        if (Math.abs(dx) > 6 * d && Math.abs(dx) > Math.abs(dy)) {
                            cedit = true;
                            col = -1;
                            invalidate();
                            return false;
                        }
                        if (Math.abs(dy) < 3 * d) return true;
                        decidit = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    int v = Math.max(0, Math.min(8, Math.round((ev.getY() - dalt() - 6 * d) / pas())));
                    if (v != (int) Math.round(val(col))) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        posa(col, v, false);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!cedit && col >= 0) {
                        if (!decidit && ev.getActionMasked() == MotionEvent.ACTION_UP) {
                            // un toc: el tirant va fins on s'ha tocat
                            int v = Math.max(0, Math.min(8, Math.round((ev.getY() - dalt() - 6 * d) / pas())));
                            posa(col, v, true);
                        } else {
                            oient.canvi(true);
                        }
                    }
                    col = -1;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
    }

    // ---------- Corda pinçada ----------

    /** La corda entre el pont i el capdamunt, deformada on es pinça. Arrossegar = on es pinça; tocar = com es toca (púa, dit…). */
    static final class Corda extends Base {
        private final Path cami = new Path();
        private float posInici;

        Corda(Context c, Supplier<Sinte.So> so, List<SoParams.Param> params, int color, EditorSo.Oient oient) {
            super(c, so, params, color, oient);
            setContentDescription("Corda: arrossega per triar on es pinça, toca per canviar com es toca");
        }

        @Override
        int ampleDp() {
            return 190;
        }

        @Override
        protected void onDraw(Canvas c) {
            Sinte.So o = so.get();
            float w = getWidth(), h = getHeight();
            float xa = 14 * d, xb = w - 14 * d, yc = h * 0.58f;
            // pont i capdamunt
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF6B5A3A);
            r.set(xa - 8 * d, yc - 14 * d, xa - 2 * d, yc + 14 * d);
            c.drawRoundRect(r, 2 * d, 2 * d, p);
            r.set(xb + 2 * d, yc - 14 * d, xb + 8 * d, yc + 14 * d);
            c.drawRoundRect(r, 2 * d, 2 * d, p);
            float xp = xa + (xb - xa) * (float) o.ksPosicio;
            float alt = (float) (8 + 12 * o.ksBrillant) * d;
            // ombra de la vibració
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1 * d);
            p.setColor(Ui.alfa(color, 60));
            cami.reset();
            cami.moveTo(xa, yc);
            cami.lineTo(xp, yc + alt * 0.6f);
            cami.lineTo(xb, yc);
            c.drawPath(cami, p);
            // la corda
            p.setStrokeWidth(2 * d);
            p.setColor(color);
            cami.reset();
            cami.moveTo(xa, yc);
            cami.lineTo(xp, yc - alt);
            cami.lineTo(xb, yc);
            c.drawPath(cami, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(xp, yc - alt, 4 * d, p);
            String tipus = SoParams.NOMS_EXCITACIO[Math.max(0, Math.min(3, o.ksTipus))];
            text(c, tipus.toUpperCase(java.util.Locale.ROOT), 4 * d, 11 * d, 11, color, false);
            text(c, "a " + Math.round(o.ksPosicio * 100) + " % del pont", w - 2 * d, 11 * d, 10, textFort, true);
            String aj = "toca: com es toca · arrossega: on es pinça";
            text(c, aj, w / 2 - measure(aj) / 2, h - 3 * d, 9, textSuau, false);
        }

        private float measure(String t) {
            p.setTextSize(9 * d);
            return p.measureText(t);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    x0 = ev.getX();
                    posInici = (float) so.get().ksPosicio;
                    mou = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getX() - x0;
                    if (!mou && Math.abs(dx) < 4 * d) return true;
                    mou = true;
                    posa(0, Math.max(0.03, Math.min(0.5, posInici + dx / (getWidth() - 28 * d))), false);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (mou) {
                        oient.canvi(true);
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        posa(1, (so.get().ksTipus + 1) % SoParams.NOMS_EXCITACIO.length, true);
                    }
                    mou = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (mou) oient.canvi(true);
                    mou = false;
                    return true;
                default:
                    return true;
            }
        }
    }

    // ---------- La placa amb el nom del sinte ----------

    /** Una franja vertical amb el nom del sinte (com la serigrafia d'un aparell). */
    static final class Placa extends View {
        private final String text;
        private final int color, fons;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        Placa(Context c, String text, int color, int fons) {
            super(c);
            this.text = text;
            this.color = color;
            this.fons = fons;
            d = c.getResources().getDisplayMetrics().density;
            p.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
            p.setLetterSpacing(0.25f);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension((int) (26 * d), resolveSize((int) (100 * d), h));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(fons);
            c.drawRoundRect(new RectF(0, 0, w, h), 8 * d, 8 * d, p);
            p.setColor(color);
            c.drawRect(w - 4 * d, 10 * d, w - 2 * d, h - 10 * d, p);
            p.setTextSize(11 * d);
            float tw = p.measureText(text);
            c.save();
            c.rotate(-90);
            c.drawText(text, -h / 2 - tw / 2, w / 2 + 4 * d - 2 * d, p);
            c.restore();
        }
    }
}
