package com.mourad.wordaday

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private lateinit var preferences: UserPreferences

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = UserPreferences(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                tts?.setSpeechRate(0.86f)
            }
        }

        val initial = preferences.settings()
        if (initial.notificationsEnabled) {
            requestNotificationPermissionIfNeeded()
            NotificationScheduler.schedule(this)
        }

        setContent {
            var settings by remember { mutableStateOf(preferences.settings()) }
            var learnedDates by remember { mutableStateOf(preferences.learnedDates()) }
            var favoriteDates by remember { mutableStateOf(preferences.favoriteDates()) }
            var quizCorrectDates by remember { mutableStateOf(preferences.quizCorrectDates()) }

            WordADayTheme(settings.themeMode, settings.dynamicColor) {
                WordADayApp(
                    settings = settings,
                    learnedDates = learnedDates,
                    favoriteDates = favoriteDates,
                    quizCorrectDates = quizCorrectDates,
                    notificationsPermissionGranted = notificationPermissionGranted(),
                    onSpeak = ::speak,
                    onToggleLearned = { date ->
                        learnedDates = toggleDate(learnedDates, date)
                        preferences.setLearnedDates(learnedDates)
                    },
                    onToggleFavorite = { date ->
                        favoriteDates = toggleDate(favoriteDates, date)
                        preferences.setFavoriteDates(favoriteDates)
                    },
                    onQuizCorrect = { date ->
                        if (!quizCorrectDates.contains(date.toString())) {
                            quizCorrectDates = quizCorrectDates + date.toString()
                            preferences.setQuizCorrectDates(quizCorrectDates)
                        }
                    },
                    onNotificationsChanged = { enabled ->
                        preferences.setNotificationsEnabled(enabled)
                        settings = preferences.settings()
                        if (enabled) {
                            requestNotificationPermissionIfNeeded()
                            NotificationScheduler.schedule(this)
                        } else {
                            NotificationScheduler.cancel(this)
                        }
                    },
                    onPickNotificationTime = {
                        TimePickerDialog(
                            this,
                            { _, hour, minute ->
                                preferences.setNotificationTime(hour, minute)
                                settings = preferences.settings()
                                NotificationScheduler.schedule(this)
                            },
                            settings.notificationHour,
                            settings.notificationMinute,
                            true
                        ).apply { setTitle("Orario della parola del giorno") }.show()
                    },
                    onThemeChanged = { mode ->
                        preferences.setThemeMode(mode)
                        settings = preferences.settings()
                    },
                    onDynamicColorChanged = { enabled ->
                        preferences.setDynamicColor(enabled)
                        settings = preferences.settings()
                    },
                    onTestNotification = {
                        if (notificationPermissionGranted()) {
                            NotificationScheduler.showNow(this)
                        } else {
                            requestNotificationPermissionIfNeeded()
                        }
                    },
                    onResetProgress = {
                        preferences.clearProgress()
                        learnedDates = emptySet()
                        favoriteDates = emptySet()
                        quizCorrectDates = emptySet()
                    }
                )
            }
        }
    }

    private fun toggleDate(current: Set<String>, date: LocalDate): Set<String> {
        val key = date.toString()
        return if (current.contains(key)) current - key else current + key
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word_a_day_tts")
    }

    private fun notificationPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    TODAY("Oggi", Icons.Rounded.Home),
    REVIEW("Ripasso", Icons.Rounded.School),
    PROGRESS("Progressi", Icons.Rounded.Insights),
    SETTINGS("Impostazioni", Icons.Rounded.Settings)
}

