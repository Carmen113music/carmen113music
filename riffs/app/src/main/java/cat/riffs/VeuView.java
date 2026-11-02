package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * La roda de notes de la veu en directe (2.44): les 12 notes en cercle, amb la tònica a dalt; les de l'escala, grans
 * i clares; les altres, petites i apagades. La que sona s'encén amb el color de la pista i un punt petit sobre la roda
 * diu cap on va la veu (amb decimals). Al mig, el nom de la nota que sona.
 */
public class VeuView extends View {

    private static final String[] NOMS = {"Do", "Do#", "Re", "Mib", "Mi", "Fa", "Fa#", "Sol", "Lab", "La", "Sib", "Si"};

    private final Paint ple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int color = Ui.SINTE;
    private boolean[] escala;          // null = totes
    private int tonica = 0;
    private int nota = -1;             // la que sona (MIDI) o −1
    private double to = Double.NaN;    // el to de la veu ara (MIDI amb decimals)
    private double nivell = 0;         // 0..1, per a la vora del cercle del mig

    public VeuView(Context c) {
        super(c);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void posaColor(int c) {
        color = c;
        invalidate();
    }

    public void posaEscala(boolean[] e, int tonica) {
        escala = e;
        this.tonica = Math.floorMod(tonica, 12);
        invalidate();
    }

    /** El que passa ara: la nota que sona, el to de la veu i el nivell (0..1). */
    public void posa(int nota, double to, double nivell) {
        if (nota == this.nota && (Double.isNaN(to) == Double.isNaN(this.to)) && Math.abs(to - this.to) < 0.02
                && Math.abs(nivell - this.nivell) < 0.02) return;
        this.nota = nota;
        this.to = to;
        this.nivell = nivell;
        invalidate();
    }

    private float dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private double angle(double pc) {
        return Math.toRadians(-90 + (pc - tonica) * 30);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float rad = Math.min(w, h) / 2f - dp(22);
        if (rad < dp(40)) return;
        // anell de fons
        ple.setStyle(Paint.Style.STROKE);
        ple.setStrokeWidth(dp(1.2f));
        ple.setColor(0xFF2A2A31);
        c.drawCircle(cx, cy, rad, ple);
        ple.setStyle(Paint.Style.FILL);

        int pcAra = nota >= 0 ? Math.floorMod(nota, 12) : -1;
        float rGran = Math.max(dp(13), Math.min(dp(20), rad * 0.17f)), rPetit = rGran * 0.72f;
        for (int pc = 0; pc < 12; pc++) {
            double a = angle(pc);
            float x = (float) (cx + rad * Math.cos(a)), y = (float) (cy + rad * Math.sin(a));
            boolean es = escala == null || escala[pc];
            boolean sona = pc == pcAra;
            float rr = es ? rGran : rPetit;
            if (sona) {
                ple.setColor(Ui.alfa(color, 70));
                c.drawCircle(x, y, rr + dp(5), ple);
                ple.setColor(color);
            } else {
                ple.setColor(es ? 0xFF3A3A43 : 0xFF222227);
            }
            c.drawCircle(x, y, rr, ple);
            text.setTextSize(es ? rGran * 0.62f : rPetit * 0.62f);
            text.setFakeBoldText(es);
            text.setColor(sona ? Ui.SOBRE_COLOR : es ? Ui.TEXT : Ui.APAGAT);
            c.drawText(NOMS[pc], x, y + text.getTextSize() * 0.36f, text);
        }
        // punt: cap on va la veu (pot quedar entre dues notes)
        if (!Double.isNaN(to)) {
            double a = angle(to);
            float rp = rad - rGran - dp(9);
            float x = (float) (cx + rp * Math.cos(a)), y = (float) (cy + rp * Math.sin(a));
            ple.setColor(Ui.TEXT);
            c.drawCircle(x, y, dp(4), ple);
        }
        // cercle del mig: la nota que sona
        float rm = rad * 0.42f;
        ple.setColor(nota >= 0 ? Ui.alfa(color, 40) : 0xFF18181C);
        c.drawCircle(cx, cy, rm, ple);
        if (nivell > 0.01) {
            ple.setStyle(Paint.Style.STROKE);
            ple.setStrokeWidth(dp(3));
            ple.setColor(nota >= 0 ? color : 0xFF4A4A55);
            r.set(cx - rm, cy - rm, cx + rm, cy + rm);
            c.drawArc(r, -90, (float) (360 * Math.min(1, nivell)), false, ple);
            ple.setStyle(Paint.Style.FILL);
        }
        if (nota >= 0) {
            text.setFakeBoldText(true);
            text.setTextSize(rm * 0.55f);
            text.setColor(Ui.TEXT);
            c.drawText(Melodia.nomNota(nota), cx, cy + rm * 0.2f, text);
        } else {
            text.setFakeBoldText(false);
            text.setTextSize(rm * 0.3f);
            text.setColor(Ui.SUAU);
            c.drawText("Canta", cx, cy + rm * 0.1f, text);
        }
        text.setFakeBoldText(false);
    }
}
