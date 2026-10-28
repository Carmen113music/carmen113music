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
        if (progres >= 0) {
            float x = desp + progres * (caben * pas - separacio);
            capcal.setColor(Ui.TEXT);
            c.drawLine(x, 1, x, h - 1, capcal);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
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
