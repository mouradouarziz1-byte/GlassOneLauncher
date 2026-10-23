package com.mourad.wordaday

import android.content.Context
import java.time.LocalDate

internal enum class ThemeMode { SYSTEM, LIGHT, DARK }

internal data class AppSettings(
    val notificationsEnabled: Boolean,
    val notificationHour: Int,
    val notificationMinute: Int,
    val themeMode: ThemeMode,
    val dynamicColor: Boolean
)

internal class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun learnedDates(): Set<String> = prefs.getStringSet(KEY_LEARNED, emptySet())?.toSet().orEmpty()
    fun favoriteDates(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()
    fun quizCorrectDates(): Set<String> = prefs.getStringSet(KEY_QUIZ_CORRECT, emptySet())?.toSet().orEmpty()

    fun setLearnedDates(value: Set<String>) = prefs.edit().putStringSet(KEY_LEARNED, value.toSet()).apply()
    fun setFavoriteDates(value: Set<String>) = prefs.edit().putStringSet(KEY_FAVORITES, value.toSet()).apply()
    fun setQuizCorrectDates(value: Set<String>) = prefs.edit().putStringSet(KEY_QUIZ_CORRECT, value.toSet()).apply()

    fun settings(): AppSettings {
        val mode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        return AppSettings(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            notificationHour = prefs.getInt(KEY_NOTIFICATION_HOUR, 20).coerceIn(0, 23),
            notificationMinute = prefs.getInt(KEY_NOTIFICATION_MINUTE, 0).coerceIn(0, 59),
            themeMode = mode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        )
    }

    fun setNotificationsEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()

    fun setNotificationTime(hour: Int, minute: Int) = prefs.edit()
        .putInt(KEY_NOTIFICATION_HOUR, hour.coerceIn(0, 23))
        .putInt(KEY_NOTIFICATION_MINUTE, minute.coerceIn(0, 59))
        .apply()

    fun setThemeMode(mode: ThemeMode) = prefs.edit().putString(KEY_THEME, mode.name).apply()
    fun setDynamicColor(enabled: Boolean) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()

    fun clearProgress() {
        prefs.edit()
            .remove(KEY_LEARNED)
            .remove(KEY_FAVORITES)
            .remove(KEY_QUIZ_CORRECT)
            .apply()
    }

    companion object {
        const val PREFS = "word_a_day_prefs"
        const val KEY_NOTIFICATION_HOUR = "notification_hour"
        const val KEY_NOTIFICATION_MINUTE = "notification_minute"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

        private const val KEY_LEARNED = "learned_dates"
        private const val KEY_FAVORITES = "favorite_dates"
        private const val KEY_QUIZ_CORRECT = "quiz_correct_dates"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}

internal object ProgressCalculator {
    fun streak(learnedDates: Set<String>, today: LocalDate = LocalDate.now()): Int {
        if (learnedDates.isEmpty()) return 0
        var cursor = if (learnedDates.contains(today.toString())) today else today.minusDays(1)
        var count = 0
        while (learnedDates.contains(cursor.toString())) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun learnedInLastDays(
        learnedDates: Set<String>,
        days: Int,
        today: LocalDate = LocalDate.now()
    ): List<Boolean> {
        if (days <= 0) return emptyList()
        return (days - 1 downTo 0).map { offset ->
            learnedDates.contains(today.minusDays(offset.toLong()).toString())
        }
    }
}
