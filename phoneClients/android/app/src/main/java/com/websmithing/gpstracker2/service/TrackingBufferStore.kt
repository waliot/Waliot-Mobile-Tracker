package com.websmithing.gpstracker2.service

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface TrackingBufferStore {
    fun loadState(): TrackingBufferSnapshot
    fun saveState(bufferedLocations: List<Location>, lastBufferedLocation: Location?)
    fun appendBufferedLocation(location: Location, maxSize: Int, lastBufferedLocation: Location?)
    fun removeOldestBufferedLocation()
    fun replaceLastBufferedLocation(location: Location?)
    fun clear()
}

data class TrackingBufferSnapshot(
    val bufferedLocations: List<Location> = emptyList(),
    val lastBufferedLocation: Location? = null,
)

@Singleton
class SQLiteTrackingBufferStore private constructor(
    private val dbHelper: TrackingBufferDatabaseHelper,
    private val sharedPreferences: SharedPreferences,
) : TrackingBufferStore {

    @Volatile
    private var legacySnapshotPresent: Boolean = sharedPreferences.contains(KEY_TRACKING_BUFFER_STATE)

    companion object {
        private const val TAG = "TrackingBufferStore"
        internal const val KEY_TRACKING_BUFFER_STATE = "trackingBufferState"
        private const val DEFAULT_DATABASE_NAME = "tracking-buffer.db"
        // Any future schema change must bump this version together with an explicit migration
        // and matching contract coverage. The current implementation only supports schema v1.
        private const val DATABASE_VERSION = 1

        private const val TABLE_BUFFERED_LOCATIONS = "buffered_locations"
        private const val TABLE_METADATA = "metadata"
        private const val COLUMN_ID = "id"
        private const val COLUMN_PROVIDER = "provider"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
        private const val COLUMN_ALTITUDE = "altitude"
        private const val COLUMN_ACCURACY = "accuracy"
        private const val COLUMN_BEARING = "bearing"
        private const val COLUMN_SPEED = "speed"
        private const val COLUMN_TIME = "time"
        private const val COLUMN_SATELLITES = "satellites"
        private const val COLUMN_HDOP = "hdop"
        private const val COLUMN_KEY = "key_name"
        private const val COLUMN_VALUE = "value"
        private const val KEY_LAST_BUFFERED_LOCATION = "lastBufferedLocation"
    }

    @Inject
    constructor(
        @ApplicationContext context: Context,
        sharedPreferences: SharedPreferences,
    ) : this(
        dbHelper = TrackingBufferDatabaseHelper(context, DEFAULT_DATABASE_NAME),
        sharedPreferences = sharedPreferences,
    )

    internal constructor(
        context: Context,
        sharedPreferences: SharedPreferences,
        databaseName: String,
    ) : this(
        dbHelper = TrackingBufferDatabaseHelper(context, databaseName),
        sharedPreferences = sharedPreferences,
    )

    override fun loadState(): TrackingBufferSnapshot {
        return runCatching {
            migrateLegacySnapshotIfRequired()
            readSnapshot(dbHelper.readableDatabase)
        }.getOrElse { error ->
            Timber.tag(TAG).e(error, "Failed to restore persisted tracking buffer state")
            clear()
            TrackingBufferSnapshot()
        }
    }

    override fun saveState(bufferedLocations: List<Location>, lastBufferedLocation: Location?) {
        runCatching {
            dbHelper.writableDatabase.transaction { database ->
                database.delete(TABLE_BUFFERED_LOCATIONS, null, null)
                bufferedLocations.forEach { location ->
                    database.insertOrThrow(TABLE_BUFFERED_LOCATIONS, null, location.toContentValues())
                }
                database.replaceLastBufferedLocation(lastBufferedLocation)
            }
            clearLegacySnapshot()
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to persist tracking buffer state")
        }
    }

    override fun appendBufferedLocation(location: Location, maxSize: Int, lastBufferedLocation: Location?) {
        runCatching {
            dbHelper.writableDatabase.transaction { database ->
                database.insertOrThrow(TABLE_BUFFERED_LOCATIONS, null, location.toContentValues())
                database.trimBufferedLocationsToLast(maxSize)
                database.replaceLastBufferedLocation(lastBufferedLocation)
            }
            clearLegacySnapshot()
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to append buffered location")
        }
    }

    override fun removeOldestBufferedLocation() {
        runCatching {
            dbHelper.writableDatabase.transaction { database ->
                database.removeOldestBufferedLocation()
            }
            clearLegacySnapshot()
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to remove oldest buffered location")
        }
    }

    override fun replaceLastBufferedLocation(location: Location?) {
        runCatching {
            dbHelper.writableDatabase.transaction { database ->
                database.replaceLastBufferedLocation(location)
            }
            clearLegacySnapshot()
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to replace last buffered location")
        }
    }

    override fun clear() {
        runCatching {
            dbHelper.writableDatabase.transaction { database ->
                database.delete(TABLE_BUFFERED_LOCATIONS, null, null)
                database.delete(TABLE_METADATA, null, null)
            }
            clearLegacySnapshot()
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to clear persisted tracking buffer state")
        }
    }

    private fun migrateLegacySnapshotIfRequired() {
        val legacyState = sharedPreferences.getString(KEY_TRACKING_BUFFER_STATE, null) ?: run {
            legacySnapshotPresent = false
            return
        }
        legacySnapshotPresent = true
        val currentState = readSnapshot(dbHelper.readableDatabase)

        if (currentState.bufferedLocations.isNotEmpty() || currentState.lastBufferedLocation != null) {
            clearLegacySnapshot()
            return
        }

        val migratedState = runCatching {
            val json = JSONObject(legacyState)
            TrackingBufferSnapshot(
                bufferedLocations = json.optJSONArray("bufferedLocations")?.toLocationList().orEmpty(),
                lastBufferedLocation = json.optJSONObject(KEY_LAST_BUFFERED_LOCATION)?.toLocation(),
            )
        }.getOrElse { error ->
            Timber.tag(TAG).e(error, "Failed to migrate legacy tracking buffer state")
            clearLegacySnapshot()
            return
        }

        dbHelper.writableDatabase.transaction { database ->
            database.delete(TABLE_BUFFERED_LOCATIONS, null, null)
            migratedState.bufferedLocations.forEach { location ->
                database.insertOrThrow(TABLE_BUFFERED_LOCATIONS, null, location.toContentValues())
            }
            database.replaceLastBufferedLocation(migratedState.lastBufferedLocation)
        }
        clearLegacySnapshot()
    }

    private fun readSnapshot(database: SQLiteDatabase): TrackingBufferSnapshot {
        val bufferedLocations = buildList {
            database.query(
                TABLE_BUFFERED_LOCATIONS,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_ID ASC"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    add(cursor.toLocation())
                }
            }
        }

        val lastBufferedLocation = database.query(
            TABLE_METADATA,
            arrayOf(COLUMN_VALUE),
            "$COLUMN_KEY = ?",
            arrayOf(KEY_LAST_BUFFERED_LOCATION),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VALUE))
                    ?.takeIf { it.isNotBlank() }
                    ?.let { JSONObject(it).toLocation() }
            }
        }

        return TrackingBufferSnapshot(
            bufferedLocations = bufferedLocations,
            lastBufferedLocation = lastBufferedLocation,
        )
    }

    private fun clearLegacySnapshot() {
        if (!legacySnapshotPresent) {
            return
        }

        sharedPreferences.edit()
            .remove(KEY_TRACKING_BUFFER_STATE)
            .apply()
        legacySnapshotPresent = false
    }

    private fun SQLiteDatabase.replaceLastBufferedLocation(location: Location?) {
        if (location == null) {
            delete(
                TABLE_METADATA,
                "$COLUMN_KEY = ?",
                arrayOf(KEY_LAST_BUFFERED_LOCATION)
            )
            return
        }

        replace(
            TABLE_METADATA,
            null,
            ContentValues().apply {
                put(COLUMN_KEY, KEY_LAST_BUFFERED_LOCATION)
                put(COLUMN_VALUE, location.toJson().toString())
            }
        )
    }

    private fun SQLiteDatabase.trimBufferedLocationsToLast(maxSize: Int) {
        if (maxSize <= 0) {
            delete(TABLE_BUFFERED_LOCATIONS, null, null)
            return
        }

        execSQL(
            """
            DELETE FROM $TABLE_BUFFERED_LOCATIONS
            WHERE $COLUMN_ID NOT IN (
                SELECT $COLUMN_ID
                FROM $TABLE_BUFFERED_LOCATIONS
                ORDER BY $COLUMN_ID DESC
                LIMIT $maxSize
            )
            """.trimIndent()
        )
    }

    private fun SQLiteDatabase.removeOldestBufferedLocation() {
        val oldestId = query(
            TABLE_BUFFERED_LOCATIONS,
            arrayOf(COLUMN_ID),
            null,
            null,
            null,
            null,
            "$COLUMN_ID ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            } else {
                null
            }
        } ?: return

        delete(
            TABLE_BUFFERED_LOCATIONS,
            "$COLUMN_ID = ?",
            arrayOf(oldestId.toString())
        )
    }

    private fun SQLiteDatabase.transaction(block: (SQLiteDatabase) -> Unit) {
        beginTransaction()
        try {
            block(this)
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun JSONArray.toLocationList(): List<Location> {
        return buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(item.toLocation())
            }
        }
    }

    private fun android.database.Cursor.toLocation(): Location {
        return Location(getString(getColumnIndexOrThrow(COLUMN_PROVIDER))).apply {
            latitude = getDouble(getColumnIndexOrThrow(COLUMN_LATITUDE))
            longitude = getDouble(getColumnIndexOrThrow(COLUMN_LONGITUDE))
            altitude = getDouble(getColumnIndexOrThrow(COLUMN_ALTITUDE))
            accuracy = getFloat(getColumnIndexOrThrow(COLUMN_ACCURACY))
            bearing = getFloat(getColumnIndexOrThrow(COLUMN_BEARING))
            speed = getFloat(getColumnIndexOrThrow(COLUMN_SPEED))
            time = getLong(getColumnIndexOrThrow(COLUMN_TIME))

            val extras = Bundle()
            if (!isNull(getColumnIndexOrThrow(COLUMN_SATELLITES))) {
                extras.putInt(COLUMN_SATELLITES, getInt(getColumnIndexOrThrow(COLUMN_SATELLITES)))
            }
            if (!isNull(getColumnIndexOrThrow(COLUMN_HDOP))) {
                extras.putDouble(COLUMN_HDOP, getDouble(getColumnIndexOrThrow(COLUMN_HDOP)))
            }
            if (!extras.isEmpty) {
                this.extras = extras
            }
        }
    }

    private fun JSONObject.toLocation(): Location {
        return Location(optString(COLUMN_PROVIDER, "persisted")).apply {
            latitude = getDouble(COLUMN_LATITUDE)
            longitude = getDouble(COLUMN_LONGITUDE)
            altitude = optDouble(COLUMN_ALTITUDE, 0.0)
            accuracy = optDouble(COLUMN_ACCURACY, 0.0).toFloat()
            bearing = optDouble(COLUMN_BEARING, 0.0).toFloat()
            speed = optDouble(COLUMN_SPEED, 0.0).toFloat()
            time = optLong(COLUMN_TIME, 0L)

            val extras = Bundle()
            if (has(COLUMN_SATELLITES)) {
                extras.putInt(COLUMN_SATELLITES, getInt(COLUMN_SATELLITES))
            }
            if (has(COLUMN_HDOP)) {
                extras.putDouble(COLUMN_HDOP, getDouble(COLUMN_HDOP))
            }
            if (!extras.isEmpty) {
                this.extras = extras
            }
        }
    }

    private fun Location.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(COLUMN_PROVIDER, provider ?: "persisted")
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
            put(COLUMN_ALTITUDE, altitude)
            put(COLUMN_ACCURACY, accuracy)
            put(COLUMN_BEARING, bearing)
            put(COLUMN_SPEED, speed)
            put(COLUMN_TIME, time)
            extras?.takeIf { it.containsKey(COLUMN_SATELLITES) }?.let { bundle ->
                put(COLUMN_SATELLITES, bundle.getInt(COLUMN_SATELLITES))
            }
            extras?.takeIf { it.containsKey(COLUMN_HDOP) }?.let { bundle ->
                put(COLUMN_HDOP, bundle.getDouble(COLUMN_HDOP))
            }
        }
    }

    private fun Location.toJson(): JSONObject {
        return JSONObject().apply {
            put(COLUMN_PROVIDER, provider ?: "persisted")
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
            put(COLUMN_ALTITUDE, altitude)
            put(COLUMN_ACCURACY, accuracy.toDouble())
            put(COLUMN_BEARING, bearing.toDouble())
            put(COLUMN_SPEED, speed.toDouble())
            put(COLUMN_TIME, time)
            extras?.takeIf { it.containsKey(COLUMN_SATELLITES) }?.let { bundle ->
                put(COLUMN_SATELLITES, bundle.getInt(COLUMN_SATELLITES))
            }
            extras?.takeIf { it.containsKey(COLUMN_HDOP) }?.let { bundle ->
                put(COLUMN_HDOP, bundle.getDouble(COLUMN_HDOP))
            }
        }
    }

    private class TrackingBufferDatabaseHelper(
        context: Context,
        databaseName: String,
    ) : SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_BUFFERED_LOCATIONS (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_PROVIDER TEXT NOT NULL,
                    $COLUMN_LATITUDE REAL NOT NULL,
                    $COLUMN_LONGITUDE REAL NOT NULL,
                    $COLUMN_ALTITUDE REAL NOT NULL,
                    $COLUMN_ACCURACY REAL NOT NULL,
                    $COLUMN_BEARING REAL NOT NULL,
                    $COLUMN_SPEED REAL NOT NULL,
                    $COLUMN_TIME INTEGER NOT NULL,
                    $COLUMN_SATELLITES INTEGER,
                    $COLUMN_HDOP REAL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE $TABLE_METADATA (
                    $COLUMN_KEY TEXT PRIMARY KEY,
                    $COLUMN_VALUE TEXT
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Intentionally empty for schema v1 only.
            // Future schema changes must implement an explicit migration instead of silent reset.
            Unit
        }
    }
}
