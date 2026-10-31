package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Franja sota l'ona de la fitxa amb el resultat de l'anàlisi:
 * - Acords: blocs amb el nom de cada acord.
 * - Notes: la melodia com un petit piano roll, amb el nom de les notes que hi caben.
 * Està alineada amb l'ona (fa servir la mateixa amplada útil) i el que sona ara es ressalta.
 * Tocar-la o arrossegar-hi el dit salta a aquell punt, com l'ona.
 */
public class AnalisiView extends View {

    private final OnaView ona;
    private final Paint ple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capcal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private Analisi.Resultat res;
    private boolean acords = true;
    private long duradaMs = 1;
    private float progres = -1;
    private int color = Ui.ACCENT;
    private OnaView.OientSalt oient;

    public AnalisiView(Context c, OnaView ona) {
        super(c);
        this.ona = ona;
        text.setTypeface(Ui.MITJA);
        capcal.setStrokeWidth(Ui.dp(c, 2));
        capcal.setColor(Ui.TEXT);
    }

    public void posa(Analisi.Resultat r, long duradaMs) {
        res = r;
        this.duradaMs = Math.max(1, duradaMs);
        invalidate();
    }

    public void setAcords(boolean a) {
        acords = a;
        invalidate();
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    public void setProgres(float f) {
        progres = f;
        invalidate();
    }

    public void setOientSalt(OnaView.OientSalt o) {
        oient = o;
    }

    private float x(long ms) {
        return ona.iniciX() + (float) ms / duradaMs * ona.ampleUtil();
    }

    private long ara() {
        return progres < 0 ? -1 : Math.round(progres * duradaMs);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float rad = Ui.dp(getContext(), 8);
        ple.setColor(Ui.SUPERFICIE);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, ple);
        if (res == null) return;
        long ara = ara();
        if (acords) dibuixaAcords(c, h, ara);
        else dibuixaNotes(c, h, ara);
        if (progres >= 0 && progres < 1f) {
            float xc = x(ara);
            c.drawLine(xc, 2, xc, h - 2, capcal);
        }
    }

    private void dibuixaAcords(Canvas c, float h, long ara) {
        float m = Ui.dp(getContext(), 4), rad = Ui.dp(getContext(), 5);
        if (res.acords.isEmpty()) {
            buit(c, h, "No s'ha trobat cap acord");
            return;
        }
        for (Analisi.Acord a : res.acords) {
            float x0 = x(a.iniciMs) + 1, x1 = x(a.fiMs) - 1;
            if (x1 - x0 < 1) continue;
            boolean actiu = ara >= a.iniciMs && ara < a.fiMs;
            ple.setColor(actiu ? color : (color & 0x00FFFFFF) | 0x38000000);
            r.set(x0, m, x1, h - m);
            c.drawRoundRect(r, rad, rad, ple);
            // nom, si hi cap
            String nom = a.nom();
            float mida = Ui.dp(getContext(), 14);
            text.setTextSize(mida);
            float tw = text.measureText(nom);
            if (tw > x1 - x0 - 6) {
                mida = Ui.dp(getContext(), 10);
                text.setTextSize(mida);
                tw = text.measureText(nom);
            }
            if (tw <= x1 - x0 - 4) {
                text.setColor(actiu ? Ui.SOBRE_COLOR : Ui.TEXT);
                c.drawText(nom, (x0 + x1 - tw) / 2f, h / 2f + mida * 0.36f, text);
            }
        }
    }

    private void dibuixaNotes(Canvas c, float h, long ara) {
        if (res.notes.isEmpty()) {
            buit(c, h, "No s'ha trobat cap nota");
            return;
        }
        int lo = 127, hi = 0;
        for (Analisi.Nota n : res.notes) {
            lo = Math.min(lo, n.midi);
            hi = Math.max(hi, n.midi);
        }
        // com a mínim una octava a la vista, centrada
        while (hi - lo < 11) {
            hi++;
            if (hi - lo < 11) lo--;
        }
        float dalt = Ui.dp(getContext(), 13), baix = Ui.dp(getContext(), 4);
        float files = hi - lo + 1;
        float fh = (h - dalt - baix) / files;
        float gruix = Math.max(Ui.dp(getContext(), 3), Math.min(Ui.dp(getContext(), 7), fh * 0.9f));
        text.setTextSize(Ui.dp(getContext(), 9.5f));
        float ultimaEtiqueta = -1e9f;
        for (Analisi.Nota n : res.notes) {
            float x0 = x(n.iniciMs) + 0.5f, x1 = Math.max(x0 + 2, x(n.fiMs) - 0.5f);
            float yc = dalt + (hi - n.midi + 0.5f) * fh;
            boolean actiu = ara >= n.iniciMs && ara < n.fiMs;
            ple.setColor(actiu ? Ui.TEXT : color);
            r.set(x0, yc - gruix / 2, x1, yc + gruix / 2);
            c.drawRoundRect(r, gruix / 2, gruix / 2, ple);
            // nom a sobre si no trepitja l'anterior
            String nom = n.nom();
            float tw = text.measureText(nom);
            if (x0 >= ultimaEtiqueta + 3 || actiu) {
                float ty = Math.max(Ui.dp(getContext(), 10), yc - gruix / 2 - Ui.dp(getContext(), 2));
                text.setColor(actiu ? Ui.TEXT : Ui.SUAU);
                c.drawText(nom, x0, ty, text);
                ultimaEtiqueta = x0 + tw;
            }
        }
    }

    private void buit(Canvas c, float h, String s) {
        text.setTextSize(Ui.dp(getContext(), 12));
        text.setColor(Ui.SUAU);
        float tw = text.measureText(s);
        c.drawText(s, (getWidth() - tw) / 2f, h / 2f + Ui.dp(getContext(), 4), text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (oient == null || res == null) return super.onTouchEvent(e);
        float f = Math.max(0f, Math.min(1f, (e.getX() - ona.iniciX()) / Math.max(1f, ona.ampleUtil())));
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
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
                progres = f;
                invalidate();
                oient.salta(f, true);
                performClick();
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
