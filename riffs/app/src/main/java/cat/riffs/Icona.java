package cat.riffs;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Icones de línia dibuixades amb codi (sobre una quadrícula de 24×24),
 * perquè l'app no depengui dels emojis de cada mòbil.
 */
public class Icona extends Drawable {

    public static final int MIC = 1, GUITARRA = 2, CARPETA = 3, HISTORIAL = 4, AJUSTOS = 5,
            PLAY = 6, PAUSA = 7, STOP = 8, ENRERE5 = 9, BUCLE = 10, COMPARTIR = 11, PAPERERA = 12,
            MES = 13, XEVRO = 14, ENRERE = 15, ESTUDI = 16, PUNTS = 17, MOURE = 18, LLETRA = 19,
            NOTA = 20, BIBLIOTECA = 21, LLAPIS = 22, METRONOM = 23, BANDERA = 24, MENYS = 25, DUPLICA = 26, PUJA = 27, BAIXA = 28, REC = 29, GRAELLA = 30, PISTES = 31, TAULA = 32, BATERIA = 33;

    private final int tipus;
    private final Paint traz = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();
    private final RectF r = new RectF();
    private final int midaPx;

    public Icona(int tipus, int color, int midaPx) {
        this.tipus = tipus;
        this.midaPx = midaPx;
        traz.setStyle(Paint.Style.STROKE);
        traz.setStrokeWidth(1.9f);
        traz.setStrokeCap(Paint.Cap.ROUND);
        traz.setStrokeJoin(Paint.Join.ROUND);
        ple.setStyle(Paint.Style.FILL);
        ple.setStrokeJoin(Paint.Join.ROUND);
        ple.setTextAlign(Paint.Align.CENTER);
        setColor(color);
    }

    public void setColor(int color) {
        traz.setColor(color);
        ple.setColor(color);
        invalidateSelf();
    }

    @Override
    public int getIntrinsicWidth() {
        return midaPx;
    }

    @Override
    public int getIntrinsicHeight() {
        return midaPx;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float m = Math.min(b.width(), b.height());
        c.save();
        c.translate(b.left + (b.width() - m) / 2f, b.top + (b.height() - m) / 2f);
        c.scale(m / 24f, m / 24f);
        dibuixa(c);
        c.restore();
    }

