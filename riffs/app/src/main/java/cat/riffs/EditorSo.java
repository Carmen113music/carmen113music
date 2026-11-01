package cat.riffs;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * El panell de l'editor de so (2.32), tipus plugin: les seccions del sinte en dues files de targetes. Es mou amb el
 * dit de costat (i amunt i avall si no hi cap); els botons es giren arrossegant amunt o avall.
 * 2.38: cada motor (analògic, FM, wavetable, orgue, corda) té els seus controls, la seva peça pròpia (algorisme,
 * taula d'ones, tirants, corda) i el seu aspecte. Si el so canvia de motor, el panell es torna a fer.
 */
final class EditorSo {

    interface Oient {
        /** S'ha mogut un botó ({@code acabat}: s'ha aixecat el dit; és el moment de desar). */
        void canvi(boolean acabat);
    }

    /** L'aspecte d'un sinte. */
    private static final class Estil {
        final String marca;
        final int fons, targeta, vora, titol, accent, cara, caraInt, voraBoto, pista;
        final boolean lliscadors, lcd;

        Estil(String marca, int fons, int targeta, int vora, int titol, int accent, int cara, int caraInt, int voraBoto,
              int pista, boolean lliscadors, boolean lcd) {
            this.marca = marca;
            this.fons = fons;
            this.targeta = targeta;
            this.vora = vora;
            this.titol = titol;
            this.accent = accent;
            this.cara = cara;
            this.caraInt = caraInt;
            this.voraBoto = voraBoto;
            this.pista = pista;
            this.lliscadors = lliscadors;
            this.lcd = lcd;
        }
    }

    /** {@code color}: el de la pista (l'analògic el fa servir; els altres tenen el seu). */
    private static Estil estil(int motor, int color) {
        switch (motor) {
            case Sinte.FM:          // fusta fosca, pantalletes verdes (sintes digitals dels 80)
                return new Estil("RIFFS FM-4", 0xFF140E0C, 0xFF241915, 0xFF4E352A, 0xFFE8C9A8, 0xFF9FE870,
                        0xFF3A2A22, 0xFF201612, 0xFF6B4E3E, 0xFF3D2C24, false, true);
            case Sinte.WAVETABLE:   // blau nit amb llum (sintes de taules d'ones moderns)
                return new Estil("RIFFS ONA", 0xFF080D13, 0xFF0F1924, 0xFF21384F, 0xFF9FD8FF, 0xFF4FD3FF,
                        0xFF1B2A3A, 0xFF0D1620, 0xFF2E4A66, 0xFF1A2C3E, false, false);
            case Sinte.ORGUE:       // fusta i crema (orgue de rodes)
                return new Estil("RIFFS TIRANTS", 0xFF1A110A, 0xFF2A1C11, 0xFF6B4A2B, 0xFFF2E3C2, 0xFFE9B35A,
                        0xFFEFE3C8, 0xFFD8C8A4, 0xFF8A6A44, 0xFF4A3420, false, false);
            case Sinte.CORDA:       // verd fosc i llautó
                return new Estil("RIFFS CORDA", 0xFF0F130C, 0xFF1A2015, 0xFF3E4A33, 0xFFE8D9A8, 0xFFE8C27A,
                        0xFF2E3626, 0xFF1A1F15, 0xFF55624A, 0xFF33402A, false, false);
            default:                // analògic: faders com els sintes dels 80
                return new Estil("RIFFS A-80", 0xFF111113, 0xFF1C1C20, 0xFF33333A, 0xFFDDDCE2, color,
                        0xFFD9D8DE, 0xFFBDBCC4, 0xFF55555E, 0xFF34343B, true, false);
        }
    }

    final FrameLayout vista;
    private final Activity a;
    private final int colorPista;
    private final Supplier<Sinte.So> so, fabrica;
    private final Oient oient;
    private final List<PotView> pots = new ArrayList<>();
    private final List<View> especials = new ArrayList<>();
    private final List<Runnable> grups = new ArrayList<>();
    private final List<Runnable> titols = new ArrayList<>();
    private int motorFet = -1;

    EditorSo(Activity a, int color, Supplier<Sinte.So> so, Supplier<Sinte.So> fabrica, Oient oient) {
        this.a = a;
        this.colorPista = color;
        this.so = so;
        this.fabrica = fabrica;
        this.oient = oient;
        vista = new FrameLayout(a);
        construeix();
    }

    private Sinte.So ara() {
        Sinte.So o = so.get();
        return o != null ? o : new Sinte.So();
    }

    /** Fa el panell per al motor del so d'ara. */
    private void construeix() {
        Sinte.So o = ara();
        motorFet = o.motor;
        pots.clear();
        especials.clear();
        grups.clear();
        titols.clear();
        vista.removeAllViews();
        Estil e = estil(o.motor, colorPista);
        vista.setBackgroundColor(e.fons);
        Oient avis = acabat -> {
            actualitza();
            oient.canvi(acabat);
        };

        LinearLayout fora = Ui.fila(a);
        fora.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 8));
        fora.addView(new VistesSinte.Placa(a, e.marca, e.accent, e.targeta),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        fora.addView(Ui.espai(a, 8));
        LinearLayout files = Ui.columna(a);
        fora.addView(files);
        LinearLayout[] fila = new LinearLayout[2];
        for (int i = 0; i < 2; i++) {
            fila[i] = Ui.fila(a);
            fila[i].setGravity(Gravity.TOP);
            files.addView(fila[i], Ui.ample(a, i == 0 ? 0 : 8));
        }
        for (SoParams.Seccio s : SoParams.seccions(o.motor)) {
            LinearLayout f = fila[s.fila == 0 ? 0 : 1];
            LinearLayout targeta = Ui.columna(a);
            GradientDrawable fons = new GradientDrawable();
            fons.setColor(e.targeta);
            fons.setStroke(Ui.dp(a, 1), e.vora);
            fons.setCornerRadius(Ui.dp(a, 12));
            targeta.setBackground(fons);
            targeta.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 2));
            LinearLayout cap = Ui.fila(a);
            cap.setGravity(Gravity.CENTER_VERTICAL);
            View punt = new View(a);
            punt.setBackground(Ui.rodo(e.accent, Ui.dp(a, 3)));
            cap.addView(punt, new LinearLayout.LayoutParams(Ui.dp(a, 6), Ui.dp(a, 6)));
            cap.addView(Ui.espai(a, 6));
            TextView t = Ui.unaLinia(Ui.negreta(a, s.titol(o).toUpperCase(java.util.Locale.ROOT), 10, e.titol));
            t.setLetterSpacing(0.08f);
            cap.addView(t);
            if (s.titol != null) titols.add(() -> t.setText(s.titol(ara()).toUpperCase(java.util.Locale.ROOT)));
            cap.setPadding(Ui.dp(a, 4), 0, 0, Ui.dp(a, 2));
            targeta.addView(cap);
            LinearLayout botons = Ui.fila(a);
            botons.setGravity(Gravity.CENTER_VERTICAL);
            if (s.vista != null) {
                VistesSinte.Base v = especial(s, e, avis);
                if (v != null) {
                    v.posaColors(Ui.alfa(e.titol, 150), e.titol, e.targeta);
                    especials.add(v);
                    botons.addView(v);
                }
            } else {
                for (SoParams.Param p : s.params) {
                    if (s.botons && p.tipus == SoParams.LLISTA) {
                        botons.addView(grupBotons(p, e, avis));
                        continue;
                    }
                    PotView pot = new PotView(a, p, e.accent);
                    pot.posaEstil(e.cara, e.caraInt, e.voraBoto, e.pista, Ui.alfa(e.titol, 170), e.lliscadors, e.lcd);
                    pot.posaOient((v, acabat) -> {
                        p.posa(ara(), v);
                        avis.canvi(acabat);
                    });
                    pots.add(pot);
                    botons.addView(pot, new LinearLayout.LayoutParams(Ui.dp(a, 64), Ui.dp(a, 86)));
                }
            }
            targeta.addView(botons);
            LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (f.getChildCount() > 0) lt.leftMargin = Ui.dp(a, 8);
            f.addView(targeta, lt);
        }
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(true);
        hs.addView(fora);
        ScrollView vs = new ScrollView(a);
        vs.setFillViewport(true);
        vs.addView(hs);
        vista.addView(vs, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        actualitza();
    }

    private VistesSinte.Base especial(SoParams.Seccio s, Estil e, Oient avis) {
        switch (s.vista) {
            case "algorisme":
                return new VistesSinte.Algorisme(a, this::ara, s.params, e.accent, avis);
            case "taula":
                return new VistesSinte.Taula(a, this::ara, s.params, e.accent, avis);
            case "tirants":
                return new VistesSinte.Tirants(a, this::ara, s.params, e.accent, avis);
            case "corda":
                return new VistesSinte.Corda(a, this::ara, s.params, e.accent, avis);
            default:
                return null;
        }
    }

    /** Una llista curta com a botons (chorus I/II, Leslie, percussió…): el triat, ple del color del sinte. */
    private View grupBotons(SoParams.Param p, Estil e, Oient avis) {
        LinearLayout col = Ui.columna(a);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
        LinearLayout fila = Ui.fila(a);
        List<TextView> bs = new ArrayList<>();
        for (int i = 0; i < p.etiquetes.length; i++) {
            double v = p.valors[i];
            TextView b = Ui.negreta(a, p.etiquetes[i], 12, e.titol);
            b.setGravity(Gravity.CENTER);
            b.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), 0);
            b.setOnClickListener(x -> {
                b.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                p.posa(ara(), v);
                avis.canvi(true);
            });
            LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(a, 36));
            if (i > 0) lb.leftMargin = Ui.dp(a, 4);
            fila.addView(b, lb);
            bs.add(b);
        }
        col.addView(fila);
        col.addView(Ui.espai(a, 6));
        col.addView(Ui.text(a, p.nom, 11, Ui.alfa(e.titol, 170)));
        grups.add(() -> {
            int tria = p.indexDe(p.llegeix(ara()));
            for (int i = 0; i < bs.size(); i++) {
                boolean on = i == tria;
                TextView b = bs.get(i);
                b.setBackground(on ? Ui.rodo(e.accent, Ui.dp(a, 8)) : Ui.rodoVora(e.fons, e.vora, Ui.dp(a, 8), Ui.dp(a, 1)));
                b.setTextColor(on ? 0xFF111111 : e.titol);
            }
        });
        LinearLayout envol = Ui.columna(a);
        envol.setGravity(Gravity.CENTER);
        envol.addView(col);
        envol.setMinimumHeight(Ui.dp(a, 86));
        return envol;
    }

    /** Posa tots els controls segons el so (sense refer el panell). */
    private void actualitza() {
        Sinte.So o = ara(), f = fabrica.get();
        if (f == null || f.motor != o.motor) f = null;
        for (PotView p : pots) {
            p.posa(p.param().llegeix(o));
            p.posaPerDefecte(p.param().llegeix(f != null ? f : new Sinte.So()));
        }
        for (Runnable g : grups) g.run();
        for (Runnable t : titols) t.run();
        for (View v : especials) v.invalidate();
    }

    /** Torna a posar tots els botons segons el so (després de canviar de preset o desfer). Si el motor ha canviat, refà el panell. */
    void refresca() {
        if (ara().motor != motorFet) construeix();
        else actualitza();
    }
}
