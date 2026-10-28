package cat.riffs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Colors, botons i diàlegs comuns de totes les pantalles. */
public final class Ui {

    public static final int FONS = 0xFF1A1714;
    public static final int TARGETA = 0xFF27221E;
    public static final int LINIA = 0xFF3B342E;
    public static final int TEXT = 0xFFF3ECE4;
    public static final int SUAU = 0xFFA39788;
    public static final int ACCENT = 0xFFE0563B;   // vermell de gravar
    public static final int GUITARRA = 0xFFF2B84B; // ambre
    public static final int VEU = 0xFF7FC4C9;      // turquesa

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

    /** «📚 Biblioteca» o «📁 Cançó nova / Pont». */
    public static String nomCarpeta(Context c, File dir) {
        String r = biblioteca(c).ruta(dir);
        return (r == null || r.isEmpty()) ? "📚 Biblioteca" : "📁 " + r;
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
        return t;
    }

    public static TextView titol(Context c, CharSequence s) {
        TextView t = text(c, s, 22, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static GradientDrawable rodo(int color, float radiPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiPx);
        return g;
    }

    /** Botó en forma de pastilla. */
    public static Button boto(Context c, CharSequence s, int fons, int colorText) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTextColor(colorText);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 44));
        b.setMinimumHeight(dp(c, 44));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rodo(fons, dp(c, 22)), null));
        return b;
    }

    /** Botó només de text, sense fons. */
    public static Button botoText(Context c, CharSequence s, int colorText) {
        Button b = boto(c, s, 0x00000000, colorText);
        b.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        return b;
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

    public static LinearLayout targeta(Context c) {
        LinearLayout l = columna(c);
        l.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), rodo(TARGETA, dp(c, 14)), null));
        l.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return l;
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

    public static View espai(Context c, int dps) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dps), dp(c, dps)));
        return v;
    }

    public static EditText camp(Context c, String pista, boolean multilinia) {
        EditText e = new EditText(c);
        e.setHint(pista);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF6E655C);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        e.setBackground(rodo(TARGETA, dp(c, 12)));
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

    /** Capçalera estàndard: «← Enrere», títol i (opcional) un botó a la dreta. */
    public static LinearLayout capcalera(Activity a, CharSequence titol, View dreta) {
        LinearLayout cap = fila(a);
        Button enrere = botoText(a, "← Enrere", ACCENT);
        enrere.setOnClickListener(v -> a.finish());
        cap.addView(enrere);
        TextView t = titol(a, titol);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cap.addView(t, pes(1));
        if (dreta != null) cap.addView(dreta);
        else cap.addView(espai(a, 72));
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
        EditText e = camp(a, "Nom", false);
        e.setText(inicial == null ? "" : inicial);
        e.setSelectAllOnFocus(true);
        FrameLayout marc = new FrameLayout(a);
        marc.setPadding(dp(a, 20), dp(a, 8), dp(a, 20), 0);
        marc.addView(e);
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(titol)
                .setView(marc)
                .setPositiveButton(boto, (dd, w) -> fet.fet(e.getText().toString()))
                .setNegativeButton("Cancel·la", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        e.requestFocus();
    }

    /** Llista totes les carpetes en arbre i deixa'n triar una (o crear-ne una de nova). */
    public static void triaCarpeta(Activity a, String titol, File actual, AmbCarpeta fet) {
        Biblioteca b = biblioteca(a);
        List<Biblioteca.Carpeta> arbre = b.arbre();
        List<String> noms = new ArrayList<>();
        for (Biblioteca.Carpeta c : arbre) {
            String marca = (actual != null && c.dir.equals(actual)) ? "  ✓" : "";
            if (c.nivell == 0) {
                noms.add("📚 Biblioteca (sense carpeta)" + marca);
            } else {
                StringBuilder s = new StringBuilder();
                for (int i = 1; i < c.nivell; i++) s.append("      ");
                s.append("📁 ").append(c.dir.getName()).append(marca);
                noms.add(s.toString());
            }
        }
        noms.add("＋ Carpeta nova…");
        new AlertDialog.Builder(a)
                .setTitle(titol)
                .setItems(noms.toArray(new String[0]), (d, i) -> {
                    if (i < arbre.size()) {
                        fet.fet(arbre.get(i).dir);
                    } else {
                        demanaText(a, "Carpeta nova", "", "Crea", nom -> {
                            try {
                                File c = b.creaCarpeta(b.arrel, nom);
                                historial(a, "📁 Carpeta nova: «" + c.getName() + "»");
                                fet.fet(c);
                            } catch (IllegalArgumentException ex) {
                                avis(a, ex.getMessage());
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel·la", null)
                .show();
    }

    public static void confirma(Activity a, String titol, String missatge, String boto, Runnable fet) {
        new AlertDialog.Builder(a)
                .setTitle(titol)
                .setMessage(missatge)
                .setPositiveButton(boto, (d, w) -> fet.run())
                .setNegativeButton("Cancel·la", null)
                .show();
    }
}