@Composable
private fun WordADayApp(
    settings: AppSettings,
    learnedDates: Set<String>,
    favoriteDates: Set<String>,
    quizCorrectDates: Set<String>,
    notificationsPermissionGranted: Boolean,
    onSpeak: (String) -> Unit,
    onToggleLearned: (LocalDate) -> Unit,
    onToggleFavorite: (LocalDate) -> Unit,
    onQuizCorrect: (LocalDate) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onPickNotificationTime: () -> Unit,
    onThemeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onResetProgress: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                tonalElevation = 8.dp
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.TODAY -> TodayScreen(
                modifier = Modifier.padding(innerPadding),
                learnedDates = learnedDates,
                favoriteDates = favoriteDates,
                quizCorrectDates = quizCorrectDates,
                onSpeak = onSpeak,
                onToggleLearned = onToggleLearned,
                onToggleFavorite = onToggleFavorite,
                onQuizCorrect = onQuizCorrect
            )

            AppTab.REVIEW -> ReviewScreen(
                modifier = Modifier.padding(innerPadding),
                learnedDates = learnedDates,
                favoriteDates = favoriteDates,
                onSpeak = onSpeak,
                onToggleLearned = onToggleLearned,
                onToggleFavorite = onToggleFavorite
            )

            AppTab.PROGRESS -> ProgressScreen(
                modifier = Modifier.padding(innerPadding),
                learnedDates = learnedDates,
                favoriteDates = favoriteDates,
                quizCorrectDates = quizCorrectDates
            )

            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                settings = settings,
                notificationsPermissionGranted = notificationsPermissionGranted,
                onNotificationsChanged = onNotificationsChanged,
                onPickNotificationTime = onPickNotificationTime,
                onThemeChanged = onThemeChanged,
                onDynamicColorChanged = onDynamicColorChanged,
                onTestNotification = onTestNotification,
                onResetProgress = onResetProgress
            )
        }
    }
}

@Composable
private fun TodayScreen(
    modifier: Modifier,
    learnedDates: Set<String>,
    favoriteDates: Set<String>,
    quizCorrectDates: Set<String>,
    onSpeak: (String) -> Unit,
    onToggleLearned: (LocalDate) -> Unit,
    onToggleFavorite: (LocalDate) -> Unit,
    onQuizCorrect: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val word = WordRepository.today()
    val learned = learnedDates.contains(today.toString())
    val favorite = favoriteDates.contains(today.toString())
    val streak = ProgressCalculator.streak(learnedDates, today)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PageHeader(
                eyebrow = "WORD A DAY · V2",
                title = "La parola di oggi",
                subtitle = "Pochi minuti, tutti i giorni. È la costanza che porta al B1."
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("🔥", "$streak", if (streak == 1) "giorno" else "giorni", Modifier.weight(1f))
                StatPill("✓", learnedDates.size.toString(), "imparate", Modifier.weight(1f))
                StatPill("★", quizCorrectDates.size.toString(), "quiz", Modifier.weight(1f))
            }
        }

        item {
            DailyWordCard(
                word = word,
                favorite = favorite,
                onSpeak = { onSpeak(word.english) },
                onFavorite = { onToggleFavorite(today) }
            )
        }

        item {
            Button(
                onClick = { onToggleLearned(today) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = if (learned) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (learned) "Imparata oggi ✓" else "Segna come imparata", fontWeight = FontWeight.Bold)
            }
        }

        item {
            DailyQuizCard(
                date = today,
                word = word,
                alreadyCorrect = quizCorrectDates.contains(today.toString()),
                onCorrect = { onQuizCorrect(today) }
            )
        }

        item {
            TipCard("Prova a dire la frase ad alta voce due volte. Poi crea una frase tua usando “${word.english}”.")
        }
    }
}

