package com.mourad.wordaday;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "daily_word_channel";
    private static final int ALARM_ID = 2207;

    @Override
    public void onReceive(Context context, Intent intent) {
        showDailyWord(context);
    }

    public static void schedule(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("word_a_day_prefs", Context.MODE_PRIVATE);
        int hour = prefs.getInt("notification_hour", 20);
        int minute = prefs.getInt("notification_minute", 0);

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    next.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
            );
        }
    }

    private static void showDailyWord(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Parola del giorno",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Una parola inglese nuova ogni giorno");
            manager.createNotificationChannel(channel);
        }

        WordRepository.Word word = WordRepository.today();
        String content = word.english + " = " + word.italian;
        String bigText = content + "\n\n" + word.example;

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.mourad.wordaday.R.drawable.ic_app)
                .setContentTitle("🇬🇧 Parola del giorno: " + word.english)
                .setContentText(content)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(bigText))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .build();

        manager.notify(1001, notification);
    }
}
