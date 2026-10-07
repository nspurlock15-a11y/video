package com.localstream.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User preferences, persisted in SharedPreferences and exposed as flows. */
object Settings {
    private lateinit var prefs: SharedPreferences

    /** Play the next episode automatically when one ends. */
    val autoplay = BoolPref("autoplay", true)
    /** Keep audio playing when the app is in the background or the screen is off. */
    val backgroundPlay = BoolPref("background_play", true)
    /** Enter picture-in-picture automatically when leaving the player. */
    val autoPip = BoolPref("auto_pip", true)
    /** Pause and ask "Are you still watching?" after this many autoplayed episodes (0 = never). */
    val stillWatchingAfter = IntPref("still_watching_after", 3)
    /** Rewind / fast-forward step in seconds. */
    val seekIncrementSec = IntPref("seek_increment_sec", 10)
    /** Length of the jump made by the "Skip intro" button (0 = hide the button). */
    val introSkipSec = IntPref("intro_skip_sec", 0)
    /** Show the "Up next" card this many seconds before an episode ends (0 = never). */
    val upNextSec = IntPref("up_next_sec", 30)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        listOf(autoplay, backgroundPlay, autoPip).forEach { it.load() }
        listOf(stillWatchingAfter, seekIncrementSec, introSkipSec, upNextSec).forEach { it.load() }
    }

    class BoolPref(private val key: String, private val default: Boolean) {
        private val state = MutableStateFlow(default)
        val flow: StateFlow<Boolean> get() = state
        val value: Boolean get() = state.value

        internal fun load() {
            state.value = prefs.getBoolean(key, default)
        }

        fun set(v: Boolean) {
            state.value = v
            prefs.edit().putBoolean(key, v).apply()
        }
    }

    class IntPref(private val key: String, private val default: Int) {
        private val state = MutableStateFlow(default)
        val flow: StateFlow<Int> get() = state
        val value: Int get() = state.value

        internal fun load() {
            state.value = prefs.getInt(key, default)
        }

        fun set(v: Int) {
            state.value = v
            prefs.edit().putInt(key, v).apply()
        }
    }
}