@Composable
private fun DailyWordCard(
    word: WordRepository.Word,
    favorite: Boolean,
    onSpeak: () -> Unit,
    onFavorite: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF5659F5), Color(0xFF7657F7), Color(0xFFA04FE0))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(gradient)
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        word.category.uppercase(Locale.ROOT),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (favorite) "Rimuovi dai salvati" else "Salva parola",
                        tint = Color.White
                    )
                }
            }

            Text(
                word.english,
                color = Color.White,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(word.italian, color = Color.White.copy(alpha = 0.90f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(6.dp))
            Surface(
                color = Color.White.copy(alpha = 0.13f),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ESEMPIO", color = Color.White.copy(alpha = 0.70f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("“${word.example}”", color = Color.White, fontSize = 17.sp, fontStyle = FontStyle.Italic)
                    Text(word.exampleItalian, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp)
                }
            }

            FilledTonalButton(
                onClick = onSpeak,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.92f),
                    contentColor = Color(0xFF4A43BB)
                )
            ) {
                Icon(Icons.Rounded.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ascolta la pronuncia", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DailyQuizCard(
    date: LocalDate,
    word: WordRepository.Word,
    alreadyCorrect: Boolean,
    onCorrect: () -> Unit
) {
    val options = remember(date) {
        val alternatives = listOf(
            WordRepository.forDate(date.plusDays(1)).italian,
            WordRepository.forDate(date.plusDays(2)).italian,
            WordRepository.forDate(date.plusDays(3)).italian
        ).filter { it != word.italian }.distinct().take(2)
        (alternatives + word.italian).shuffled(Random(date.toEpochDay()))
    }
    var selected by rememberSaveable(date.toString()) { mutableStateOf<String?>(if (alreadyCorrect) word.italian else null) }
    val answered = selected != null

    ElevatedCard(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("QUIZ RAPIDO", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Cosa significa “${word.english}”?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            options.forEach { option ->
                val isCorrect = option == word.italian
                val isSelected = selected == option
                val container = when {
                    answered && isCorrect -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                }

                OutlinedButton(
                    onClick = {
                        if (!answered) {
                            selected = option
                            if (isCorrect) onCorrect()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = container)
                ) {
                    Text(option, modifier = Modifier.weight(1f), maxLines = 2)
                    if (answered && isCorrect) Text("✓", fontWeight = FontWeight.Bold)
                }
            }

            if (answered) {
                Text(
                    if (selected == word.italian) "Perfetto. Risposta corretta!" else "Quella corretta è: ${word.italian}",
                    color = if (selected == word.italian) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private enum class ReviewFilter { RECENT, LEARNED, SAVED }

@Composable
private fun ReviewScreen(
    modifier: Modifier,
    learnedDates: Set<String>,
    favoriteDates: Set<String>,
    onSpeak: (String) -> Unit,
    onToggleLearned: (LocalDate) -> Unit,
    onToggleFavorite: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    var filter by rememberSaveable { mutableStateOf(ReviewFilter.RECENT) }
    val dates = remember(today, learnedDates, favoriteDates, filter) {
        (0L until 60L).map { today.minusDays(it) }.filter { date ->
            when (filter) {
                ReviewFilter.RECENT -> true
                ReviewFilter.LEARNED -> learnedDates.contains(date.toString())
                ReviewFilter.SAVED -> favoriteDates.contains(date.toString())
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                eyebrow = "RIPASSO",
                title = "Le tue parole",
                subtitle = "Riascolta, salva e consolida quello che hai visto nei giorni scorsi."
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == ReviewFilter.RECENT, onClick = { filter = ReviewFilter.RECENT }, label = { Text("Recenti") })
                FilterChip(selected = filter == ReviewFilter.LEARNED, onClick = { filter = ReviewFilter.LEARNED }, label = { Text("Imparate") })
                FilterChip(selected = filter == ReviewFilter.SAVED, onClick = { filter = ReviewFilter.SAVED }, label = { Text("Salvate") })
            }
        }

        if (dates.isEmpty()) {
            item {
                EmptyState("Qui non c’è ancora niente", "Segna parole come imparate o salvale per ritrovarle qui.")
            }
        } else {
            items(dates, key = { it.toString() }) { date ->
                ReviewWordCard(
                    date = date,
                    learned = learnedDates.contains(date.toString()),
                    favorite = favoriteDates.contains(date.toString()),
                    onSpeak = onSpeak,
                    onToggleLearned = { onToggleLearned(date) },
                    onToggleFavorite = { onToggleFavorite(date) }
                )
            }
        }
    }
}

@Composable
private fun ReviewWordCard(
    date: LocalDate,
    learned: Boolean,
    favorite: Boolean,
    onSpeak: (String) -> Unit,
    onToggleLearned: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val word = remember(date) { WordRepository.forDate(date) }
    var expanded by rememberSaveable(date.toString()) { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(date.format(formatter).replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(word.english, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(word.italian, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(if (favorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = "Salva")
                }
                IconButton(onClick = { onSpeak(word.english) }) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = "Ascolta")
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(word.example, fontStyle = FontStyle.Italic)
                Text(word.exampleItalian, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                TextButton(onClick = onToggleLearned) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (learned) "Segnata come imparata" else "Segna come imparata")
                }
            } else if (learned) {
                Text("✓ Imparata", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProgressScreen(
    modifier: Modifier,
    learnedDates: Set<String>,
    favoriteDates: Set<String>,
    quizCorrectDates: Set<String>
) {
    val today = LocalDate.now()
    val streak = ProgressCalculator.streak(learnedDates, today)
    val week = ProgressCalculator.learnedInLastDays(learnedDates, 7, today)
    val categoryCounts = remember(learnedDates, today) {
        learnedDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isAfter(today) && !it.isBefore(today.minusDays(179)) }
            .map { WordRepository.forDate(it).category }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PageHeader(
                eyebrow = "PROGRESSI",
                title = "La costanza si vede",
                subtitle = "Non serve studiare ore: serve tornare ogni giorno."
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("🔥", streak.toString(), "streak", Modifier.weight(1f))
                StatPill("✓", learnedDates.size.toString(), "imparate", Modifier.weight(1f))
                StatPill("★", favoriteDates.size.toString(), "salvate", Modifier.weight(1f))
            }
        }
        item {
            ElevatedCard(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Ultimi 7 giorni", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val labels = listOf("L", "M", "M", "G", "V", "S", "D")
                        week.forEachIndexed { index, done ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(
                                        if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (done) "✓" else "·", color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                }
                                Text(labels[index], fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text("Quiz corretti: ${quizCorrectDates.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            ElevatedCard(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Categorie studiate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (categoryCounts.isEmpty()) {
                        Text("Segna qualche parola come imparata e qui vedrai dove stai facendo più pratica.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val highest = max(1, categoryCounts.maxOf { it.value })
                        categoryCounts.forEach { entry ->
                            CategoryProgress(entry.key, entry.value, entry.value.toFloat() / highest)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryProgress(label: String, count: Int, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0.08f, 1f)).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    settings: AppSettings,
    notificationsPermissionGranted: Boolean,
    onNotificationsChanged: (Boolean) -> Unit,
    onPickNotificationTime: () -> Unit,
    onThemeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onResetProgress: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PageHeader(
                eyebrow = "IMPOSTAZIONI",
                title = "Falla tua",
                subtitle = "Scegli quando ricevere la parola e come deve apparire l’app."
            )
        }

        item {
            SettingsGroup(title = "Promemoria", icon = Icons.Rounded.NotificationsActive) {
                SettingSwitchRow(
                    title = "Notifica giornaliera",
                    subtitle = "Una parola nuova ogni giorno",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = onNotificationsChanged
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingActionRow(
                    title = "Orario",
                    subtitle = String.format(Locale.ITALY, "%02d:%02d", settings.notificationHour, settings.notificationMinute),
                    enabled = settings.notificationsEnabled,
                    onClick = onPickNotificationTime
                )
                if (settings.notificationsEnabled && !notificationsPermissionGranted && Build.VERSION.SDK_INT >= 33) {
                    Text(
                        "Android non ha ancora concesso il permesso alle notifiche. Attivalo quando compare la richiesta di sistema.",
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
                OutlinedButton(
                    onClick = onTestNotification,
                    enabled = settings.notificationsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Invia una notifica di prova")
                }
            }
        }

        item {
            SettingsGroup(title = "Aspetto", icon = Icons.Rounded.Palette) {
                Text("Tema", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeChanged(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "Sistema"
                                        ThemeMode.LIGHT -> "Chiaro"
                                        ThemeMode.DARK -> "Scuro"
                                    }
                                )
                            }
                        )
                    }
                }
                SettingSwitchRow(
                    title = "Colori dinamici",
                    subtitle = "Abbina l’app ai colori del telefono",
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColorChanged
                )
            }
        }

        item {
            SettingsGroup(title = "Dati", icon = Icons.Rounded.Refresh) {
                Text("Progressi, parole salvate e quiz restano soltanto sul telefono.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Azzera progressi")
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Word a Day · 2.0", fontWeight = FontWeight.Bold)
                    Text("Offline · Nessun account · Nessuna pubblicità", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Azzerare i progressi?") },
            text = { Text("Verranno rimossi parole imparate, salvate e risultati dei quiz. Le impostazioni restano invariate.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetProgress()
                    showResetDialog = false
                }) { Text("Azzera") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
    }
}

@Composable
private fun StatPill(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$icon $value", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TipCard(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f), shape = RoundedCornerShape(22.dp)) {
        Text("💡  $text", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, lineHeight = 21.sp)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("📚", fontSize = 32.sp)
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, icon: ImageVector, content: @Composable Column.() -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingActionRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 13.sp)
        }
        Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.35f))
    }
}
