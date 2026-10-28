package cat.alarmaveu;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CalendarContract;

/**
 * Accés als comptes (Google, etc.) per poder demanar-ne la sincronització.
 *
 * Des d'Android 12L, si una app no té accés a un compte, Android ignora en silenci les seves peticions
 * de sincronització d'aquell compte i li diu que la sincronització està desactivada (encara que no ho estigui).
 * L'accés s'obté un sol cop: l'usuari tria el compte a la finestra estàndard d'Android.
 */
final class Comptes {

    private Comptes() {
    }

    /** true si l'app «veu» aquest compte (i, per tant, en pot demanar la sincronització). */
    static boolean teAcces(Context ctx, String nom, String tipus) {
        if (nom == null || tipus == null) return false;
        try {
            for (Account a : AccountManager.get(ctx).getAccountsByType(tipus)) {
                if (a.name.equalsIgnoreCase(nom)) return true;
            }
        } catch (RuntimeException ignorat) {
            // sense accés
        }
        return false;
    }

    /** Finestra d'Android per triar el compte; en triar-lo, l'app hi obté accés per sempre. */
    static Intent intentAcces(String nom, String tipus, String explicacio) {
        return AccountManager.newChooseAccountIntent(new Account(nom, tipus), null,
                new String[]{tipus}, explicacio, null, null, null);
    }

    /** Demana pujar ara mateix els canvis del calendari d'aquest compte. */
    static void pujaAra(String nom, String tipus) {
        Bundle extres = new Bundle();
        extres.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extres.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        extres.putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, true);
        ContentResolver.requestSync(new Account(nom, tipus), CalendarContract.AUTHORITY, extres);
    }
}
