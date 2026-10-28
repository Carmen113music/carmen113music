package cat.riffs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/** Regla de compassos i temps a dalt de les pistes. Tocar-la posa el capçal. */
public class RegleView extends View {

    public interface OientCapcal {
        void capcal(long ms, boolean acabat);
    }

    private final Projecte projecte;
    private final Vista vista;
    private final Paint linia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capcal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle = new Path();
    private OientCapcal oient;

    public RegleView(Context c, Projecte p, Vista v) {
        super(c);
        projecte = p;
        vista = v;
        text.setColor(Ui.SUAU);
        text.setTextSize(Ui.dp(c, 11));
        text.setTypeface(Ui.MITJA);
        capcal.setColor(Ui.TEXT);
        setBackgroundColor(Ui.SUPERFICIE);
    }

    public void setOientCapcal(OientCapcal o) {
        oient = o;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        double mpt = projecte.msPerTemps();
        int compas = projecte.compas;
        float pxTemps = (float) (mpt * vista.pxPerMs);
        // amb molt poc zoom, només numera cada 2, 4, 8... compassos
        int cadaCompas = 1;
        while (pxTemps * compas * cadaCompas < Ui.dp(getContext(), 34)) cadaCompas *= 2;

        long primer = (long) Math.floor(vista.ms(0) / mpt);
        long darrer = (long) Math.ceil(vista.ms(w) / mpt);
        for (long t = Math.max(0, primer); t <= darrer; t++) {
            float x = vista.x(t * mpt);
            boolean esCompas = t % compas == 0;
            long numCompas = t / compas + 1;
            if (esCompas) {
                boolean numera = (numCompas - 1) % cadaCompas == 0;
                linia.setColor(numera ? 0x66FFFFFF : 0x33FFFFFF);
                linia.setStrokeWidth(Ui.dp(getContext(), 1));
                c.drawLine(x, numera ? h * 0.35f : h * 0.6f, x, h, linia);
                if (numera) c.drawText(Long.toString(numCompas), x + Ui.dp(getContext(), 4), h * 0.5f, text);
            } else if (pxTemps > Ui.dp(getContext(), 7)) {
                linia.setColor(0x26FFFFFF);
                linia.setStrokeWidth(Ui.dp(getContext(), 1));
                c.drawLine(x, h * 0.72f, x, h, linia);
            }
        }
        // línia de baix
        linia.setColor(Ui.LINIA);
        c.drawLine(0, h - 0.5f, w, h - 0.5f, linia);

        // capçal
        float xc = vista.x(vista.capcalMs);
        if (xc >= -20 && xc <= w + 20) {
            float m = Ui.dp(getContext(), 7);
            triangle.reset();
            triangle.moveTo(xc - m, h - m * 1.4f);
            triangle.lineTo(xc + m, h - m * 1.4f);
            triangle.lineTo(xc, h);
            triangle.close();
            c.drawPath(triangle, capcal);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (oient == null) return super.onTouchEvent(e);
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
            long ms = Math.max(0, Math.round(vista.ms(e.getX())));
            oient.capcal(ms, a == MotionEvent.ACTION_UP);
            if (a == MotionEvent.ACTION_UP) performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
