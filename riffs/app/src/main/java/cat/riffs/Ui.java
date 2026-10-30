package cat.riffs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Colors, peces i diàlegs comuns de totes les pantalles. */
public final class Ui {

    // Paleta fosca d'estudi
    public static final int FONS = 0xFF0E0E10;
    public static final int SUPERFICIE = 0xFF18181C;
    public static final int SUPERFICIE2 = 0xFF222227;
    public static final int LINIA = 0xFF2A2A31;
    public static final int LINIA_FORTA = 0xFF3A3A43;
    public static final int TEXT = 0xFFF2F1F5;
    public static final int SUAU = 0xFF8D8C97;
    public static final int APAGAT = 0xFF5E5D68;
    public static final int ACCENT = 0xFFFF4B3E;    // vermell de gravar
    public static final int GUITARRA = 0xFFF5B445;  // ambre
    public static final int VEU = 0xFF59D0D6;       // turquesa
    public static final int BATERIA = 0xFFB18CFF;   // lila (pistes de bateria)
    public static final int SOBRE_COLOR = 0xFF141416; // text sobre ambre o turquesa

    public static final Typeface MITJA = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface FINA = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private Ui() {}

    // ---------- Dades de l'app ----------

    public static Biblioteca biblioteca(Context c) {
        return new Biblioteca(new File(c.getFilesDir(), "biblioteca"));
    }

    public static File fitxerHistorial(Context c) {
        return new File(c.getFilesDir(), "historial.txt");
    }

    public static void historial(Context c, String text) {
        Historial.afegeix(fitxerHistorial(c), System.currentTimeMillis(), text);
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("riffs", Context.MODE_PRIVATE);
    }

    /** Carpeta on es desen les gravacions noves (sempre dins la biblioteca i existent). */
    public static File carpetaActual(Context c) {
        Biblioteca b = biblioteca(c);
        String p = prefs(c).getString("carpeta", null);
        if (p != null) {
            File f = new File(p);
            if (f.isDirectory() && b.esDins(f)) return f;
        }
        return b.arrel;
    }

    public static void posaCarpetaActual(Context c, File dir) {
        prefs(c).edit().putString("carpeta", dir.getAbsolutePath()).apply();
    }

