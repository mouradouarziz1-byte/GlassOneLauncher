package com.mourad.wordaday;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String PREFS = "word_a_day_prefs";
    private static final String LEARNED = "learned_dates";
    private static final String HOUR = "notification_hour";
    private static final String MINUTE = "notification_minute";

    private SharedPreferences prefs;
    private TextToSpeech tts;
    private TextView streakText;
    private TextView statsText;
    private TextView notificationTimeText;
    private Button learnedButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.UK);
                tts.setSpeechRate(0.85f);
            }
        });

        setContentView(buildScreen());
        NotificationReceiver.schedule(this);
        refreshProgress();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 247, 251));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView appName = text("WORD A DAY", 14, Color.rgb(91, 99, 255), true);
        appName.setLetterSpacing(0.12f);
        root.addView(appName);

        TextView title = text("Una parola. Ogni giorno.", 30, Color.rgb(24, 28, 44), true);
        setMargins(title, 0, 8, 0, 4);
        root.addView(title);

        TextView subtitle = text("5 minuti al giorno per portare il tuo inglese verso il B1.", 16, Color.rgb(100, 105, 125), false);
        root.addView(subtitle);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        setMargins(summary, 0, 22, 0, 18);
        root.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        streakText = summaryChip("🔥 0 giorni");
        statsText = summaryChip("✓ 0 studiate");
        summary.addView(streakText, weighted());
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(10), 1);
        summary.addView(new View(this), gap);
        summary.addView(statsText, weighted());

        WordRepository.Word today = WordRepository.today();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.setBackground(rounded(Color.WHITE, 24));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView category = text(today.category.toUpperCase(Locale.ROOT), 12, Color.rgb(91, 99, 255), true);
        category.setLetterSpacing(0.08f);
        card.addView(category);

        TextView word = text(today.english, 42, Color.rgb(20, 23, 37), true);
        setMargins(word, 0, 10, 0, 2);
        card.addView(word);

        TextView translation = text(today.italian, 19, Color.rgb(91, 99, 255), true);
        card.addView(translation);

        Button listen = button("🔊  Ascolta la pronuncia", false);
        setMargins(listen, 0, 18, 0, 18);
        listen.setOnClickListener(v -> speak(today.english));
        card.addView(listen);

        TextView exampleLabel = text("ESEMPIO", 12, Color.rgb(130, 134, 151), true);
        exampleLabel.setLetterSpacing(0.08f);
        card.addView(exampleLabel);

        TextView example = text("“" + today.example + "”", 18, Color.rgb(37, 41, 58), false);
        example.setTypeface(Typeface.create("sans", Typeface.ITALIC));
        setMargins(example, 0, 8, 0, 6);
        card.addView(example);

        TextView exampleIt = text(today.exampleItalian, 15, Color.rgb(105, 109, 127), false);
        card.addView(exampleIt);

        learnedButton = button("✓  Segna come imparata", true);
        setMargins(learnedButton, 0, 20, 0, 0);
        learnedButton.setOnClickListener(v -> toggleLearnedToday());
        card.addView(learnedButton);

        TextView dailyTip = text("💡 Prova a usare la parola di oggi in una tua frase prima di chiudere l'app.", 15, Color.rgb(76, 80, 99), false);
        dailyTip.setPadding(dp(18), dp(16), dp(18), dp(16));
        dailyTip.setBackground(rounded(Color.rgb(235, 237, 255), 18));
        setMargins(dailyTip, 0, 16, 0, 18);
        root.addView(dailyTip);

        TextView settingsTitle = text("Allenamento", 20, Color.rgb(27, 31, 46), true);
        root.addView(settingsTitle);

        Button history = button("📚  Vedi le ultime parole", false);
        setMargins(history, 0, 12, 0, 10);
        history.setOnClickListener(v -> showHistory());
        root.addView(history);

        notificationTimeText = text("", 15, Color.rgb(90, 95, 114), false);
        refreshNotificationTime();
        root.addView(notificationTimeText);

        Button notificationTime = button("🔔  Cambia orario notifica", false);
        setMargins(notificationTime, 0, 8, 0, 0);
        notificationTime.setOnClickListener(v -> chooseNotificationTime());
        root.addView(notificationTime);

        TextView footer = text("Funziona anche offline • Nessun account richiesto", 12, Color.rgb(145, 148, 163), false);
        footer.setGravity(Gravity.CENTER);
        setMargins(footer, 0, 24, 0, 0);
        root.addView(footer);

        return scroll;
    }

    private void speak(String word) {
        if (tts != null) {
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "daily_word");
        }
    }

    private void toggleLearnedToday() {
        String today = LocalDate.now().toString();
        Set<String> learned = new HashSet<>(prefs.getStringSet(LEARNED, new HashSet<>()));
        if (learned.contains(today)) {
            learned.remove(today);
            Toast.makeText(this, "Parola rimossa dalle imparate", Toast.LENGTH_SHORT).show();
        } else {
            learned.add(today);
            Toast.makeText(this, "Ottimo! Parola imparata 🎉", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putStringSet(LEARNED, learned).apply();
        refreshProgress();
    }

    private void refreshProgress() {
        Set<String> learned = prefs.getStringSet(LEARNED, new HashSet<>());
        int streak = calculateStreak(learned);
        if (streakText != null) streakText.setText("🔥 " + streak + (streak == 1 ? " giorno" : " giorni"));
        if (statsText != null) statsText.setText("✓ " + learned.size() + " studiate");

        boolean todayLearned = learned.contains(LocalDate.now().toString());
        if (learnedButton != null) {
            learnedButton.setText(todayLearned ? "✓  Imparata oggi" : "✓  Segna come imparata");
            learnedButton.setAlpha(todayLearned ? 0.78f : 1f);
        }
    }

    private int calculateStreak(Set<String> learned) {
        int streak = 0;
        LocalDate date = LocalDate.now();
        if (!learned.contains(date.toString())) date = date.minusDays(1);
        while (learned.contains(date.toString())) {
            streak++;
            date = date.minusDays(1);
        }
        return streak;
    }

    private void showHistory() {
        Set<String> learned = prefs.getStringSet(LEARNED, new HashSet<>());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ITALIAN);
        StringBuilder text = new StringBuilder();
        LocalDate date = LocalDate.now();

        for (int i = 0; i < 14; i++) {
            WordRepository.Word word = WordRepository.forDate(date);
            boolean done = learned.contains(date.toString());
            text.append(done ? "✓  " : "○  ")
                .append(date.format(formatter))
                .append("  •  ")
                .append(word.english)
                .append(" = ")
                .append(word.italian)
                .append("\n\n");
            date = date.minusDays(1);
        }

        new AlertDialog.Builder(this)
            .setTitle("Ultime 14 parole")
            .setMessage(text.toString().trim())
            .setPositiveButton("Chiudi", null)
            .show();
    }

    private void chooseNotificationTime() {
        int hour = prefs.getInt(HOUR, 20);
        int minute = prefs.getInt(MINUTE, 0);
        TimePickerDialog dialog = new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> {
            prefs.edit().putInt(HOUR, selectedHour).putInt(MINUTE, selectedMinute).apply();
            NotificationReceiver.schedule(this);
            refreshNotificationTime();
            Toast.makeText(this, "Notifica giornaliera impostata", Toast.LENGTH_SHORT).show();
        }, hour, minute, true);
        dialog.setTitle("Orario della parola del giorno");
        dialog.show();
    }

    private void refreshNotificationTime() {
        int hour = prefs.getInt(HOUR, 20);
        int minute = prefs.getInt(MINUTE, 0);
        if (notificationTimeText != null) {
            notificationTimeText.setText(String.format(Locale.ITALY, "Notifica giornaliera: %02d:%02d", hour, minute));
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setLineSpacing(0, 1.12f);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private TextView summaryChip(String value) {
        TextView tv = text(value, 14, Color.rgb(53, 57, 76), true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        tv.setBackground(rounded(Color.WHITE, 16));
        return tv;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(52));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        button.setTextColor(primary ? Color.WHITE : Color.rgb(56, 61, 84));
        button.setBackground(rounded(primary ? Color.rgb(91, 99, 255) : Color.rgb(239, 240, 247), 16));
        return button;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private void setMargins(View view, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        view.setLayoutParams(lp);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
