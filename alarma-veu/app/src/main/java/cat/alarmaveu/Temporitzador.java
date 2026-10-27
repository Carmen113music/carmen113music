package cat.alarmaveu;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/**
 * Temporitzador propi de l'app (no depèn de l'app de Rellotge del mòbil):
 * - mentre compta, hi ha una notificació amb el compte enrere i un botó «Cancel·la»;
 * - quan s'acaba, sona com una alarma (so d'alarma repetit i vibració) fins que es toca «Atura».
 */
public class Temporitzador extends BroadcastReceiver {

    private static final String SONA = "cat.alarmaveu.TEMPORITZADOR_SONA";
    private static final String ATURA = "cat.alarmaveu.TEMPORITZADOR_ATURA";
    private static final String CANCELA = "cat.alarmaveu.TEMPORITZADOR_CANCELA";
    private static final String EXTRA_ID = "id";
    private static final String EXTRA_TITOL = "titol";

    private static final String CANAL_COMPTE = "compte_enrere";
    private static final String CANAL_SONA = "temporitzador_sona";

    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    /** Engega un temporitzador de {@code segons} segons. */
    public static void programa(Context ctx, int segons, String titol) {
        creaCanals(ctx);
        long quan = System.currentTimeMillis() + segons * 1000L;
        int id = (int) ((quan / 1000L) % 1_000_000_000L);

        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent sona = accio(ctx, SONA, id, titol);
        boolean exacte = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exacte) {
            PendingIntent obre = PendingIntent.getActivity(ctx, id, new Intent(ctx, MainActivity.class), FLAGS);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(quan, obre), sona);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quan, sona);
        }

        Notification compte = new Notification.Builder(ctx, CANAL_COMPTE)
                .setSmallIcon(R.drawable.ic_temporitzador)
                .setContentTitle("⏱ " + titol)
                .setContentText("Compte enrere")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(quan)
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(segons * 1000L)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(ctx, R.drawable.ic_temporitzador), "Cancel·la",
                        accio(ctx, CANCELA, id, titol)).build())
                .build();
        ctx.getSystemService(NotificationManager.class).notify(id, compte);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int id = intent.getIntExtra(EXTRA_ID, 0);
        String titol = intent.getStringExtra(EXTRA_TITOL);
        if (titol == null) titol = "Temporitzador";
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        String a = intent.getAction();
        if (SONA.equals(a)) {
            nm.cancel(id);
            creaCanals(ctx);
            PendingIntent atura = accio(ctx, ATURA, id, titol);
            Notification n = new Notification.Builder(ctx, CANAL_SONA)
                    .setSmallIcon(R.drawable.ic_temporitzador)
                    .setContentTitle("⏰ " + titol)
                    .setContentText("S'ha acabat el temps. Toca per aturar.")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setContentIntent(atura)
                    .setDeleteIntent(atura)
                    .setAutoCancel(true)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(ctx, R.drawable.ic_temporitzador), "Atura", atura).build())
                    .build();
            n.flags |= Notification.FLAG_INSISTENT; // el so es repeteix fins que s'atura
            nm.notify(id, n);
        } else if (ATURA.equals(a)) {
            nm.cancel(id);
        } else if (CANCELA.equals(a)) {
            ctx.getSystemService(AlarmManager.class).cancel(accio(ctx, SONA, id, titol));
            nm.cancel(id);
        }
    }

    private static PendingIntent accio(Context ctx, String accio, int id, String titol) {
        Intent i = new Intent(ctx, Temporitzador.class)
                .setAction(accio)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITOL, titol);
        return PendingIntent.getBroadcast(ctx, id, i, FLAGS);
    }

    private static void creaCanals(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);

        NotificationChannel compte = new NotificationChannel(CANAL_COMPTE, "Compte enrere",
                NotificationManager.IMPORTANCE_LOW);
        compte.setDescription("Temporitzadors en marxa");
        nm.createNotificationChannel(compte);

        NotificationChannel sona = new NotificationChannel(CANAL_SONA, "Fi del temporitzador",
                NotificationManager.IMPORTANCE_HIGH);
        sona.setDescription("Sona quan s'acaba un temporitzador");
        Uri so = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (so == null) so = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        sona.setSound(so, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        sona.enableVibration(true);
        sona.setVibrationPattern(new long[]{0, 600, 400, 600, 400, 600});
        sona.setBypassDnd(true);
        nm.createNotificationChannel(sona);
    }
}