    /** Quan una carpeta canvia de nom, actualitza on es grava i quina és l'última gravació. */
    public static void reubica(Context c, File vella, File nova) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        for (String clau : new String[]{"carpeta", "ultima"}) {
            String v = p.getString(clau, null);
            String a = vella.getAbsolutePath();
            if (v != null && (v.equals(a) || v.startsWith(a + File.separator))) {
                e.putString(clau, nova.getAbsolutePath() + v.substring(a.length()));
            }
        }
        e.apply();
    }

    /** «Biblioteca» o «Cançó nova / Pont». */
    public static String nomCarpeta(Context c, File dir) {
        String r = biblioteca(c).ruta(dir);
        return (r == null || r.isEmpty()) ? "Biblioteca" : r;
    }

    public static int colorMode(Fitxa f) {
        return f.esVeu() ? VEU : GUITARRA;
    }

    public static int iconaMode(Fitxa f) {
        return f.esVeu() ? Icona.MIC : Icona.GUITARRA;
    }

    /** Color amb transparència (0..255). */
    /** Color d'una pista de l'Estudi: lila la bateria, turquesa la veu, ambre la guitarra. */
    public static int colorPista(Projecte.Pista t) {
        return t.esBateria() ? BATERIA : t.veu ? VEU : GUITARRA;
    }

    public static int iconaPista(Projecte.Pista t) {
        return t.esBateria() ? Icona.BATERIA : t.veu ? Icona.MIC : Icona.GUITARRA;
    }

    public static int alfa(int color, int alfa) {
        return (color & 0x00FFFFFF) | (alfa << 24);
    }

    // ---------- Mides i peces ----------

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView negreta(Context c, CharSequence s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(MITJA);
        return t;
    }

    /** Etiqueta petita en majúscules («ES DESA A»). */
    public static TextView etiqueta(Context c, String s) {
        TextView t = text(c, s.toUpperCase(new java.util.Locale("ca")), 11, SUAU);
        t.setTypeface(MITJA);
        t.setLetterSpacing(0.12f);
        return t;
    }

    public static TextView unaLinia(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static GradientDrawable rodo(int color, float radiPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiPx);
        return g;
    }

    public static GradientDrawable rodoVora(int color, int vora, float radiPx, int gruixPx) {
        GradientDrawable g = rodo(color, radiPx);
        g.setStroke(gruixPx, vora);
        return g;
    }

    public static RippleDrawable tocable(int color, float radiPx) {
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), rodo(color, radiPx), rodo(0xFFFFFFFF, radiPx));
    }

    public static ImageView imatge(Context c, int icona, int color, int midaDp) {
        ImageView i = new ImageView(c);
        i.setImageDrawable(new Icona(icona, color, dp(c, midaDp)));
        i.setScaleType(ImageView.ScaleType.CENTER);
        return i;
    }

    /** Botó rodó només amb icona. */
    public static FrameLayout botoIcona(Context c, int icona, int color, int fons, int midaDp, int iconaDp, String descripcio) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(tocable(fons, dp(c, midaDp / 2f)));
        f.setClickable(true);
        f.setFocusable(true);
        f.setContentDescription(descripcio);
        ImageView i = imatge(c, icona, color, iconaDp);
        f.addView(i, new FrameLayout.LayoutParams(dp(c, midaDp), dp(c, midaDp), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, midaDp), dp(c, midaDp)));
        return f;
    }

    public static void colorIcona(android.view.ViewGroup boto, int color) {
        ImageView i = (ImageView) boto.getChildAt(0);
        ((Icona) i.getDrawable()).setColor(color);
    }

    public static void canviaIcona(FrameLayout boto, int icona, int color, int iconaDp) {
        ImageView i = (ImageView) boto.getChildAt(0);
        i.setImageDrawable(new Icona(icona, color, dp(boto.getContext(), iconaDp)));
    }

    /** Botó en forma de pastilla, amb icona opcional (0 = sense). */
    public static LinearLayout pastilla(Context c, int icona, String s, int fons, int colorText) {
        LinearLayout l = fila(c);
        l.setGravity(Gravity.CENTER);
        l.setBackground(tocable(fons, dp(c, 24)));
        l.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        l.setMinimumHeight(dp(c, 48));
        l.setClickable(true);
        l.setFocusable(true);
        if (icona != 0) {
            ImageView i = imatge(c, icona, colorText, 20);
            l.addView(i, new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)));
            l.addView(espai(c, 8));
        }
        TextView t = negreta(c, s, 15, colorText);
        l.addView(t);
        return l;
    }

    public static LinearLayout fila(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout columna(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** Fila tocable de llista: rajola amb icona, textos i el que s'hi afegeixi a la dreta. */
    public static LinearLayout targeta(Context c) {
        LinearLayout l = fila(c);
        l.setBackground(tocable(SUPERFICIE, dp(c, 16)));
        l.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        l.setClickable(true);
        l.setFocusable(true);
        return l;
    }

    /** Rajola quadrada arrodonida amb una icona (per a les llistes). */
    public static FrameLayout rajola(Context c, int icona, int colorIcona, int fons, int midaDp) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(rodo(fons, dp(c, 12)));
        f.addView(imatge(c, icona, colorIcona, Math.round(midaDp * 0.5f)),
                new FrameLayout.LayoutParams(dp(c, midaDp), dp(c, midaDp), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, midaDp), dp(c, midaDp)));
        return f;
    }

    public static LinearLayout.LayoutParams ample(Context c, int margeDalt) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, margeDalt);
        return p;
    }

    public static LinearLayout.LayoutParams pes(float pes) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, pes);
    }

    /** Espai fix (quadrat). */
    public static View espai(Context c, int dps) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dps), dp(c, dps)));
        return v;
    }

    /** Espai que s'estira dins una fila (horitzontal). Alçada fixa d'1 px: no estira la fila. */
    public static View mollaH(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    /** Espai que s'estira dins una columna (vertical). Amplada fixa d'1 px. */
    public static View mollaV(Context c, float pes) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, 0, pes));
        return v;
    }

    public static View separador(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINIA);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    public static EditText camp(Context c, String pista, boolean multilinia) {
        EditText e = new EditText(c);
        e.setHint(pista);
        e.setTextColor(TEXT);
        e.setHintTextColor(APAGAT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setBackground(rodoVora(SUPERFICIE, LINIA, dp(c, 14), 1));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        if (multilinia) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setSingleLine(false);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setSingleLine(true);
        }
        return e;
    }

    /** Pantalla amb fons fosc i marges laterals. */
    public static LinearLayout pantalla(Activity a) {
        LinearLayout arrel = columna(a);
        arrel.setBackgroundColor(FONS);
        return arrel;
    }

    /**
     * Barra de dalt de les pantalles secundàries: fletxa enrere, títol i, a la dreta, el que es vulgui.
     */
    public static LinearLayout barraEnrere(Activity a, CharSequence titol, View... dreta) {
        LinearLayout cap = fila(a);
        cap.setPadding(dp(a, 4), dp(a, 8), dp(a, 8), dp(a, 8));
        FrameLayout enrere = botoIcona(a, Icona.ENRERE, TEXT, 0x00000000, 44, 24, "Enrere");
        enrere.setOnClickListener(v -> a.finish());
        cap.addView(enrere);
        TextView t = unaLinia(negreta(a, titol, 18, TEXT));
        LinearLayout.LayoutParams lp = pes(1);
        lp.leftMargin = dp(a, 4);
        cap.addView(t, lp);
        for (View v : dreta) {
            cap.addView(v);
        }
        return cap;
    }

    public static void avis(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    // ---------- Diàlegs ----------

    public interface AmbText {
        void fet(String text);
    }

    public interface AmbCarpeta {
        void fet(File carpeta);
    }

    /** Demana un text (per exemple el nom d'una carpeta). */
    public static void demanaText(Activity a, String titol, String inicial, String boto, AmbText fet) {
        Full.demanaText(a, titol, inicial, boto, fet);
    }

    /** Llista totes les carpetes en arbre i deixa'n triar una (o crear-ne una de nova). */
    public static void triaCarpeta(Activity a, String titol, File actual, AmbCarpeta fet) {
        Full.triaCarpeta(a, titol, actual, fet);
    }

    /** Confirmació per a una acció que esborra (botó vermell). */
    public static void confirma(Activity a, String titol, String missatge, String boto, Runnable fet) {
        Full.confirma(a, titol, missatge, boto, true, fet);
    }

    public interface AmbIndex {
        void fet(int index);
    }

    /** Control segmentat: una fila d'opcions on només n'hi ha una de triada. */
    public static LinearLayout segmentat(Context c, String[] opcions, int triada, int color, AmbIndex fet) {
        LinearLayout seg = fila(c);
        seg.setBackground(rodo(SUPERFICIE2, dp(c, 22)));
        seg.setPadding(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        TextView[] ts = new TextView[opcions.length];
        for (int i = 0; i < opcions.length; i++) {
            TextView t = negreta(c, opcions[i], 13, SUAU);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(c, 38));
            t.setClickable(true);
            ts[i] = t;
            seg.addView(t, pes(1));
        }
        Runnable[] pinta = new Runnable[1];
        int[] actual = {triada};
        pinta[0] = () -> {
            for (int i = 0; i < ts.length; i++) {
                boolean a = i == actual[0];
                ts[i].setBackground(tocable(a ? color : 0x00000000, dp(c, 19)));
                ts[i].setTextColor(a ? (color == TEXT ? SOBRE_COLOR : 0xFFFFFFFF) : SUAU);
            }
        };
        for (int i = 0; i < ts.length; i++) {
            int idx = i;
            ts[i].setOnClickListener(v -> {
                actual[0] = idx;
                pinta[0].run();
                fet.fet(idx);
            });
        }
        pinta[0].run();
        return seg;
    }
}
