package cat.riffs;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * El panell de l'editor de so (2.32), tipus plugin: les seccions del sinte (oscil·ladors, filtre, envolupants, LFO,
 * efectes…) en dues files de targetes amb botons rodons. Es mou amb el dit de costat (i amunt i avall si no hi cap);
 * els botons es giren arrossegant amunt o avall.
 */
final class EditorSo {

    interface Oient {
        /** S'ha mogut un botó ({@code acabat}: s'ha aixecat el dit; és el moment de desar). */
        void canvi(boolean acabat);
    }

    /** Quantes seccions van a la primera fila (la resta, a la segona). */
    private static final int PRIMERA_FILA = 6;

    final View vista;
    private final List<PotView> pots = new ArrayList<>();
    private final Supplier<Sinte.So> so, fabrica;

    EditorSo(Activity a, int color, Supplier<Sinte.So> so, Supplier<Sinte.So> fabrica, Oient oient) {
        this.so = so;
        this.fabrica = fabrica;
        LinearLayout files = Ui.columna(a);
        files.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 8));
        LinearLayout fila = null;
        List<SoParams.Seccio> ss = SoParams.seccions();
        for (int i = 0; i < ss.size(); i++) {
            if (i == 0 || i == PRIMERA_FILA) {
                fila = Ui.fila(a);
                fila.setGravity(Gravity.TOP);
                files.addView(fila, Ui.ample(a, i == 0 ? 0 : 8));
            }
            SoParams.Seccio s = ss.get(i);
            LinearLayout targeta = Ui.columna(a);
            GradientDrawable fons = new GradientDrawable();
            fons.setColor(0xFF15151A);
            fons.setStroke(Ui.dp(a, 1), Ui.LINIA);
            fons.setCornerRadius(Ui.dp(a, 12));
            targeta.setBackground(fons);
            targeta.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 2));
            LinearLayout cap = Ui.fila(a);
            cap.setGravity(Gravity.CENTER_VERTICAL);
            View punt = new View(a);
            punt.setBackground(Ui.rodo(color, Ui.dp(a, 3)));
            cap.addView(punt, new LinearLayout.LayoutParams(Ui.dp(a, 6), Ui.dp(a, 6)));
            cap.addView(Ui.espai(a, 6));
            TextView t = Ui.unaLinia(Ui.negreta(a, s.nom.toUpperCase(java.util.Locale.ROOT), 10, Ui.SUAU));
            t.setLetterSpacing(0.08f);
            cap.addView(t);
            cap.setPadding(Ui.dp(a, 4), 0, 0, Ui.dp(a, 2));
            targeta.addView(cap);
            LinearLayout botons = Ui.fila(a);
            for (SoParams.Param p : s.params) {
                PotView pot = new PotView(a, p, color);
                pot.posaOient((v, acabat) -> {
                    p.posa(so.get(), v);
                    oient.canvi(acabat);
                });
                pots.add(pot);
                botons.addView(pot, new LinearLayout.LayoutParams(Ui.dp(a, 64), Ui.dp(a, 86)));
            }
            targeta.addView(botons);
            LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (fila.getChildCount() > 0) lt.leftMargin = Ui.dp(a, 8);
            fila.addView(targeta, lt);
        }
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(true);
        hs.addView(files);
        ScrollView vs = new ScrollView(a);
        vs.setFillViewport(true);
        vs.addView(hs);
        vista = vs;
        refresca();
    }

    /** Torna a posar tots els botons segons el so (després de canviar de preset o desfer). */
    void refresca() {
        Sinte.So o = so.get(), f = fabrica.get();
        for (PotView p : pots) {
            p.posa(p.param().llegeix(o));
            p.posaPerDefecte(p.param().llegeix(f != null ? f : new Sinte.So()));
        }
    }
}
