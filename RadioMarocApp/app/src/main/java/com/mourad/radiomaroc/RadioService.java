package com.mourad.radiomaroc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;

public class RadioService extends Service {

    public static final String ACTION_PLAY = "com.mourad.radiomaroc.PLAY";
    public static final String ACTION_STOP = "com.mourad.radiomaroc.STOP";
    public static final String EXTRA_NAME = "station_name";
    public static final String EXTRA_URL = "station_url";

    private static final String CHANNEL_ID = "radio_playback";
    private static final int NOTIFICATION_ID = 42;

    private MediaPlayer player;
    private AudioManager audioManager;
    private String currentName = "Radio Maroc";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopPlayback();
            return START_NOT_STICKY;
        }

        if (ACTION_PLAY.equals(intent.getAction())) {
            String name = intent.getStringExtra(EXTRA_NAME);
            String url = intent.getStringExtra(EXTRA_URL);
            if (name != null && url != null) {
                currentName = name;
                startForeground(NOTIFICATION_ID, buildNotification("Connessione…"));
                startStation(url);
            }
        }
        return START_NOT_STICKY;
    }

    private void startStation(String url) {
        releasePlayer();
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnPreparedListener(mp -> {
            mp.start();
            updateNotification("In riproduzione");
        });
        player.setOnErrorListener((mp, what, extra) -> {
            updateNotification("Stream non disponibile");
            return true;
        });
        player.setOnCompletionListener(mp -> updateNotification("Stream terminato"));

        try {
            player.setDataSource(this, Uri.parse(url));
            player.prepareAsync();
        } catch (Exception e) {
            updateNotification("Errore di connessione");
        }
    }

    private Notification buildNotification(String status) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 10, openApp, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, RadioService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 11, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.mourad.radiomaroc.R.drawable.ic_radio)
                .setContentTitle(currentName)
                .setContentText(status)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        com.mourad.radiomaroc.R.drawable.ic_radio, "Stop", stopPending).build())
                .build();
    }

    private void updateNotification(String status) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(status));
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Radio playback", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Controlli per la radio in riproduzione");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(channel);
    }

    private void stopPlayback() {
        releasePlayer();
        audioManager.abandonAudioFocus(null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.reset();
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
