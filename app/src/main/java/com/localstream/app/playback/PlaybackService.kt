package com.localstream.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.localstream.app.data.Library
import com.localstream.app.data.Settings
import com.localstream.app.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer instance and the media session. Because the player lives here
 * rather than in the activity, playback keeps going in picture-in-picture, with the
 * screen off and from the lock screen, and the system shows the standard media
 * notification / lock-screen controls (play, pause, previous, next, seek bar).
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null

    /** Episodes autoplayed in a row without the user touching anything. */
    private var autoAdvances = 0

    private val saveTicker = object : Runnable {
        override fun run() {
            saveCurrent()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val seekMs = Settings.seekIncrementSec.value * 1000L
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(seekMs)
            .setSeekForwardIncrementMs(seekMs)
            .build()
        player.addListener(listener)

        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openPlayer)
            .build()

        scope.launch { Settings.autoplay.flow.collect { applyPauseAtEnd() } }
        scope.launch { PlaybackBridge.sleepTimer.collect { onSleepTimerChanged(it) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCurrent()
        val keepPlaying = Settings.backgroundPlay.value &&
            player.playWhenReady &&
            player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_ENDED
        if (!keepPlaying) {
            player.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveCurrent()
        Library.flush()
        handler.removeCallbacks(saveTicker)
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ progress

    private fun saveCurrent(completed: Boolean = false) {
        if (!::player.isInitialized) return
        val item = player.currentMediaItem ?: return
        val duration = player.duration.takeIf { it > 0 } ?: 0
        Library.saveProgress(item.mediaId, player.currentPosition, duration, completed)
    }

    private fun applyPauseAtEnd() {
        player.pauseAtEndOfMediaItems =
            !Settings.autoplay.value || PlaybackBridge.sleepTimer.value == SleepTimer.EndOfEpisode
    }

    private fun onSleepTimerChanged(timer: SleepTimer?) {
        sleepJob?.cancel()
        applyPauseAtEnd()
        if (timer is SleepTimer.After) {
            sleepJob = scope.launch {
                delay((timer.endsAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                player.pause()
                PlaybackBridge.sleepTimer.value = null
            }
        }
    }

    private fun resetStillWatching() {
        autoAdvances = 0
        PlaybackBridge.stillWatching.value = false
    }

    private fun onAutoAdvance() {
        autoAdvances++
        val limit = Settings.stillWatchingAfter.value
        if (limit > 0 && autoAdvances >= limit) {
            player.pause()
            autoAdvances = 0
            PlaybackBridge.stillWatching.value = true
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(saveTicker)
            if (isPlaying) {
                handler.postDelayed(saveTicker, SAVE_INTERVAL_MS)
            } else {
                saveCurrent()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val oldItem = oldPosition.mediaItem
            if (oldItem != null && oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                // Leaving an episode: remember where it was left, or that it was finished.
                val auto = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                Library.saveProgress(oldItem.mediaId, oldPosition.positionMs, 0, completed = auto)
                if (auto) onAutoAdvance() else resetStillWatching()
            } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                resetStillWatching()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Record the new episode right away so it becomes "last watched".
            if (mediaItem != null) handler.post { saveCurrent() }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST && playWhenReady) {
                resetStillWatching()
            }
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                PlaybackBridge.sleepTimer.value == SleepTimer.EndOfEpisode
            ) {
                PlaybackBridge.sleepTimer.value = null
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                saveCurrent(completed = true)
                if (PlaybackBridge.sleepTimer.value == SleepTimer.EndOfEpisode) {
                    PlaybackBridge.sleepTimer.value = null
                }
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        // Controllers (the player screen, lock screen, Bluetooth, ...) only send media ids;
        // the file location and subtitles are filled in here from the library.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { MediaItems.resolve(it) }.toMutableList())

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            saveCurrent()
            resetStillWatching()
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    mediaItems.map { MediaItems.resolve(it) },
                    startIndex,
                    startPositionMs,
                ),
            )
        }

        // Lets the system media controls (e.g. after a reboot) resume the last episode.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val last = Library.lastWatched()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            val (episodes, index) = Library.playlistFor(last.id)
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            val title = Library.lookup(last.id)!!.first
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    episodes.map { MediaItems.build(title, it) },
                    index,
                    Library.resumePositionMs(last.id),
                ),
            )
        }
    }

    companion object {
        private const val SAVE_INTERVAL_MS = 5_000L
    }
}