    private void linia(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, traz);
    }

    private void carpeta(Canvas c) {
        p.reset();
        p.moveTo(3, 7);
        p.lineTo(3, 6);
        p.quadTo(3, 5, 4, 5);
        p.lineTo(9, 5);
        p.lineTo(11, 7);
        p.lineTo(20, 7);
        p.quadTo(21, 7, 21, 8);
        p.lineTo(21, 18);
        p.quadTo(21, 19, 20, 19);
        p.lineTo(4, 19);
        p.quadTo(3, 19, 3, 18);
        p.close();
        c.drawPath(p, traz);
    }

    private void dibuixa(Canvas c) {
        switch (tipus) {
            case MIC:
                r.set(9, 2.5f, 15, 14);
                c.drawRoundRect(r, 3, 3, traz);
                r.set(5, 4, 19, 18);
                c.drawArc(r, 180, -180, false, traz);
                linia(c, 12, 18, 12, 21);
                linia(c, 8.5f, 21, 15.5f, 21);
                break;
            case GUITARRA:
                // guitarra acústica inclinada: dues corbes del cos, boca, pont i mànec
                c.save();
                c.rotate(40, 12, 12);
                c.translate(0, 0.6f);
                r.set(7.5f, 12.5f, 16.5f, 21.5f);
                c.drawArc(r, -54.4f, 288.8f, false, traz);
                r.set(8.8f, 8.3f, 15.2f, 14.7f);
                c.drawArc(r, 144.9f, 250.2f, false, traz);
                c.drawCircle(12, 14.6f, 1.25f, ple);
                linia(c, 10.6f, 18.4f, 13.4f, 18.4f);
                linia(c, 12, 8.3f, 12, 2.6f);
                r.set(10.9f, 0.6f, 13.1f, 3.2f);
                c.drawRoundRect(r, 0.8f, 0.8f, ple);
                c.restore();
                break;
            case CARPETA:
                carpeta(c);
                break;
            case BIBLIOTECA:
                r.set(3.5f, 4, 7.5f, 20);
                c.drawRoundRect(r, 1, 1, traz);
                r.set(9.5f, 4, 13.5f, 20);
                c.drawRoundRect(r, 1, 1, traz);
                p.reset();
                p.moveTo(15.6f, 5.2f);
                p.lineTo(19.4f, 4.2f);
                p.lineTo(21.4f, 19.6f);
                p.lineTo(17.6f, 20.6f);
                p.close();
                c.drawPath(p, traz);
                break;
            case HISTORIAL:
                c.drawCircle(12, 12, 9, traz);
                linia(c, 12, 7, 12, 12);
                linia(c, 12, 12, 15.5f, 14);
                break;
            case AJUSTOS:
                linia(c, 4, 7, 6.6f, 7);
                c.drawCircle(9, 7, 2.3f, traz);
                linia(c, 11.4f, 7, 20, 7);
                linia(c, 4, 17, 12.6f, 17);
                c.drawCircle(15, 17, 2.3f, traz);
                linia(c, 17.4f, 17, 20, 17);
                break;
            case PLAY:
                p.reset();
                p.moveTo(7.5f, 4.8f);
                p.lineTo(19.2f, 12);
                p.lineTo(7.5f, 19.2f);
                p.close();
                c.drawPath(p, ple);
                c.drawPath(p, traz);
                break;
            case PAUSA:
                r.set(6.5f, 4.5f, 10.5f, 19.5f);
                c.drawRoundRect(r, 1.2f, 1.2f, ple);
                r.set(13.5f, 4.5f, 17.5f, 19.5f);
                c.drawRoundRect(r, 1.2f, 1.2f, ple);
                break;
            case STOP:
                r.set(6, 6, 18, 18);
                c.drawRoundRect(r, 2.2f, 2.2f, ple);
                break;
            case ENRERE5:
                r.set(3.5f, 3.5f, 20.5f, 20.5f);
                c.drawArc(r, 215, 290, false, traz);
                linia(c, 3.6f, 3.5f, 4.6f, 8.6f);
                linia(c, 4.6f, 8.6f, 9.6f, 7.6f);
                ple.setTextSize(8.5f);
                ple.setFakeBoldText(true);
                c.drawText("5", 12, 15, ple);
                break;
            case BUCLE:
                p.reset();
                p.moveTo(17, 2.5f);
                p.lineTo(20.5f, 6);
                p.lineTo(17, 9.5f);
                c.drawPath(p, traz);
                p.reset();
                p.moveTo(3.5f, 11);
                p.lineTo(3.5f, 10);
                p.quadTo(3.5f, 6, 7.5f, 6);
                p.lineTo(20.5f, 6);
                c.drawPath(p, traz);
                p.reset();
                p.moveTo(7, 21.5f);
                p.lineTo(3.5f, 18);
                p.lineTo(7, 14.5f);
                c.drawPath(p, traz);
                p.reset();
                p.moveTo(20.5f, 13);
                p.lineTo(20.5f, 14);
                p.quadTo(20.5f, 18, 16.5f, 18);
                p.lineTo(3.5f, 18);
                c.drawPath(p, traz);
                break;
            case COMPARTIR:
                p.reset();
                p.moveTo(4.5f, 12.5f);
                p.lineTo(4.5f, 19);
                p.quadTo(4.5f, 21, 6.5f, 21);
                p.lineTo(17.5f, 21);
                p.quadTo(19.5f, 21, 19.5f, 19);
                p.lineTo(19.5f, 12.5f);
                c.drawPath(p, traz);
                linia(c, 12, 3, 12, 15);
                p.reset();
                p.moveTo(7.5f, 7.5f);
                p.lineTo(12, 3);
                p.lineTo(16.5f, 7.5f);
                c.drawPath(p, traz);
                break;
            case PISTES:
                // taulell: tres pistes amb clips
                r.set(3, 5, 15, 8.5f);
                c.drawRoundRect(r, 1.2f, 1.2f, ple);
                r.set(7, 10.25f, 21, 13.75f);
                c.drawRoundRect(r, 1.2f, 1.2f, ple);
                r.set(3, 15.5f, 12, 19);
                c.drawRoundRect(r, 1.2f, 1.2f, ple);
                r.set(14, 15.5f, 21, 19);
                c.drawRoundRect(r, 1.2f, 1.2f, ple);
                break;
            case TAULA:
                // taula de so: tres faders
                linia(c, 6, 3.5f, 6, 20.5f);
                linia(c, 12, 3.5f, 12, 20.5f);
                linia(c, 18, 3.5f, 18, 20.5f);
                r.set(3.5f, 12, 8.5f, 15.5f);
                c.drawRoundRect(r, 1, 1, ple);
                r.set(9.5f, 6, 14.5f, 9.5f);
                c.drawRoundRect(r, 1, 1, ple);
                r.set(15.5f, 14.5f, 20.5f, 18);
                c.drawRoundRect(r, 1, 1, ple);
                break;
            case GRAELLA:
                // graella (snap): quadre amb línies
                r.set(3.5f, 4.5f, 20.5f, 19.5f);
                c.drawRoundRect(r, 2, 2, traz);
                linia(c, 9.2f, 4.5f, 9.2f, 19.5f);
                linia(c, 14.8f, 4.5f, 14.8f, 19.5f);
                linia(c, 3.5f, 12, 20.5f, 12);
                break;
            case REC:
                // el cercle de gravar
                c.drawCircle(12, 12, 6.5f, ple);
                break;
            case PUJA:
                linia(c, 12, 19.5f, 12, 5);
                p.reset();
                p.moveTo(6.5f, 10.5f);
                p.lineTo(12, 5);
                p.lineTo(17.5f, 10.5f);
                c.drawPath(p, traz);
                break;
            case BAIXA:
                linia(c, 12, 4.5f, 12, 19);
                p.reset();
                p.moveTo(6.5f, 13.5f);
                p.lineTo(12, 19);
                p.lineTo(17.5f, 13.5f);
                c.drawPath(p, traz);
                break;
            case DUPLICA:
                // dos clips, el de davant amb un +
                p.reset();
                p.moveTo(4.5f, 15.5f);
                p.lineTo(4.5f, 5.5f);
                p.quadTo(4.5f, 3.5f, 6.5f, 3.5f);
                p.lineTo(14.5f, 3.5f);
                c.drawPath(p, traz);
                r.set(8.5f, 7.5f, 20.5f, 20.5f);
                c.drawRoundRect(r, 2, 2, traz);
                linia(c, 14.5f, 10.5f, 14.5f, 17.5f);
                linia(c, 11, 14, 18, 14);
                break;
            case PAPERERA:
                linia(c, 3.5f, 6.5f, 20.5f, 6.5f);
                p.reset();
                p.moveTo(18.5f, 6.5f);
                p.lineTo(18.5f, 19.5f);
                p.quadTo(18.5f, 21.5f, 16.5f, 21.5f);
                p.lineTo(7.5f, 21.5f);
                p.quadTo(5.5f, 21.5f, 5.5f, 19.5f);
                p.lineTo(5.5f, 6.5f);
                c.drawPath(p, traz);
                p.reset();
                p.moveTo(8.5f, 6.5f);
                p.lineTo(8.5f, 4.5f);
                p.quadTo(8.5f, 2.5f, 10.5f, 2.5f);
                p.lineTo(13.5f, 2.5f);
                p.quadTo(15.5f, 2.5f, 15.5f, 4.5f);
                p.lineTo(15.5f, 6.5f);
                c.drawPath(p, traz);
                linia(c, 10, 11, 10, 17);
                linia(c, 14, 11, 14, 17);
                break;
            case MES:
                linia(c, 12, 5, 12, 19);
                linia(c, 5, 12, 19, 12);
                break;
            case XEVRO:
                p.reset();
                p.moveTo(9, 5.5f);
                p.lineTo(15.5f, 12);
                p.lineTo(9, 18.5f);
                c.drawPath(p, traz);
                break;
            case ENRERE:
                p.reset();
                p.moveTo(15, 5.5f);
                p.lineTo(8.5f, 12);
                p.lineTo(15, 18.5f);
                c.drawPath(p, traz);
                break;
            case ESTUDI:
                // pistes d'un multipista i el capçal
                r.set(3, 4.5f, 12, 8.5f);
                c.drawRoundRect(r, 1.5f, 1.5f, traz);
                r.set(8, 10, 20.5f, 14);
                c.drawRoundRect(r, 1.5f, 1.5f, traz);
                r.set(3, 15.5f, 15, 19.5f);
                c.drawRoundRect(r, 1.5f, 1.5f, traz);
                break;
            case PUNTS:
                c.drawCircle(12, 5, 1.7f, ple);
                c.drawCircle(12, 12, 1.7f, ple);
                c.drawCircle(12, 19, 1.7f, ple);
                break;
            case MOURE:
                carpeta(c);
                linia(c, 8.5f, 13, 15.5f, 13);
                p.reset();
                p.moveTo(13, 10.5f);
                p.lineTo(15.5f, 13);
                p.lineTo(13, 15.5f);
                c.drawPath(p, traz);
                break;
            case LLETRA:
                r.set(5, 2.5f, 19, 21.5f);
                c.drawRoundRect(r, 2, 2, traz);
                linia(c, 8.5f, 8, 15.5f, 8);
                linia(c, 8.5f, 12, 15.5f, 12);
                linia(c, 8.5f, 16, 12.5f, 16);
                break;
            case NOTA:
                c.drawCircle(7.5f, 17.5f, 2.8f, traz);
                c.drawCircle(17.5f, 15.5f, 2.8f, traz);
                linia(c, 10.3f, 17.5f, 10.3f, 5);
                linia(c, 20.3f, 15.5f, 20.3f, 3);
                linia(c, 10.3f, 5, 20.3f, 3);
                linia(c, 10.3f, 9, 20.3f, 7);
                break;
            case LLAPIS:
                p.reset();
                p.moveTo(16.5f, 3.5f);
                p.lineTo(20.5f, 7.5f);
                p.lineTo(8.5f, 19.5f);
                p.lineTo(3.5f, 20.5f);
                p.lineTo(4.5f, 15.5f);
                p.close();
                c.drawPath(p, traz);
                linia(c, 13.5f, 6.5f, 17.5f, 10.5f);
                break;
            case METRONOM:
                p.reset();
                p.moveTo(9.5f, 3);
                p.lineTo(14.5f, 3);
                p.lineTo(19, 21);
                p.lineTo(5, 21);
                p.close();
                c.drawPath(p, traz);
                linia(c, 6.2f, 16.5f, 17.8f, 16.5f);
                linia(c, 12, 16.5f, 16.8f, 6.5f);
                c.drawCircle(15.4f, 9.4f, 1.3f, ple);
                break;
            case BANDERA:
                linia(c, 6, 21, 6, 3);
                p.reset();
                p.moveTo(6, 3.5f);
                p.lineTo(18.5f, 7.5f);
                p.lineTo(6, 11.5f);
                p.close();
                c.drawPath(p, ple);
                c.drawPath(p, traz);
                break;
            case MENYS:
                linia(c, 5, 12, 19, 12);
                break;
            case BATERIA:
                // tambor amb dues baquetes
                r.set(4, 9, 20, 13.5f);
                c.drawOval(r, traz);
                linia(c, 4, 11.25f, 4, 17.5f);
                linia(c, 20, 11.25f, 20, 17.5f);
                r.set(4, 15.25f, 20, 19.75f);
                c.drawArc(r, 0, 180, false, traz);
                linia(c, 10.5f, 10.5f, 6, 3.5f);
                linia(c, 13.5f, 10.5f, 18, 3.5f);
                break;
            default:
                break;
        }
    }

    @Override
    public void setAlpha(int alpha) {
        traz.setAlpha(alpha);
        ple.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        traz.setColorFilter(cf);
        ple.setColorFilter(cf);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
