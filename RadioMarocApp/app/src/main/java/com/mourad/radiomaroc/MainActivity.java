package com.mourad.radiomaroc;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView nowPlaying;

    private static final Station[] STATIONS = new Station[] {
            new Station("HIT Radio", "Hits • Rabat", "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3"),
            new Station("Medi 1 Radio", "News & Music • Tanger", "https://cdn.live.easybroadcast.io/live/83_medi1radio-maghreb_8s9i4bn/playlist.m3u8"),
            new Station("Radio Mars", "Sport • Casablanca", "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3"),
            new Station("Radio Aswat", "News & Talk • Casablanca", "https://broadcast.ice.infomaniak.ch/aswat-high.mp3"),
            new Station("Chada FM", "Musique marocaine", "https://edge16.vedge.infomaniak.com/livecast/ik:chadatv/manifest.m3u8"),
            new Station("MFM Radio", "Music For Morocco", "https://a5.asurahosting.com:7980/radio.mp3"),
            new Station("Med Radio", "Talk & Actualité", "https://medradio.ice.infomaniak.ch/medradio-128.mp3"),
            new Station("Radio Plus Agadir", "Agadir • Maroc", "https://stream-158.zeno.fm/bqdbb6hd0neuv")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(18, 18, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("🇲🇦 Radio Maroc");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("راديو المغرب  •  Le Maroc en direct");
        subtitle.setTextColor(Color.rgb(190, 190, 190));
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle);

        LinearLayout player = new LinearLayout(this);
        player.setOrientation(LinearLayout.VERTICAL);
        player.setPadding(dp(18), dp(16), dp(18), dp(16));
        player.setBackground(rounded(Color.rgb(34, 34, 34), 20));
        LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playerLp.setMargins(0, 0, 0, dp(20));
        root.addView(player, playerLp);

        TextView live = new TextView(this);
        live.setText("● LIVE");
        live.setTextColor(Color.rgb(193, 39, 45));
        live.setTextSize(13);
        live.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        player.addView(live);

        nowPlaying = new TextView(this);
        nowPlaying.setText("Scegli una stazione");
        nowPlaying.setTextColor(Color.WHITE);
        nowPlaying.setTextSize(19);
        nowPlaying.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowPlaying.setPadding(0, dp(6), 0, dp(12));
        player.addView(nowPlaying);

        Button stopButton = new Button(this);
        stopButton.setText("■  STOP");
        stopButton.setTextColor(Color.WHITE);
        stopButton.setTextSize(14);
        stopButton.setAllCaps(false);
        stopButton.setBackground(rounded(Color.rgb(0, 98, 51), 16));
        stopButton.setOnClickListener(v -> stopRadio());
        player.addView(stopButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        TextView section = new TextView(this);
        section.setText("Stazioni popolari");
        section.setTextColor(Color.WHITE);
        section.setTextSize(20);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setPadding(0, 0, 0, dp(10));
        root.addView(section);

        for (Station station : STATIONS) {
            root.addView(createStationButton(station));
        }

        TextView footer = new TextView(this);
        footer.setText("Internet richiesto • Gli stream appartengono alle rispettive emittenti");
        footer.setTextColor(Color.rgb(130, 130, 130));
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(20), dp(8), 0);
        root.addView(footer);

        return scroll;
    }

    private View createStationButton(Station station) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.setBackground(rounded(Color.rgb(31, 31, 31), 18));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> play(station));

        TextView name = new TextView(this);
        name.setText("▶   " + station.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(18);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(name);

        TextView meta = new TextView(this);
        meta.setText(station.meta);
        meta.setTextColor(Color.rgb(170, 170, 170));
        meta.setTextSize(13);
        meta.setPadding(dp(35), dp(3), 0, 0);
        card.addView(meta);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        return card;
    }

    private void play(Station station) {
        nowPlaying.setText("Connessione a " + station.name + "…");
        Intent intent = new Intent(this, RadioService.class);
        intent.setAction(RadioService.ACTION_PLAY);
        intent.putExtra(RadioService.EXTRA_NAME, station.name);
        intent.putExtra(RadioService.EXTRA_URL, station.url);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        nowPlaying.postDelayed(() -> nowPlaying.setText(station.name), 1200);
    }

    private void stopRadio() {
        Intent intent = new Intent(this, RadioService.class);
        intent.setAction(RadioService.ACTION_STOP);
        startService(intent);
        nowPlaying.setText("Riproduzione fermata");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class Station {
        final String name;
        final String meta;
        final String url;
        Station(String name, String meta, String url) {
            this.name = name;
            this.meta = meta;
            this.url = url;
        }
    }
}
