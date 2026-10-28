package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Dibuixa una forma d'ona amb barres.
 * - En directe (mentre es grava): les barres noves entren per la dreta.
 * - D'un fitxer: tota l'ona, amb la part ja escoltada pintada i el capçal; es pot tocar per saltar.
 */
public class OnaView extends View {

    public interface OientMarca {
        /** ms = on queda el primer temps; acabat = l'usuari ha deixat anar el dit */
        void marca(long ms, boolean acabat);
    }

    public interface OientSalt {
        /** fraccio de 0 a 1; acabat = l'usuari ha deixat anar el dit */
        void salta(float fraccio, boolean acabat);
    }

    private final Paint barra = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capcal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private float[] pics;           // mode fitxer
    private float[] directe = new float[400];
    private int nDirecte = 0;
    private boolean modeDirecte = false;

    private float progres = -1;     // 0..1, o -1 sense capçal
    private int colorActiu = Ui.ACCENT;
    private int colorInactiu = Ui.LINIA_FORTA;
    private float ampleBarra, separacio;
    private OientSalt oient;
    private boolean tocant = false;

    // Graella de temps i bandera del primer temps (mode fitxer)
    private final Paint linia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandera = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path triangle = new android.graphics.Path();
    private double bpm = 0;
    private int compas = 4;
    private long primerMs = -1;
    private long duradaMs = 0;
    private boolean editaMarca = false;
    private OientMarca oientMarca;

