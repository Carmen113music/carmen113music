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

    /**
     * Panell amb un lliscador (per exemple, un volum). {@code canvi} rep el valor (0..max) a mesura que es mou;
     * {@code text} diu com es mostra.
     */
    public static void lliscador(Activity a, String titol, int valor, int max, Ui.AmbIndex canvi,
                                 java.util.function.IntFunction<String> text) {
        LinearLayout cos = Ui.columna(a);
        cos.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), Ui.dp(a, 8));
        LinearLayout cap = Ui.fila(a);
        cap.addView(Ui.negreta(a, titol, 17, Ui.TEXT), Ui.pes(1));
        TextView v = Ui.negreta(a, text.apply(valor), 15, Ui.GUITARRA);
        v.setFontFeatureSettings("tnum");
        cap.addView(v);
        cos.addView(cap);
        android.widget.SeekBar sb = new android.widget.SeekBar(a);
        sb.setMax(max);
        sb.setProgress(valor);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar b, int p, boolean usuari) {
                v.setText(text.apply(p));
                canvi.fet(p);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar b) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar b) {}
        });
        cos.addView(sb, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 48)));
        Dialog d = panellBaix(a, cos);
        d.show();
    }

    /** Panell amb un títol i una llista d'opcions (una per fila, amb icona). */
    public static void llista(Activity a, String titol, String subtitol, List<Accio> opcions) {
        llista(a, titol, subtitol, opcions, null);
    }

    /**
     * Llista per triar una opció (tocar-la la tria i tanca). A dalt, la fletxa «<» torna enrere sense triar res
     * ({@code enrere}: què fer en tornar, per exemple tornar a la llista d'abans; null = només tancar).
     * Té el mateix aspecte que {@link #triaAmbProva} (2.30: tots els panells de triar són iguals).
     */
    public static void llista(Activity a, String titol, String subtitol, List<Accio> opcions, Runnable enrere) {
        Dialog[] d = new Dialog[1];
        LinearLayout cos = capTria(a, titol, subtitol, () -> {
            d[0].dismiss();
            if (enrere != null) enrere.run();
        }, null);
        LinearLayout files = Ui.columna(a);
        for (Accio ac : opcions) {
            int c = ac.perill ? Ui.ACCENT : Ui.TEXT;
            LinearLayout f = filaTria(a, ac.icona, ac.nom, c);
            f.setBackground(Ui.tocable(Ui.SUPERFICIE2, Ui.dp(a, 12)));
            f.setOnClickListener(v -> {
                d[0].dismiss();
                ac.fet.run();
            });
            files.addView(f, Ui.ample(a, 5));
        }
        afegeixLlistaTria(a, cos, files, opcions.size());
        d[0] = panellBaix(a, cos);
        d[0].show();
    }

    public static void triaAmbProva(Activity a, String titol, String subtitol, String[] noms, int triat, int color,
                                    Ui.AmbIndex prova, Ui.AmbIndex tria) {
        triaAmbProva(a, titol, subtitol, noms, triat, color, prova, tria, null, null);
    }

    /**
     * Panell per triar un so escoltant-lo: tocar una fila el fa sonar ({@code prova}) i el deixa triat ({@code tria}),
     * però el panell no es tanca, perquè se'n puguin provar uns quants. «Fet» el tanca amb el que hi ha triat.
     * La fletxa «<» torna enrere **sense canviar res**: si ja s'havia triat un altre so, es torna al d'abans
     * ({@code tria(triat)}, o {@code desfes} si el d'abans no és a la llista) i després {@code enrere} (pot ser null).
     */
    public static void triaAmbProva(Activity a, String titol, String subtitol, String[] noms, int triat, int color,
                                    Ui.AmbIndex prova, Ui.AmbIndex tria, Runnable desfes, Runnable enrere) {
        final int[] sel = {triat};
        Dialog[] d = new Dialog[1];
        LinearLayout fet = Ui.pastilla(a, 0, "Fet", Ui.TEXT, Ui.SOBRE_COLOR);
        LinearLayout cos = capTria(a, titol, subtitol, () -> {
            d[0].dismiss();
            if (sel[0] != triat) {
                if (triat >= 0) tria.fet(triat);
                else if (desfes != null) desfes.run();
            }
            if (enrere != null) enrere.run();
        }, fet);
        LinearLayout files = Ui.columna(a);
        List<LinearLayout> rows = new java.util.ArrayList<>();
        Runnable pinta = () -> {
            for (int i = 0; i < rows.size(); i++) {
                boolean t = i == sel[0];
                LinearLayout f = rows.get(i);
                f.setBackground(Ui.tocable(t ? Ui.alfa(color, 50) : Ui.SUPERFICIE2, Ui.dp(a, 12)));
                Ui.colorIcona(f, t ? color : Ui.SUAU);
            }
        };
        for (int i = 0; i < noms.length; i++) {
            int idx = i;
            LinearLayout f = filaTria(a, Icona.PLAY, noms[i], Ui.TEXT);
            f.setOnClickListener(v -> {
                prova.fet(idx);
                if (sel[0] != idx) {
                    sel[0] = idx;
                    tria.fet(idx);
                    pinta.run();
                }
            });
            rows.add(f);
            files.addView(f, Ui.ample(a, 5));
        }
        pinta.run();
        afegeixLlistaTria(a, cos, files, noms.length);
        d[0] = panellBaix(a, cos);
        fet.setOnClickListener(v -> d[0].dismiss());
        d[0].show();
    }

    /** Capçalera comuna dels panells de triar: «<», títol (i subtítol) i, si n'hi ha, un botó a la dreta. */
    private static LinearLayout capTria(Activity a, String titol, String subtitol, Runnable enrere, View dreta) {
        LinearLayout cos = Ui.columna(a);
        cos.setPadding(Ui.dp(a, 12), Ui.dp(a, 2), Ui.dp(a, 18), 0);
        LinearLayout cap = Ui.fila(a);
        cap.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout torna = Ui.botoIcona(a, Icona.ENRERE, Ui.TEXT, Ui.SUPERFICIE2, 38, 18, "Enrere sense triar");
        torna.setOnClickListener(v -> enrere.run());
        cap.addView(torna);
        cap.addView(Ui.espai(a, 10));
        LinearLayout tx = Ui.columna(a);
        tx.addView(Ui.unaLinia(Ui.negreta(a, titol, 15, Ui.TEXT)));
        if (subtitol != null && !subtitol.isEmpty()) {
            TextView st = Ui.text(a, subtitol, 12, Ui.SUAU);
            st.setLineSpacing(0, 1.15f);
            st.setMaxLines(2);
            st.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tx.addView(st, Ui.ample(a, 2));
        }
        cap.addView(tx, Ui.pes(1));
        if (dreta != null) {
            cap.addView(Ui.espai(a, 10));
            cap.addView(dreta, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(a, 38)));
        }
        cos.addView(cap);
        return cos;
    }

    /** Una fila dels panells de triar (la mateixa mida i lletra a tot arreu). */
    private static LinearLayout filaTria(Activity a, int icona, String nom, int color) {
        LinearLayout f = Ui.fila(a);
        f.setClickable(true);
        f.setFocusable(true);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
        f.setMinimumHeight(Ui.dp(a, 42));
        if (icona > 0) {
            f.addView(Ui.imatge(a, icona, Ui.SUAU, 16), new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));
            f.addView(Ui.espai(a, 10));
        }
        f.addView(Ui.unaLinia(Ui.negreta(a, nom, 13, color)), Ui.pes(1));
        return f;
    }

    private static void afegeixLlistaTria(Activity a, LinearLayout cos, LinearLayout files, int n) {
        ScrollView sc = new ScrollView(a);
        files.setPadding(files.getPaddingLeft(), files.getPaddingTop(), files.getPaddingRight(), Ui.dp(a, 10));
        sc.addView(files);
        // 2.31.2: la llista no pot ser més alta que l'espai que queda de veritat (en horitzontal, l'última fila
        // quedava fora de la pantalla i no s'hi podia arribar); el que no hi cap es desplaça
        android.graphics.Rect vis = new android.graphics.Rect();
        a.getWindow().getDecorView().getWindowVisibleDisplayFrame(vis);
        int altVisible = vis.height() > 0 ? vis.height() : a.getResources().getDisplayMetrics().heightPixels;
        int maxim = Math.max(Ui.dp(a, 120), altVisible - Ui.dp(a, 175));
        LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(Ui.dp(a, 47) * n + Ui.dp(a, 18), maxim));
        ls.topMargin = Ui.dp(a, 8);
        cos.addView(sc, ls);
    }

    // ---------- Editor d'acords (com el del Cubase) ----------

    /**
     * L'editor d'acords (com el del Cubase), per posar al costat dels pads petits (2.31.2): quatre columnes
     * (Fonamental, Tipus, Tensions, Nota greu). {@link EditorAcord#posa} hi carrega l'acord d'un pad; cada toc
     * el canvia i avisa {@code canvi} (que el desa i el fa sonar).
     */
    public static final class EditorAcord {
        public final View vista;
        private final Acords.Acord ac = new Acords.Acord();
        private final Runnable pinta;
        private final java.util.function.Consumer<Acords.Acord> canvi;

        private EditorAcord(View vista, Runnable[] pinta, java.util.function.Consumer<Acords.Acord> canvi, Acords.Acord[] ref) {
            this.vista = vista;
            this.pinta = pinta[0];
            this.canvi = canvi;
            ref[0] = ac;
        }

        /** Mostra l'acord d'un pad (buit: no hi ha res marcat fins que en toquis una casella). */
        public void posa(Acords.Acord a) {
            Acords.Acord b = a == null ? new Acords.Acord() : a;
            ac.arrel = b.arrel;
            ac.grafia = b.grafia;
            ac.tipus = b.tipus;
            ac.tensions = b.tensions;
            ac.baix = b.baix;
            pinta.run();
        }

        public Acords.Acord acord() {
            return ac.copia();
        }

        void avisa() {
            pinta.run();
            canvi.accept(ac.copia());
        }
    }

    public static EditorAcord editorAcord(Activity a, int color, java.util.function.Consumer<Acords.Acord> canvi) {
        String[] baixos = new String[13];
        baixos[0] = "—";
        for (int i = 0; i < 12; i++) baixos[i + 1] = Acords.NOTES[i];
        List<TextView> bArrel = new java.util.ArrayList<>(), bTipus = new java.util.ArrayList<>(),
                bTens = new java.util.ArrayList<>(), bBaix = new java.util.ArrayList<>();
        LinearLayout cols = Ui.fila(a);
        cols.setGravity(Gravity.TOP);
        cols.addView(columnaAcord(a, "Fonamental", Acords.NOTES, 3, bArrel), Ui.pes(3));
        cols.addView(Ui.espai(a, 10));
        cols.addView(columnaAcord(a, "Tipus", Acords.TIPUS, 2, bTipus), Ui.pes(2));
        cols.addView(Ui.espai(a, 10));
        cols.addView(columnaAcord(a, "Tensions", Acords.TENSIONS, 3, bTens), Ui.pes(3));
        cols.addView(Ui.espai(a, 10));
        cols.addView(columnaAcord(a, "Nota greu", baixos, 3, bBaix), Ui.pes(3));
        ScrollView sc = new ScrollView(a);
        sc.addView(cols);

        Acords.Acord[] ref = new Acords.Acord[1];
        Runnable[] pinta = new Runnable[1];
        pinta[0] = () -> {
            Acords.Acord ac = ref[0];
            if (ac == null) return;
            boolean buit = ac.buit();
            for (int i = 0; i < bArrel.size(); i++) marcaBoto(a, bArrel.get(i), i == ac.arrel, color);
            for (int i = 0; i < bTipus.size(); i++) marcaBoto(a, bTipus.get(i), !buit && i == ac.tipus, color);
            for (int i = 0; i < bTens.size(); i++) marcaBoto(a, bTens.get(i), !buit && ac.te(i), color);
            for (int i = 0; i < bBaix.size(); i++) marcaBoto(a, bBaix.get(i), !buit && i == ac.baix + 1, color);
        };
        EditorAcord ed = new EditorAcord(sc, pinta, canvi, ref);
        Acords.Acord ac = ref[0];
        Runnable siBuit = () -> {
            if (ac.buit()) {   // un pad buit comença sent un acord de C major
                ac.arrel = 0;
                ac.tipus = Acords.MAJ;
                ac.tensions = 0;
                ac.baix = -1;
            }
        };
        for (int i = 0; i < bArrel.size(); i++) {
            int k = i;
            bArrel.get(i).setOnClickListener(v -> {
                siBuit.run();
                ac.arrel = k;
                ac.grafia = 0;
                ed.avisa();
            });
        }
        for (int i = 0; i < bTipus.size(); i++) {
            int k = i;
            bTipus.get(i).setOnClickListener(v -> {
                siBuit.run();
                ac.tipus = k;
                ed.avisa();
            });
        }
        for (int i = 0; i < bTens.size(); i++) {
            int k = i;
            bTens.get(i).setOnClickListener(v -> {
                siBuit.run();
                ac.commuta(k);
                ed.avisa();
            });
        }
        for (int i = 0; i < bBaix.size(); i++) {
            int k = i;
            bBaix.get(i).setOnClickListener(v -> {
                siBuit.run();
                ac.baix = k - 1;
                ed.avisa();
            });
        }
        pinta[0].run();
        return ed;
    }

    private static LinearLayout columnaAcord(Activity a, String titol, String[] noms, int cols, List<TextView> botons) {
        LinearLayout c = Ui.columna(a);
        c.addView(Ui.etiqueta(a, titol));
        LinearLayout fila = null;
        for (int i = 0; i < noms.length; i++) {
            if (i % cols == 0) {
                fila = Ui.fila(a);
                c.addView(fila, Ui.ample(a, 5));
            }
            TextView t = Ui.negreta(a, noms[i], 13, Ui.TEXT);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Ui.dp(a, 34));
            t.setClickable(true);
            t.setSingleLine(true);
            LinearLayout.LayoutParams lp = Ui.pes(1);
            if (i % cols != 0) lp.leftMargin = Ui.dp(a, 5);
            fila.addView(t, lp);
            botons.add(t);
        }
        // que l'última fila tingui les caselles de la mateixa mida
        int resta = noms.length % cols;
        if (resta != 0) {
            for (int i = resta; i < cols; i++) {
                View v = new View(a);
                LinearLayout.LayoutParams lp = Ui.pes(1);
                lp.leftMargin = Ui.dp(a, 5);
                fila.addView(v, lp);
            }
        }
        return c;
    }

    private static void marcaBoto(Activity a, TextView t, boolean on, int color) {
        t.setBackground(Ui.tocable(on ? color : Ui.SUPERFICIE2, Ui.dp(a, 10)));
        t.setTextColor(on ? Ui.SOBRE_COLOR : Ui.TEXT);
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

    // ---------- Metrònom ----------

    private static final int[] COMPASSOS = {2, 3, 4, 6};

    private static int indexCompas(int c) {
        for (int i = 0; i < COMPASSOS.length; i++) if (COMPASSOS[i] == c) return i;
        return 2;
    }

    private static LinearLayout ambScrollLimitat(Activity a, LinearLayout dins) {
        int maxim = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.78f);
        ScrollView sc = new ScrollView(a) {
            @Override
            protected void onMeasure(int ample, int alt) {
                super.onMeasure(ample, MeasureSpec.makeMeasureSpec(maxim, MeasureSpec.AT_MOST));
            }
        };
        sc.addView(dins);
        LinearLayout l = Ui.columna(a);
        l.addView(sc, Ui.ample(a, 0));
        return l;
    }

    /** Ajustos del metrònom per gravar. Quan es tanca, desa i avisa. */
    public static void metronom(Activity a, Runnable enTancar) {
        Metronom.Config k = Metronom.Config.llegeix(a);
        LinearLayout cos = Ui.columna(a);
        cos.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), Ui.dp(a, 4));

        LinearLayout cap = Ui.fila(a);
        cap.addView(Ui.rajola(a, Icona.METRONOM, Ui.ACCENT, Ui.alfa(Ui.ACCENT, 36), 44));
        LinearLayout tx = Ui.columna(a);
        tx.addView(Ui.negreta(a, "Metrònom", 17, Ui.TEXT));
        tx.addView(Ui.text(a, "Claqueta i compte enrere per gravar a tempo", 13, Ui.SUAU), Ui.ample(a, 4));
        LinearLayout.LayoutParams lt = Ui.pes(1);
        lt.leftMargin = Ui.dp(a, 12);
        cap.addView(tx, lt);
        android.widget.Switch actiu = new android.widget.Switch(a);
        actiu.setChecked(k.actiu);
        cap.addView(actiu);
        cos.addView(cap);

        LinearLayout opcions = Ui.columna(a);
        ControlTempo tempo = new ControlTempo(a, k.bpm);
        tempo.setOient(b -> k.bpm = b);
        opcions.addView(tempo, Ui.ample(a, 22));

        opcions.addView(Ui.etiqueta(a, "Compàs"), Ui.ample(a, 20));
        opcions.addView(Ui.segmentat(a, new String[]{"2/4", "3/4", "4/4", "6/8"}, indexCompas(k.compas), Ui.TEXT,
                i -> k.compas = COMPASSOS[i]), Ui.ample(a, 8));

        opcions.addView(Ui.etiqueta(a, "Compte enrere"), Ui.ample(a, 18));
        opcions.addView(Ui.segmentat(a, new String[]{"Cap", "1 compàs", "2 compassos"}, Math.max(0, Math.min(2, k.compteEnrere)), Ui.TEXT,
                i -> k.compteEnrere = i), Ui.ample(a, 8));

        opcions.addView(Ui.etiqueta(a, "Clic"), Ui.ample(a, 18));
        String[] sons = {Metronom.SO_AURICULARS, Metronom.SO_MAI};
        int iso = k.so.equals(Metronom.SO_MAI) ? 1 : 0;
        opcions.addView(Ui.segmentat(a, new String[]{"Amb auriculars", "Només visual"}, iso, Ui.TEXT,
                i -> k.so = sons[i]), Ui.ample(a, 8));
        TextView nota = Ui.text(a, "El clic no sona mai per l'altaveu, perquè es gravaria. Amb «Amb auriculars» sona només si en tens connectats; si no (o si els desconnectes), la pantalla fa pampallugues a cada temps.", 12, Ui.SUAU);
        nota.setLineSpacing(0, 1.2f);
        opcions.addView(nota, Ui.ample(a, 8));

        Metronom[] prova = new Metronom[1];
        LinearLayout escolta = Ui.pastilla(a, Icona.PLAY, "Escolta el tempo (per l'altaveu)", Ui.SUPERFICIE2, Ui.TEXT);
        TextView textEscolta = (TextView) escolta.getChildAt(2);
        escolta.setOnClickListener(v -> {
            if (prova[0] != null) {
                prova[0].atura();
                prova[0] = null;
                textEscolta.setText("Escolta el tempo (per l'altaveu)");
            } else {
                prova[0] = new Metronom(k.bpm, k.compas, 0, true);
                if (prova[0].comenca()) textEscolta.setText("Atura");
                else prova[0] = null;
            }
        });
        tempo.setOient(b -> {
            k.bpm = b;
            if (prova[0] != null) {   // torna a engegar amb el tempo nou
                prova[0].atura();
                prova[0] = new Metronom(k.bpm, k.compas, 0, true);
                if (!prova[0].comenca()) prova[0] = null;
            }
        });
        opcions.addView(escolta, Ui.ample(a, 16));

        Runnable mostraOpcions = () -> opcions.setAlpha(actiu.isChecked() ? 1f : 0.45f);
        actiu.setOnCheckedChangeListener((b, v) -> {
            k.actiu = v;
            mostraOpcions.run();
        });
        mostraOpcions.run();
        cos.addView(opcions);

        LinearLayout fet = Ui.pastilla(a, 0, "Fet", Ui.TEXT, Ui.SOBRE_COLOR);
        cos.addView(fet, Ui.ample(a, 20));

        Dialog d = panellBaix(a, ambScrollLimitat(a, cos));
        fet.setOnClickListener(v -> d.dismiss());
        d.setOnDismissListener(x -> {
            if (prova[0] != null) prova[0].atura();
            k.desa(a);
            if (enTancar != null) enTancar.run();
        });
        d.show();
    }

    /** Tempo i compàs d'una gravació. {@code fet} rep el BPM (0 = sense tempo). */
    public interface AmbTempo {
        void fet(double bpm, int compas);
    }

    public static void tempo(Activity a, Fitxa f, AmbTempo fet) {
        LinearLayout cos = Ui.columna(a);
        cos.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), Ui.dp(a, 4));
        cos.addView(Ui.negreta(a, "Tempo de la gravació", 17, Ui.TEXT));
        TextView ajuda = Ui.text(a, f.claqueta
                ? "Es va gravar amb metrònom. Si el canvies, la graella deixarà de coincidir amb el clic que vas sentir."
                : "Posa el tempo amb − i +, o toca «Tap» al ritme mentre l'escoltes.", 13, Ui.SUAU);
        ajuda.setLineSpacing(0, 1.2f);
        cos.addView(ajuda, Ui.ample(a, 6));
        ControlTempo tempo = new ControlTempo(a, f.teTempo() ? f.bpm : 100);
        cos.addView(tempo, Ui.ample(a, 18));
        int[] compas = {f.compas};
        cos.addView(Ui.etiqueta(a, "Compàs"), Ui.ample(a, 18));
        cos.addView(Ui.segmentat(a, new String[]{"2/4", "3/4", "4/4", "6/8"}, indexCompas(f.compas), Ui.TEXT,
                i -> compas[0] = COMPASSOS[i]), Ui.ample(a, 8));

        LinearLayout botons = Ui.fila(a);
        Dialog[] d = new Dialog[1];
        if (f.teTempo()) {
            LinearLayout treu = Ui.pastilla(a, 0, "Treu el tempo", 0x00000000, Ui.ACCENT);
            treu.setOnClickListener(v -> {
                d[0].dismiss();
                fet.fet(0, compas[0]);
            });
            botons.addView(treu);
        }
        botons.addView(Ui.mollaH(a));
        LinearLayout desa = Ui.pastilla(a, 0, "Desa", Ui.TEXT, Ui.SOBRE_COLOR);
        desa.setOnClickListener(v -> {
            d[0].dismiss();
            fet.fet(tempo.bpm(), compas[0]);
        });
        botons.addView(desa);
        cos.addView(botons, Ui.ample(a, 22));

        d[0] = panellBaix(a, ambScrollLimitat(a, cos));
        d[0].show();
    }
}
