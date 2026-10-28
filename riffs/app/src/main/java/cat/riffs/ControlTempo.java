package cat.riffs;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Control del tempo: el BPM en gran, botons − i + (mantenint premut van ràpid) i un botó «Tap»
 * per marcar el tempo tocant amb el dit.
 */
public class ControlTempo extends LinearLayout {

    public interface Oient {
        void canvi(double bpm);
    }

    private final TextView numero;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Long> tocs = new ArrayList<>();
    private double bpm;
    private Oient oient;

    public ControlTempo(Context c, double inicial) {
        super(c);
        setOrientation(VERTICAL);
        bpm = Ritme.limita(inicial);

        LinearLayout fila = Ui.fila(c);
        fila.setGravity(Gravity.CENTER);
        FrameLayout menys = Ui.botoIcona(c, Icona.MENYS, Ui.TEXT, Ui.SUPERFICIE2, 52, 22, "Més lent");
        repeteix(menys, -1);
        fila.addView(menys);

        LinearLayout mig = Ui.columna(c);
        mig.setGravity(Gravity.CENTER);
        numero = Ui.text(c, "", 44, Ui.TEXT);
        numero.setTypeface(Ui.FINA);
        numero.setFontFeatureSettings("tnum");
        numero.setGravity(Gravity.CENTER);
        mig.addView(numero);
        TextView u = Ui.etiqueta(c, "BPM");
        u.setGravity(Gravity.CENTER);
        mig.addView(u, Ui.ample(c, 4));
        fila.addView(mig, Ui.pes(1));

        FrameLayout mes = Ui.botoIcona(c, Icona.MES, Ui.TEXT, Ui.SUPERFICIE2, 52, 22, "Més ràpid");
        repeteix(mes, +1);
        fila.addView(mes);
        addView(fila);

        LinearLayout tap = Ui.pastilla(c, 0, "Tap · toca al ritme", Ui.SUPERFICIE2, Ui.TEXT);
        tap.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.setPressed(true);
                toca();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
            }
            return true;
        });
        addView(tap, Ui.ample(c, 12));
        mostra();
    }

    public void setOient(Oient o) {
        oient = o;
    }

    public double bpm() {
        return bpm;
    }

    private void canvia(double nou) {
        bpm = Ritme.limita(nou);
        mostra();
        if (oient != null) oient.canvi(bpm);
    }

    private void mostra() {
        numero.setText(Long.toString(Math.round(bpm)));
    }

    private void toca() {
        long ara = System.currentTimeMillis();
        if (!tocs.isEmpty() && ara - tocs.get(tocs.size() - 1) > 2000) tocs.clear();
        tocs.add(ara);
        if (tocs.size() > 8) tocs.remove(0);
        double b = Ritme.bpmDeTocs(tocs);
        if (b > 0) canvia(Math.round(b));
    }

    /** Un toc suma o resta 1; mantenint premut, va repetint cada cop més ràpid. */
    private void repeteix(FrameLayout boto, int pas) {
        final int[] repeticions = {0};
        Runnable r = new Runnable() {
            @Override
            public void run() {
                repeticions[0]++;
                canvia(Math.round(bpm) + (repeticions[0] > 8 ? 5 * pas : pas));
                h.postDelayed(this, repeticions[0] > 3 ? 70 : 160);
            }
        };
        boto.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    canvia(Math.round(bpm) + pas);
                    repeticions[0] = 0;
                    h.postDelayed(r, 450);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    h.removeCallbacks(r);
                    return true;
                default:
                    return true;
            }
        });
    }
}
