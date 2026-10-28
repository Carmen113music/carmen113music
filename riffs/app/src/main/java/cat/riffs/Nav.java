package cat.riffs;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Barra de navegació de baix: Gravar · Biblioteca · Estudi. */
public final class Nav {

    public static final int GRAVAR = 0, BIBLIOTECA = 1, ESTUDI = 2;

    private Nav() {}

    public static LinearLayout barra(Activity a, int actiu) {
        LinearLayout b = Ui.fila(a);
        b.setBackgroundColor(Ui.SUPERFICIE);
        b.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 8));
        b.addView(element(a, Icona.MIC, "Gravar", actiu == GRAVAR, () -> va(a, actiu, GRAVAR)), Ui.pes(1));
        b.addView(element(a, Icona.BIBLIOTECA, "Biblioteca", actiu == BIBLIOTECA, () -> va(a, actiu, BIBLIOTECA)), Ui.pes(1));
        b.addView(element(a, Icona.ESTUDI, "Estudi", actiu == ESTUDI, () -> va(a, actiu, ESTUDI)), Ui.pes(1));

        LinearLayout amb = Ui.columna(a);
        amb.addView(Ui.separador(a));
        amb.addView(b, Ui.ample(a, 0));
        return amb;
    }

    private static LinearLayout element(Activity a, int icona, String nom, boolean actiu, Runnable accio) {
        LinearLayout e = Ui.columna(a);
        e.setGravity(Gravity.CENTER);
        e.setClickable(true);
        e.setFocusable(true);
        e.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, null));
        int color = actiu ? Ui.TEXT : Ui.APAGAT;

        LinearLayout pastilla = Ui.fila(a);
        pastilla.setGravity(Gravity.CENTER);
        if (actiu) pastilla.setBackground(Ui.rodo(Ui.SUPERFICIE2, Ui.dp(a, 16)));
        ImageView i = Ui.imatge(a, icona, actiu ? Ui.ACCENT : color, 22);
        pastilla.addView(i, new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 32)));
        e.addView(pastilla);

        TextView t = Ui.text(a, nom, 12, color);
        if (actiu) t.setTypeface(Ui.MITJA);
        t.setGravity(Gravity.CENTER);
        e.addView(t, Ui.ample(a, 4));
        e.setOnClickListener(v -> accio.run());
        return e;
    }

    private static void va(Activity a, int des, int a2) {
        if (des == a2) return;
        Intent i;
        if (a2 == GRAVAR) {
            i = new Intent(a, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else if (a2 == BIBLIOTECA) {
            i = new Intent(a, BibliotecaActivity.class);
        } else {
            i = new Intent(a, EstudiActivity.class);
        }
        a.startActivity(i);
        a.overridePendingTransition(0, 0);
        // La pantalla principal sempre queda a sota; les altres pestanyes es substitueixen
        if (des != GRAVAR) {
            a.finish();
            a.overridePendingTransition(0, 0);
        }
    }
}
