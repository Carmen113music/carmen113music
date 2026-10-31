package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Un botó rodó de l'editor de so (2.32), com els d'un sinte: arrossegar el dit amunt o avall el gira
 * (tot el recorregut en uns 220 dp), doble toc = torna al valor del so de fàbrica. A sota, el nom i el valor. Les ones es trien tocant (canvien d'una
 * en una) i es dibuixen en lloc del botó.
 */
public class PotView extends View {

    public interface Oient {
        /** El valor ha canviat ({@code acabat}: s'ha aixecat el dit). */
        void canvi(double valor, boolean acabat);
    }

    private final SoParams.Param param;
    private final int color;
    private double valor;
    private double perDefecte;
    private Oient oient;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path cami = new Path();
    private final float d;
    private float xInici, yInici, fInici;
    private boolean cedit;
    private boolean mou;
    private long darrerToc = 0;

    public PotView(Context c, SoParams.Param param, int color) {
        super(c);
        this.param = param;
        this.color = color;
        d = c.getResources().getDisplayMetrics().density;
        p.setTypeface(Ui.MITJA);
        setContentDescription(param.nom);
        setHapticFeedbackEnabled(true);
    }

    public SoParams.Param param() {
        return param;
    }

    public void posaOient(Oient o) {
        oient = o;
    }

    public void posa(double v) {
        valor = v;
        invalidate();
    }

    public void posaPerDefecte(double v) {
        perDefecte = v;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize((int) (64 * d), w), resolveSize((int) (86 * d), h));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float cx = w / 2f, cy = 30 * d, rad = 19 * d;
        if (param.tipus == SoParams.ONA) {
            dibuixaOna(c, cx, cy);
        } else {
            double f = param.fraccio(valor);
            // pista de l'arc (de les 7 a les 5, com un potenciòmetre)
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(3.5f * d);
            r.set(cx - rad - 5 * d, cy - rad - 5 * d, cx + rad + 5 * d, cy + rad + 5 * d);
            p.setColor(Ui.LINIA_FORTA);
            c.drawArc(r, 135, 270, false, p);
            // els bipolars (afinació, envolupant…) s'omplen des del mig
            boolean bipolar = param.min < 0 && param.max > 0;
            double f0 = bipolar ? param.fraccio(0) : 0;
            p.setColor(color);
            float a0 = (float) (135 + 270 * Math.min(f0, f)), a1 = (float) (270 * Math.abs(f - f0));
            if (a1 > 0.5f) c.drawArc(r, a0, a1, false, p);
            // el botó
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF2C2C33);
            c.drawCircle(cx, cy, rad, p);
            p.setColor(0xFF1B1B20);
            c.drawCircle(cx, cy, rad - 3 * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1 * d);
            p.setColor(0xFF45454F);
            c.drawCircle(cx, cy, rad, p);
            // l'indicador
            double ang = Math.toRadians(135 + 270 * f);
            p.setStrokeWidth(2.6f * d);
            p.setColor(Ui.TEXT);
            c.drawLine(cx + (float) Math.cos(ang) * rad * 0.25f, cy + (float) Math.sin(ang) * rad * 0.25f,
                    cx + (float) Math.cos(ang) * (rad - 4 * d), cy + (float) Math.sin(ang) * (rad - 4 * d), p);
        }
        p.setStyle(Paint.Style.FILL);
        // nom i valor
        p.setTextSize(11 * d);
        p.setColor(Ui.SUAU);
        String nom = retalla(param.nom, w - 4 * d);
        c.drawText(nom, cx - p.measureText(nom) / 2, 66 * d, p);
        p.setTextSize(11 * d);
        p.setColor(mou ? color : Ui.TEXT);
        String t = retalla(param.text(valor), w - 2 * d);
        c.drawText(t, cx - p.measureText(t) / 2, 80 * d, p);
    }

    private String retalla(String s, float max) {
        if (p.measureText(s) <= max) return s;
        while (s.length() > 1 && p.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** La forma de l'ona dins una rajola. */
    private void dibuixaOna(Canvas c, float cx, float cy) {
        float w = 44 * d, h = 34 * d;
        r.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1B1B20);
        c.drawRoundRect(r, 7 * d, 7 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * d);
        p.setColor(Ui.alfa(color, 140));
        c.drawRoundRect(r, 7 * d, 7 * d, p);
        float x0 = r.left + 7 * d, x1 = r.right - 7 * d, ym = cy, a = h * 0.3f;
        cami.reset();
        int tipus = (int) Math.round(valor);
        int passos = 40;
        for (int i = 0; i <= passos; i++) {
            float t = i / (float) passos;
            double ph = (t * 2) % 1.0;   // dos cicles
            double y;
            switch (tipus) {
                case Sinte.QUADRADA:
                    y = ph < 0.5 ? 1 : -1;
                    break;
                case Sinte.TRIANGLE:
                    y = 1 - 4 * Math.abs(ph - 0.5);
                    break;
                case Sinte.SINUS:
                    y = Math.sin(2 * Math.PI * ph);
                    break;
                default:
                    y = 2 * ph - 1;
                    break;
            }
            float x = x0 + (x1 - x0) * t, yy = ym - (float) y * a;
            if (i == 0) cami.moveTo(x, yy);
            else cami.lineTo(x, yy);
        }
        p.setStrokeWidth(2 * d);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        c.drawPath(cami, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // encara no s'agafa el dit: si es mou de costat, el panell es desplaça
                xInici = ev.getX();
                yInici = ev.getY();
                fInici = (float) param.fraccio(valor);
                mou = false;
                cedit = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = yInici - ev.getY(), dx = ev.getX() - xInici;
                if (!mou) {
                    if (Math.abs(dx) > 6 * d && Math.abs(dx) > Math.abs(dy)) {
                        cedit = true;   // és un desplaçament del panell
                        return false;
                    }
                    if (Math.abs(dy) < 3 * d || param.tipus == SoParams.ONA) return true;
                    getParent().requestDisallowInterceptTouchEvent(true);   // amunt o avall: és el botó
                    yInici = ev.getY();
                    dy = 0;
                }
                mou = true;
                double nou = param.deFraccio(fInici + dy / (220 * d));
                if (nou != valor) {
                    boolean salt = param.tipus == SoParams.ENTER || param.tipus == SoParams.LLISTA;
                    valor = nou;
                    if (salt) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    invalidate();
                    if (oient != null) oient.canvi(valor, false);
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                long ara = ev.getEventTime();
                if (cedit) return true;
                if (!mou) {
                    if (param.tipus == SoParams.ONA) {
                        valor = (Math.round(valor) + 1) % Sinte.NOMS_ONA.length;   // la següent ona
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    } else if (ara - darrerToc < 350) {
                        valor = perDefecte;                                    // doble toc: el valor de fàbrica
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    } else {
                        darrerToc = ara;
                        return true;
                    }
                }
                mou = false;
                invalidate();
                if (oient != null) oient.canvi(valor, true);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mou = false;
                invalidate();
                if (oient != null) oient.canvi(valor, true);
                return true;
            default:
                return true;
        }
    }
}