    public OnaView(Context c) {
        super(c);
        ampleBarra = Ui.dp(c, 3);
        separacio = Ui.dp(c, 2);
        capcal.setStrokeWidth(Ui.dp(c, 2));
        capcal.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setBarres(float amplePx, float separacioPx) {
        ampleBarra = amplePx;
        separacio = separacioPx;
        invalidate();
    }

    public void setColors(int actiu, int inactiu) {
        colorActiu = actiu;
        colorInactiu = inactiu;
        invalidate();
    }

    /** Mode fitxer. */
    public void setPics(float[] p) {
        modeDirecte = false;
        pics = p;
        invalidate();
    }

    public void setProgres(float f) {
        if (tocant) return;
        progres = f;
        invalidate();
    }

    public void setOientSalt(OientSalt o) {
        oient = o;
    }

    /** Graella: tempo, compàs, primer temps (-1 = sense) i durada total de la gravació. */
    public void setGraella(double bpm, int compas, long primerMs, long duradaMs) {
        this.bpm = bpm;
        this.compas = Math.max(1, compas);
        this.primerMs = primerMs;
        this.duradaMs = duradaMs;
        invalidate();
    }

    /** Mentre està activat, tocar l'ona mou la bandera en lloc de saltar. */
    public void setEditaMarca(boolean e, OientMarca o) {
        editaMarca = e;
        oientMarca = o;
        invalidate();
    }

    public boolean editaMarca() {
        return editaMarca;
    }

    /** Mode directe: buida l'ona. */
    public void comencaDirecte() {
        modeDirecte = true;
        nDirecte = 0;
        invalidate();
    }

    /** Mode directe: afegeix un nivell nou (0..1). */
    public void afegeix(float pic) {
        modeDirecte = true;
        // escala aproximada a decibels perquè els sons fluixos també es vegin
        float db = pic <= 0.0001f ? -60f : (float) (20 * Math.log10(pic));
        float v = Math.max(0.03f, Math.min(1f, (db + 54f) / 54f));
        if (nDirecte == directe.length) {
            System.arraycopy(directe, 1, directe, 0, directe.length - 1);
            nDirecte--;
        }
        directe[nDirecte++] = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float mig = h / 2f;
        float pas = ampleBarra + separacio;
        int caben = Math.max(1, (int) ((w + separacio) / pas));
        float radi = ampleBarra / 2f;

        if (modeDirecte) {
            // punts de base
            barra.setColor(colorInactiu);
            for (int i = 0; i < caben; i++) {
                float x = i * pas;
                r.set(x, mig - radi, x + ampleBarra, mig + radi);
                c.drawRoundRect(r, radi, radi, barra);
            }
            barra.setColor(colorActiu);
            int mostrar = Math.min(caben, nDirecte);
            for (int k = 0; k < mostrar; k++) {
                float v = directe[nDirecte - mostrar + k];
                int col = caben - mostrar + k;
                float x = col * pas;
                float alt = Math.max(ampleBarra, v * (h - 2));
                r.set(x, mig - alt / 2f, x + ampleBarra, mig + alt / 2f);
                c.drawRoundRect(r, radi, radi, barra);
            }
            return;
        }

        if (pics == null || pics.length == 0) {
            barra.setColor(colorInactiu);
            for (int i = 0; i < caben; i++) {
                float x = i * pas;
                r.set(x, mig - radi, x + ampleBarra, mig + radi);
                c.drawRoundRect(r, radi, radi, barra);
            }
            return;
        }

        float desp = (w - (caben * pas - separacio)) / 2f;
        float ampleOna = caben * pas - separacio;

        // Graella de temps (darrere de l'ona)
        if (bpm > 0 && duradaMs > 0) {
            double mpt = 60000.0 / bpm;
            double origen = primerMs >= 0 ? primerMs : 0;
            long kInici = (long) Math.ceil(-origen / mpt);
            float pxPerTemps = (float) (mpt / duradaMs * ampleOna);
            boolean nomesCompassos = pxPerTemps < Ui.dp(getContext(), 6);
            for (long k = kInici; ; k++) {
                double t = origen + k * mpt;
                if (t > duradaMs) break;
                boolean esCompas = Math.floorMod(k, (long) compas) == 0;
                if (nomesCompassos && !esCompas) continue;
                float x = desp + (float) (t / duradaMs) * ampleOna;
                linia.setStrokeWidth(esCompas ? Ui.dp(getContext(), 1.2f) : Ui.dp(getContext(), 0.8f));
                linia.setColor(esCompas ? 0x40FFFFFF : 0x1CFFFFFF);
                c.drawLine(x, 0, x, h, linia);
            }
        }

        for (int i = 0; i < caben; i++) {
            // pic més alt dins el tros que toca a aquesta barra
            int a = (int) ((long) i * pics.length / caben);
            int b = Math.max(a + 1, (int) ((long) (i + 1) * pics.length / caben));
            float v = 0;
            for (int k = a; k < b && k < pics.length; k++) v = Math.max(v, pics[k]);
            float alt = Math.max(ampleBarra, (0.08f + 0.92f * v) * (h - 2));
            float x = desp + i * pas;
            boolean escoltat = progres >= 0 && (i + 0.5f) / caben <= progres;
            barra.setColor(escoltat ? colorActiu : colorInactiu);
            r.set(x, mig - alt / 2f, x + ampleBarra, mig + alt / 2f);
            c.drawRoundRect(r, radi, radi, barra);
        }
        if (progres >= 0 && progres < 1f && !editaMarca) {
            float x = desp + progres * ampleOna;
            capcal.setColor(Ui.TEXT);
            c.drawLine(x, 1, x, h - 1, capcal);
        }

        // Bandera del primer temps
        if (primerMs >= 0 && duradaMs > 0) {
            float x = desp + (float) primerMs / duradaMs * ampleOna;
            float m = Ui.dp(getContext(), editaMarca ? 11 : 8);
            bandera.setColor(Ui.ACCENT);
            bandera.setStrokeWidth(Ui.dp(getContext(), editaMarca ? 2.5f : 2f));
            c.drawLine(x, 0, x, h, bandera);
            triangle.reset();
            triangle.moveTo(x, 0);
            triangle.lineTo(x + m * 1.2f, m * 0.55f);
            triangle.lineTo(x, m * 1.1f);
            triangle.close();
            c.drawPath(triangle, bandera);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (editaMarca && oientMarca != null && duradaMs > 0 && !modeDirecte) {
            float pas = ampleBarra + separacio;
            int caben = Math.max(1, (int) ((getWidth() + separacio) / pas));
            float ampleOna = caben * pas - separacio;
            float desp = (getWidth() - ampleOna) / 2f;
            float f = Math.max(0f, Math.min(1f, (e.getX() - desp) / Math.max(1f, ampleOna)));
            long ms = Math.round(f * duradaMs);
            int acc = e.getActionMasked();
            if (acc == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            if (acc == MotionEvent.ACTION_DOWN || acc == MotionEvent.ACTION_MOVE || acc == MotionEvent.ACTION_UP) {
                primerMs = ms;
                invalidate();
                oientMarca.marca(ms, acc == MotionEvent.ACTION_UP);
                if (acc == MotionEvent.ACTION_UP) performClick();
            }
            return true;
        }
        if (oient == null || modeDirecte || pics == null) return super.onTouchEvent(e);
        float f = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, getWidth())));
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tocant = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                progres = f;
                invalidate();
                oient.salta(f, false);
                return true;
            case MotionEvent.ACTION_MOVE:
                progres = f;
                invalidate();
                oient.salta(f, false);
                return true;
            case MotionEvent.ACTION_UP:
                tocant = false;
                progres = f;
                invalidate();
                oient.salta(f, true);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                tocant = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
