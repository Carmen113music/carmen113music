package cat.alarmaveu;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.AlarmClock;

import java.util.ArrayList;
import java.util.List;

/**
 * Maneres diferents de demanar un temporitzador al Rellotge. Cada marca de mòbil n'accepta unes o altres,
 * i la pantalla de diagnòstic permet provar-les i quedar-se amb la que funciona en aquest mòbil.
 *
 * Mode 0 = no fer servir el temporitzador del Rellotge: posar una alarma a l'hora que toca (sempre funciona).
 */
final class Temporitzadors {

    static final String PREFS = "config";
    static final String CLAU_MODE = "mode_temporitzador";
    static final int MODES = 5;

    private static final String RELLOTGE_GOOGLE = "com.google.android.deskclock";

    private Temporitzadors() {
    }

    static String nom(int mode) {
        switch (mode) {
            case 1: return "1 · Estàndard d'Android (durada en segons)";
            case 2: return "2 · Durada com a número llarg";
            case 3: return "3 · Sense «no mostris la pantalla»";
            case 4: return "4 · Durada en text";
            case 5: return "5 · Rellotge de Google";
            default: return "Alarma a l'hora que toca (sense temporitzador)";
        }
    }

    /** Intent per al mode indicat; null si aquest mode no es pot fer en aquest mòbil. */
    static Intent crea(Context ctx, int mode, int segons, String titol) {
        Intent t = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_MESSAGE, titol)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        switch (mode) {
            case 1:
                t.putExtra(AlarmClock.EXTRA_LENGTH, segons).putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                break;
            case 2:
                t.putExtra(AlarmClock.EXTRA_LENGTH, (long) segons).putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                break;
            case 3:
                t.putExtra(AlarmClock.EXTRA_LENGTH, segons);
                break;
            case 4:
                t.putExtra(AlarmClock.EXTRA_LENGTH, String.valueOf(segons)).putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                break;
            case 5:
                if (!installat(ctx, RELLOTGE_GOOGLE)) return null;
                t.setPackage(RELLOTGE_GOOGLE);
                t.putExtra(AlarmClock.EXTRA_LENGTH, segons).putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                break;
            default:
                return null;
        }
        return t;
    }

    static int modeTriat(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(CLAU_MODE, 0);
    }

    static void triaMode(Context ctx, int mode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(CLAU_MODE, mode).apply();
    }

    static boolean installat(Context ctx, String paquet) {
        try {
            ctx.getPackageManager().getPackageInfo(paquet, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Apps que responen a una acció (per exemple, quins rellotges accepten temporitzadors). */
    static List<String> appsPer(Context ctx, String accio) {
        List<String> noms = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(accio), 0)) {
            noms.add(ri.loadLabel(pm) + " (" + ri.activityInfo.packageName + ")");
        }
        return noms;
    }
}
