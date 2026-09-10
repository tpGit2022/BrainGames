package com.seeksky.braingames

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.content.edit
import androidx.core.database.sqlite.transaction
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val DATABASE_NAME = "schulte_scores.db"

/**
 * Persistent score repository backed by SQLite.
 *
 * Every completed game is an individual row so future versions can aggregate records by
 * difficulty and completion time for progress charts without changing the storage format.
 */
class ScoreStore(context: Context) {
    private val appContext = context.applicationContext
    private val database = ScoreDatabase(appContext)

    init {
        migrateLegacyPreferences()
    }

    fun getScores(): List<ScoreRecord> = database.getAllScores()

    fun add(score: ScoreRecord): List<ScoreRecord> {
        database.insertScore(score)
        return database.getAllScores()
    }

    fun clear() {
        database.deleteAllScores()
    }

    /** Flushes pending SQLite writes and exports a consistent database snapshot as a ZIP. */
    fun exportDatabase(outputStream: OutputStream) {
        database.checkpointAndClose()
        val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
        check(databaseFile.isFile) { "Score database does not exist" }
        writeDatabaseZip(databaseFile, outputStream)
    }

    private fun migrateLegacyPreferences() {
        val preferences = appContext.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val legacyValue = preferences.getString(LEGACY_KEY_SCORES, null) ?: return
        val legacyScores = ScoreCodec.decode(legacyValue)
        if (legacyScores.isNotEmpty()) database.insertScores(legacyScores)
        preferences.edit { remove(LEGACY_KEY_SCORES) }
    }

    private companion object {
        const val LEGACY_PREFERENCES_NAME = "schulte_scores"
        const val LEGACY_KEY_SCORES = "scores"
    }
}

private class ScoreDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_SCORES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_GRID_SIZE INTEGER NOT NULL,
                $COLUMN_ELAPSED_MILLIS INTEGER NOT NULL,
                $COLUMN_ERRORS INTEGER NOT NULL,
                $COLUMN_COMPLETED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX index_scores_size_date ON $TABLE_SCORES " +
                "($COLUMN_GRID_SIZE, $COLUMN_COMPLETED_AT)",
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insertScore(score: ScoreRecord) {
        writableDatabase.insertOrThrow(TABLE_SCORES, null, score.toContentValues())
    }

    fun insertScores(scores: List<ScoreRecord>) {
        writableDatabase.transaction {
            scores.forEach { score ->
                insertOrThrow(TABLE_SCORES, null, score.toContentValues())
            }
        }
    }

    fun getAllScores(): List<ScoreRecord> {
        val scores = mutableListOf<ScoreRecord>()
        readableDatabase.query(
            TABLE_SCORES,
            arrayOf(
                COLUMN_ID,
                COLUMN_GRID_SIZE,
                COLUMN_ELAPSED_MILLIS,
                COLUMN_ERRORS,
                COLUMN_COMPLETED_AT,
            ),
            null,
            null,
            null,
            null,
            "$COLUMN_COMPLETED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val sizeColumn = cursor.getColumnIndexOrThrow(COLUMN_GRID_SIZE)
            val elapsedColumn = cursor.getColumnIndexOrThrow(COLUMN_ELAPSED_MILLIS)
            val errorsColumn = cursor.getColumnIndexOrThrow(COLUMN_ERRORS)
            val completedColumn = cursor.getColumnIndexOrThrow(COLUMN_COMPLETED_AT)
            while (cursor.moveToNext()) {
                scores += ScoreRecord(
                    gridSize = cursor.getInt(sizeColumn),
                    elapsedMillis = cursor.getLong(elapsedColumn),
                    errors = cursor.getInt(errorsColumn),
                    completedAtMillis = cursor.getLong(completedColumn),
                    id = cursor.getLong(idColumn),
                )
            }
        }
        return scores
    }

    fun deleteAllScores() {
        writableDatabase.delete(TABLE_SCORES, null, null)
    }

    fun checkpointAndClose() {
        if (writableDatabase.isWriteAheadLoggingEnabled) {
            writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                cursor.moveToFirst()
            }
        }
        close()
    }

    private fun ScoreRecord.toContentValues() = ContentValues().apply {
        put(COLUMN_GRID_SIZE, gridSize)
        put(COLUMN_ELAPSED_MILLIS, elapsedMillis)
        put(COLUMN_ERRORS, errors)
        put(COLUMN_COMPLETED_AT, completedAtMillis)
    }

    private companion object {
        const val DATABASE_VERSION = 1
        const val TABLE_SCORES = "score_records"
        const val COLUMN_ID = "id"
        const val COLUMN_GRID_SIZE = "grid_size"
        const val COLUMN_ELAPSED_MILLIS = "elapsed_millis"
        const val COLUMN_ERRORS = "errors"
        const val COLUMN_COMPLETED_AT = "completed_at_millis"
    }
}

internal fun writeDatabaseZip(databaseFile: File, outputStream: OutputStream) {
    ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOutput ->
        zipOutput.putNextEntry(ZipEntry(DATABASE_NAME).apply {
            time = databaseFile.lastModified()
        })
        BufferedInputStream(databaseFile.inputStream()).use { databaseInput ->
            databaseInput.copyTo(zipOutput)
        }
        zipOutput.closeEntry()
    }
}

internal object ScoreCodec {
    fun encode(scores: List<ScoreRecord>): String = scores.joinToString(";") { score ->
        listOf(
            score.gridSize,
            score.elapsedMillis,
            score.errors,
            score.completedAtMillis,
        ).joinToString(",")
    }

    fun decode(value: String): List<ScoreRecord> {
        if (value.isBlank()) return emptyList()
        return value.split(';').mapNotNull { encodedScore ->
            val values = encodedScore.split(',')
            if (values.size != 4) return@mapNotNull null

            val gridSize = values[0].toIntOrNull() ?: return@mapNotNull null
            val elapsedMillis = values[1].toLongOrNull() ?: return@mapNotNull null
            val errors = values[2].toIntOrNull() ?: return@mapNotNull null
            val completedAtMillis = values[3].toLongOrNull() ?: return@mapNotNull null
            if (gridSize !in MIN_GRID_SIZE..MAX_GRID_SIZE || elapsedMillis < 0 || errors < 0) {
                return@mapNotNull null
            }

            ScoreRecord(gridSize, elapsedMillis, errors, completedAtMillis)
        }
    }
}
