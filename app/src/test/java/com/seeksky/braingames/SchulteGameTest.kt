package com.seeksky.braingames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.random.Random

class SchulteGameTest {
    @Test
    fun createBoard_containsEveryNumberExactlyOnce() {
        val board = createBoard(5, Random(42))

        assertEquals(25, board.size)
        assertEquals((1..25).toList(), board.sorted())
    }

    @Test(expected = IllegalArgumentException::class)
    fun createBoard_rejectsUnsupportedSize() {
        createBoard(2)
    }

    @Test
    fun evaluateTap_keepsTargetAfterWrongNumber() {
        val result = evaluateTap(expectedNumber = 4, tappedNumber = 9, cellCount = 9)

        assertFalse(result.isCorrect)
        assertFalse(result.isComplete)
        assertEquals(4, result.nextNumber)
    }

    @Test
    fun evaluateTap_advancesAfterCorrectNumber() {
        val result = evaluateTap(expectedNumber = 4, tappedNumber = 4, cellCount = 9)

        assertTrue(result.isCorrect)
        assertFalse(result.isComplete)
        assertEquals(5, result.nextNumber)
    }

    @Test
    fun evaluateTap_completesOnLastNumber() {
        val result = evaluateTap(expectedNumber = 9, tappedNumber = 9, cellCount = 9)

        assertTrue(result.isCorrect)
        assertTrue(result.isComplete)
    }

    @Test
    fun scoreCodec_roundTripsRecords() {
        val scores = listOf(
            ScoreRecord(3, 4_321L, 0, 100L),
            ScoreRecord(5, 18_765L, 2, 200L),
        )

        assertEquals(scores, ScoreCodec.decode(ScoreCodec.encode(scores)))
    }

    @Test
    fun formatDuration_formatsMinutesSecondsAndHundredths() {
        assertEquals("01:02.34", formatDuration(62_349L))
    }

    @Test
    fun buildTrainingMetricPoints_ordersSessionsAndCalculatesMetrics() {
        val scores = listOf(
            ScoreRecord(gridSize = 3, elapsedMillis = 4_500L, errors = 1, completedAtMillis = 200L),
            ScoreRecord(gridSize = 5, elapsedMillis = 10_000L, errors = 0, completedAtMillis = 150L),
            ScoreRecord(gridSize = 3, elapsedMillis = 9_000L, errors = 0, completedAtMillis = 100L),
        )

        val points = buildTrainingMetricPoints(scores, gridSize = 3)

        assertEquals(2, points.size)
        assertEquals(1, points[0].sessionNumber)
        assertEquals(1f, points[0].speedPerSecond, 0.001f)
        assertEquals(100f, points[0].accuracyPercent, 0.001f)
        assertEquals(2, points[1].sessionNumber)
        assertEquals(2f, points[1].speedPerSecond, 0.001f)
        assertEquals(90f, points[1].accuracyPercent, 0.001f)
    }

    @Test
    fun percentChange_reportsRelativeDifference() {
        assertEquals(25f, percentChange(first = 4f, latest = 5f)!!, 0.001f)
        assertEquals(null, percentChange(first = 0f, latest = 5f))
    }

    @Test
    fun writeDatabaseZip_containsCompleteDatabaseFile() {
        val databaseBytes = "sqlite database content".toByteArray()
        val databaseFile = File.createTempFile("schulte-test", ".db").apply {
            writeBytes(databaseBytes)
        }
        val output = ByteArrayOutputStream()

        try {
            writeDatabaseZip(databaseFile, output)

            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zipInput ->
                assertEquals("schulte_scores.db", zipInput.nextEntry.name)
                assertTrue(databaseBytes.contentEquals(zipInput.readBytes()))
                assertEquals(null, zipInput.nextEntry)
            }
        } finally {
            databaseFile.delete()
        }
    }
}
