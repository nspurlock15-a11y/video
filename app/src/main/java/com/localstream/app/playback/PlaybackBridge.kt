package com.localstream.app.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** In-process state shared between the playback service and the player screen. */
object PlaybackBridge {
    /** Set when playback was paused to ask "Are you still watching?". */
    val stillWatching = MutableStateFlow(false)

    val sleepTimer = MutableStateFlow<SleepTimer?>(null)
}

sealed interface SleepTimer {
    /** Pause when [android.os.SystemClock.elapsedRealtime] reaches [endsAtElapsed]. */
    data class After(val minutes: Int, val endsAtElapsed: Long) : SleepTimer

    /** Pause when the current episode finishes. */
    data object EndOfEpisode : SleepTimer
}
