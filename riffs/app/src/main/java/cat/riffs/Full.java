package cat.riffs;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/**
 * Finestres emergents amb l'estil de l'app: un panell que puja des de baix
 * (accions, triar carpeta) i quadres centrats (confirmar, escriure un nom).
 */
public final class Full {

    private Full() {}

    /** Una acció del panell: icona, rètol i què fa. */
    public static final class Accio {
        final int icona;
        final String nom;
        final boolean perill;
        final Runnable fet;

        public Accio(int icona, String nom, boolean perill, Runnable fet) {
            this.icona = icona;
            this.nom = nom;
            this.perill = perill;
            this.fet = fet;
        }
    }

    // ---------- Bases ----------

    private static Dialog panellBaix(Activity a, View contingut) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout marc = Ui.columna(a);
        GradientDrawable fons = new GradientDrawable();
        fons.setColor(Ui.SUPERFICIE);
        float r = Ui.dp(a, 24);
        fons.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        marc.setBackground(fons);
        marc.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 20));

        // agafador
        View agafador = new View(a);
        agafador.setBackground(Ui.rodo(Ui.LINIA_FORTA, Ui.dp(a, 3)));
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 5));
        la.gravity = Gravity.CENTER_HORIZONTAL;
        marc.addView(agafador, la);
        marc.addView(contingut, Ui.ample(a, 8));

        d.setContentView(marc);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.setDimAmount(0.6f);
            w.setNavigationBarColor(Ui.SUPERFICIE);
        }
        d.setCanceledOnTouchOutside(true);
        return d;
    }

    private static Dialog quadre(Activity a, View contingut) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout marc = new FrameLayout(a);
        marc.setBackground(Ui.rodo(Ui.SUPERFICIE, Ui.dp(a, 24)));
        marc.setPadding(Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 18));
        marc.addView(contingut);
        d.setContentView(marc);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            int ample = Math.min(a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 40), Ui.dp(a, 420));
            w.setLayout(ample, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        d.setCanceledOnTouchOutside(true);
        return d;
    }

    /** Fila de botons a la dreta: [Cancel·la] [principal]. */
    private static LinearLayout botons(Activity a, Dialog d, String principal, int colorPrincipal, Runnable fet) {
        LinearLayout f = Ui.fila(a);
        f.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout cancela = Ui.pastilla(a, 0, "Cancel·la", 0x00000000, Ui.SUAU);
        cancela.setOnClickListener(v -> d.dismiss());
        f.addView(cancela);
        f.addView(Ui.espai(a, 8));
        LinearLayout ok = Ui.pastilla(a, 0, principal, colorPrincipal,
                colorPrincipal == Ui.TEXT ? Ui.SOBRE_COLOR : 0xFFFFFFFF);
        ok.setOnClickListener(v -> {
            d.dismiss();
            fet.run();
        });
        f.addView(ok);
        return f;
    }

    // ---------- Panell d'accions ----------

    /**
     * Panell amb una capçalera (rajola, títol i subtítol) i una fila de botons rodons amb icona i rètol.
     */
    public static void accions(Activity a, int icona, int color, String titol, String subtitol, List<Accio> accions) {
        LinearLayout cos = Ui.columna(a);
        cos.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), 0);

        LinearLayout cap = Ui.fila(a);
        cap.addView(Ui.rajola(a, icona, color, Ui.alfa(color == Ui.TEXT ? Ui.LINIA_FORTA : color, color == Ui.TEXT ? 255 : 40), 44));
        LinearLayout tx = Ui.columna(a);
        tx.addView(Ui.unaLinia(Ui.negreta(a, titol, 17, Ui.TEXT)));
        if (subtitol != null && !subtitol.isEmpty()) {
            tx.addView(Ui.unaLinia(Ui.text(a, subtitol, 13, Ui.SUAU)), Ui.ample(a, 4));
        }
        LinearLayout.LayoutParams lt = Ui.pes(1);
        lt.leftMargin = Ui.dp(a, 12);
        cap.addView(tx, lt);
        cos.addView(cap);

        Dialog[] d = new Dialog[1];
        LinearLayout fila = Ui.fila(a);
        fila.setGravity(Gravity.CENTER);
        for (Accio ac : accions) {
            LinearLayout b = Ui.columna(a);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            b.setClickable(true);
            b.setFocusable(true);
            b.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
            b.setBackground(Ui.tocable(0x00000000, Ui.dp(a, 16)));
            int c = ac.perill ? Ui.ACCENT : Ui.TEXT;
            FrameLayout cercle = new FrameLayout(a);
            cercle.setBackground(Ui.rodo(ac.perill ? Ui.alfa(Ui.ACCENT, 34) : Ui.SUPERFICIE2, Ui.dp(a, 28)));
            cercle.addView(Ui.imatge(a, ac.icona, c, 24),
                    new FrameLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56), Gravity.CENTER));
            b.addView(cercle, new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56)));
            TextView t = Ui.text(a, ac.nom, 12, ac.perill ? Ui.ACCENT : Ui.SUAU);
            t.setMaxLines(2);
            t.setGravity(Gravity.CENTER);
            b.addView(t, Ui.ample(a, 8));
            b.setContentDescription(ac.nom);
            b.setOnClickListener(v -> {
                d[0].dismiss();
                ac.fet.run();
            });
            fila.addView(b, Ui.pes(1));
        }
        cos.addView(fila, Ui.ample(a, 20));

        d[0] = panellBaix(a, cos);
        d[0].show();
    }

    // ---------- Confirmar ----------

    public static void confirma(Activity a, String titol, String missatge, String boto, boolean perill, Runnable fet) {
        LinearLayout cos = Ui.columna(a);
        cos.addView(Ui.negreta(a, titol, 18, Ui.TEXT));
        TextView m = Ui.text(a, missatge, 14, Ui.SUAU);
        m.setLineSpacing(0, 1.25f);
        cos.addView(m, Ui.ample(a, 10));
        Dialog d = quadre(a, cos);
        cos.addView(botons(a, d, boto, perill ? Ui.ACCENT : Ui.TEXT, fet), Ui.ample(a, 20));
        d.show();
    }

    /** Missatge amb un botó d'acció (per exemple, «Obre la configuració»). */
    public static void avis(Activity a, String titol, String missatge, String boto, Runnable fet) {
        confirma(a, titol, missatge, boto, false, fet);
    }

    // ---------- Escriure un nom ----------

    public static void demanaText(Activity a, String titol, String inicial, String boto, Ui.AmbText fet) {
        LinearLayout cos = Ui.columna(a);
        cos.addView(Ui.negreta(a, titol, 18, Ui.TEXT));
        EditText e = Ui.camp(a, "Nom", false);
        e.setBackground(Ui.rodoVora(Ui.SUPERFICIE2, Ui.LINIA_FORTA, Ui.dp(a, 14), 1));
        e.setText(inicial == null ? "" : inicial);
        e.setSelectAllOnFocus(true);
        cos.addView(e, Ui.ample(a, 14));
        Dialog d = quadre(a, cos);
        cos.addView(botons(a, d, boto, Ui.TEXT, () -> fet.fet(e.getText().toString())), Ui.ample(a, 18));
        e.setOnEditorActionListener((v, accio, ev) -> {
            d.dismiss();
            fet.fet(e.getText().toString());
            return true;
        });
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        e.requestFocus();
    }

    // ---------- Triar carpeta ----------

    public static void triaCarpeta(Activity a, String titol, File actual, Ui.AmbCarpeta fet) {
        Biblioteca b = Ui.biblioteca(a);
        List<Biblioteca.Carpeta> arbre = b.arbre();

        LinearLayout cos = Ui.columna(a);
        TextView t = Ui.negreta(a, titol, 17, Ui.TEXT);
        t.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), Ui.dp(a, 8));
        cos.addView(t);

        Dialog[] d = new Dialog[1];
        LinearLayout llista = Ui.columna(a);
        llista.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), 0);
        for (Biblioteca.Carpeta c : arbre) {
            boolean es = actual != null && c.dir.equals(actual);
            LinearLayout f = Ui.fila(a);
            f.setClickable(true);
            f.setFocusable(true);
            f.setBackground(Ui.tocable(es ? Ui.SUPERFICIE2 : 0x00000000, Ui.dp(a, 14)));
            f.setPadding(Ui.dp(a, 10 + 22 * Math.max(0, c.nivell - 1)), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
            int ic = c.nivell == 0 ? Icona.BIBLIOTECA : Icona.CARPETA;
            f.addView(Ui.rajola(a, ic, es ? Ui.ACCENT : Ui.TEXT, Ui.SUPERFICIE2, 36));
            String nom = c.nivell == 0 ? "Biblioteca" : c.dir.getName();
            TextView tn = Ui.unaLinia(c.nivell == 0 || es ? Ui.negreta(a, nom, 15, Ui.TEXT) : Ui.text(a, nom, 15, Ui.TEXT));
            LinearLayout.LayoutParams lp = Ui.pes(1);
            lp.leftMargin = Ui.dp(a, 12);
            f.addView(tn, lp);
            if (es) f.addView(Ui.text(a, "Aquí", 12, Ui.ACCENT));
            File dir = c.dir;
            f.setOnClickListener(v -> {
                d[0].dismiss();
                fet.fet(dir);
            });
            llista.addView(f);
        }

        int maxim = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.55f);
        ScrollView scroll = new ScrollView(a) {
            @Override
            protected void onMeasure(int ample, int alt) {
                // creix amb la llista, però com a molt fins a mitja pantalla llarga
                super.onMeasure(ample, MeasureSpec.makeMeasureSpec(maxim, MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(llista);
        cos.addView(scroll, Ui.ample(a, 0));

        LinearLayout nova = Ui.pastilla(a, Icona.MES, "Carpeta nova", Ui.SUPERFICIE2, Ui.TEXT);
        nova.setOnClickListener(v -> {
            d[0].dismiss();
            demanaText(a, "Carpeta nova", "", "Crea", nom -> {
                try {
                    File c = b.creaCarpeta(b.arrel, nom);
                    Ui.historial(a, "Carpeta nova: «" + c.getName() + "»");
                    fet.fet(c);
                } catch (IllegalArgumentException ex) {
                    Ui.avis(a, ex.getMessage());
                }
            });
        });
        LinearLayout.LayoutParams ln = Ui.ample(a, 12);
        ln.leftMargin = Ui.dp(a, 20);
        ln.rightMargin = Ui.dp(a, 20);
        cos.addView(nova, ln);

        d[0] = panellBaix(a, cos);
        d[0].show();
    }
}
