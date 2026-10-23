package com.mourad.wordaday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressCalculatorTest {
    private val today = LocalDate.of(2026, 10, 23)

    @Test
    fun streakCountsTodayAndPreviousDays() {
        val learned = setOf(
            today.toString(),
            today.minusDays(1).toString(),
            today.minusDays(2).toString()
        )
        assertEquals(3, ProgressCalculator.streak(learned, today))
    }

    @Test
    fun streakCanStartFromYesterday() {
        val learned = setOf(
            today.minusDays(1).toString(),
            today.minusDays(2).toString()
        )
        assertEquals(2, ProgressCalculator.streak(learned, today))
    }

    @Test
    fun streakStopsAtFirstGap() {
        val learned = setOf(
            today.toString(),
            today.minusDays(2).toString()
        )
        assertEquals(1, ProgressCalculator.streak(learned, today))
    }

    @Test
    fun weeklyActivityIsOldestToNewest() {
        val learned = setOf(today.toString(), today.minusDays(6).toString())
        val week = ProgressCalculator.learnedInLastDays(learned, 7, today)
        assertEquals(7, week.size)
        assertTrue(week.first())
        assertTrue(week.last())
        assertFalse(week[1])
    }

    @Test
    fun wordRepositoryHasUsefulContent() {
        assertTrue(WordRepository.size() >= 80)
        val word = WordRepository.forDate(today)
        assertTrue(word.english.isNotBlank())
        assertTrue(word.italian.isNotBlank())
        assertTrue(word.example.isNotBlank())
        assertTrue(word.exampleItalian.isNotBlank())
        assertTrue(word.category.isNotBlank())
    }
}
