package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.TraceColorScheme
import com.example.model.TracerCaptureMode
import com.example.model.TracerPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tracerDataStore: DataStore<Preferences> by preferencesDataStore(name = "tracer_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val CLUB_TYPE = intPreferencesKey("club_type")
        val HANDEDNESS = intPreferencesKey("handedness")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val FRONT_CAMERA = booleanPreferencesKey("front_camera")
        val SHOW_PLANE_GUIDE = booleanPreferencesKey("show_plane_guide")
        val AUTO_ARM = booleanPreferencesKey("auto_arm")
        val SHOW_TRAIL_GHOST = booleanPreferencesKey("show_trail_ghost")
        val CAPTURE_MODE = intPreferencesKey("capture_mode")
        val TRACE_COLOR_SCHEME = intPreferencesKey("trace_color_scheme")
        val COUNTDOWN_SEC = intPreferencesKey("countdown_sec")
        val RECORD_DURATION_SEC = intPreferencesKey("record_duration_sec")
        val REPLAY_COUNT = intPreferencesKey("replay_count")
        val LOOP_REPLAY_SPEED = floatPreferencesKey("loop_replay_speed")
        val COUNTDOWN_BEEPS = booleanPreferencesKey("countdown_beeps")
    }

    val preferencesFlow: Flow<TracerPreferences> = context.tracerDataStore.data.map { prefs ->
        val clubOrd = prefs[Keys.CLUB_TYPE] ?: ClubType.IRON_7.ordinal
        val handOrd = prefs[Keys.HANDEDNESS] ?: Handedness.RIGHT.ordinal
        val modeOrd = prefs[Keys.CAPTURE_MODE] ?: TracerCaptureMode.AUTO_TRACE.ordinal
        val colorOrd = prefs[Keys.TRACE_COLOR_SCHEME] ?: TraceColorScheme.CLASSIC_RED_GREEN.ordinal
        TracerPreferences(
            clubType = ClubType.entries[clubOrd.coerceIn(0, ClubType.entries.lastIndex)],
            handedness = Handedness.entries[handOrd.coerceIn(0, Handedness.entries.lastIndex)],
            sensitivity = (prefs[Keys.SENSITIVITY] ?: 0.62f).coerceIn(0.2f, 1.0f),
            useFrontCamera = prefs[Keys.FRONT_CAMERA] ?: false,
            showPlaneGuide = prefs[Keys.SHOW_PLANE_GUIDE] ?: true,
            autoArm = prefs[Keys.AUTO_ARM] ?: true,
            showTrailGhost = prefs[Keys.SHOW_TRAIL_GHOST] ?: true,
            captureMode = TracerCaptureMode.entries[modeOrd.coerceIn(0, TracerCaptureMode.entries.lastIndex)],
            traceColorScheme = TraceColorScheme.entries[colorOrd.coerceIn(0, TraceColorScheme.entries.lastIndex)],
            countdownSec = (prefs[Keys.COUNTDOWN_SEC] ?: 3).coerceIn(2, 10),
            recordDurationSec = (prefs[Keys.RECORD_DURATION_SEC] ?: 4).coerceIn(2, 8),
            replayCount = (prefs[Keys.REPLAY_COUNT] ?: 2).coerceIn(1, 5),
            loopReplaySpeed = (prefs[Keys.LOOP_REPLAY_SPEED] ?: 0.5f).coerceIn(0.25f, 1.0f),
            countdownBeeps = prefs[Keys.COUNTDOWN_BEEPS] ?: true
        )
    }

    suspend fun setClubType(clubType: ClubType) {
        context.tracerDataStore.edit { it[Keys.CLUB_TYPE] = clubType.ordinal }
    }

    suspend fun setHandedness(handedness: Handedness) {
        context.tracerDataStore.edit { it[Keys.HANDEDNESS] = handedness.ordinal }
    }

    suspend fun setSensitivity(sensitivity: Float) {
        context.tracerDataStore.edit { it[Keys.SENSITIVITY] = sensitivity.coerceIn(0.2f, 1.0f) }
    }

    suspend fun setUseFrontCamera(front: Boolean) {
        context.tracerDataStore.edit { it[Keys.FRONT_CAMERA] = front }
    }

    suspend fun setShowPlaneGuide(show: Boolean) {
        context.tracerDataStore.edit { it[Keys.SHOW_PLANE_GUIDE] = show }
    }

    suspend fun setAutoArm(autoArm: Boolean) {
        context.tracerDataStore.edit { it[Keys.AUTO_ARM] = autoArm }
    }

    suspend fun setShowTrailGhost(show: Boolean) {
        context.tracerDataStore.edit { it[Keys.SHOW_TRAIL_GHOST] = show }
    }

    suspend fun setCaptureMode(mode: TracerCaptureMode) {
        context.tracerDataStore.edit { it[Keys.CAPTURE_MODE] = mode.ordinal }
    }

    suspend fun setTraceColorScheme(scheme: TraceColorScheme) {
        context.tracerDataStore.edit { it[Keys.TRACE_COLOR_SCHEME] = scheme.ordinal }
    }

    suspend fun setCountdownSec(seconds: Int) {
        context.tracerDataStore.edit { it[Keys.COUNTDOWN_SEC] = seconds.coerceIn(2, 10) }
    }

    suspend fun setRecordDurationSec(seconds: Int) {
        context.tracerDataStore.edit { it[Keys.RECORD_DURATION_SEC] = seconds.coerceIn(2, 8) }
    }

    suspend fun setReplayCount(count: Int) {
        context.tracerDataStore.edit { it[Keys.REPLAY_COUNT] = count.coerceIn(1, 5) }
    }

    suspend fun setLoopReplaySpeed(speed: Float) {
        context.tracerDataStore.edit { it[Keys.LOOP_REPLAY_SPEED] = speed.coerceIn(0.25f, 1.0f) }
    }

    suspend fun setCountdownBeeps(enabled: Boolean) {
        context.tracerDataStore.edit { it[Keys.COUNTDOWN_BEEPS] = enabled }
    }
}
