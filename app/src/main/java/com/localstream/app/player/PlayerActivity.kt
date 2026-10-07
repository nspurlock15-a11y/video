package com.localstream.app.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.localstream.app.R
import com.localstream.app.data.Library
import com.localstream.app.data.Settings
import com.localstream.app.playback.MediaItems
import com.localstream.app.playback.PlaybackService
import com.localstream.app.ui.MainActivity
import com.localstream.app.ui.theme.LocalStreamTheme

class PlayerActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    private var inPip by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    /** Intent waiting for the controller to connect. */
    private var pendingIntent: Intent? = null
    private var playerView: PlayerView? = null
    private var videoAspect: Rational? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val c = controller ?: return
            when (intent.getIntExtra(EXTRA_PIP_CONTROL, 0)) {
                PIP_PLAY_PAUSE -> if (c.playWhenReady) c.pause() else c.play()
                PIP_NEXT -> c.seekToNextMediaItem()
                PIP_PREVIOUS -> c.seekToPrevious()
            }
        }
    }

    private val controllerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val w = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
                videoAspect = clampAspect(w, videoSize.height)
                updatePipParams()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerView?.keepScreenOn = isPlaying
            updatePipParams()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updatePipParams()

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            errorMessage = null
            updatePipParams()
        }

        override fun onPlayerError(error: PlaybackException) {
            errorMessage = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                -> "This file can't be found any more. It may have been moved or deleted — try rescanning your library."
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                -> "This video uses a format your phone can't play (${error.errorCodeName})."
                else -> "Playback error: ${error.errorCodeName}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // A fresh launch carries the episode to play; a recreated activity just reattaches.
        pendingIntent = if (savedInstanceState == null) intent else null

        onBackPressedDispatcher.addCallback(this) { exitPlayer() }

        setContent {
            LocalStreamTheme {
                PlayerScreen(
                    controller = controller,
                    inPip = inPip,
                    errorMessage = errorMessage,
                    onDismissError = { errorMessage = null },
                    onBack = ::exitPlayer,
                    onEnterPip = ::enterPip,
                    onPlayerViewReady = { view ->
                        playerView = view
                        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePipParams() }
                    },
                )
            }
        }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try {
                future.get()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not start the player", Toast.LENGTH_LONG).show()
                finish()
                return@addListener
            }
            c.addListener(controllerListener)
            controller = c
            playerView?.keepScreenOn = c.isPlaying
            c.videoSize.let { controllerListener.onVideoSizeChanged(it) }
            pendingIntent?.let { handleIntent(c, it) }
            pendingIntent = null
            if (c.mediaItemCount == 0) {
                // Opened from a stale notification with nothing to play.
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            updatePipParams()
        }, MoreExecutors.directExecutor())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val c = controller
        if (c != null) handleIntent(c, intent) else pendingIntent = intent
    }

    private fun handleIntent(c: MediaController, intent: Intent) {
        val episodeId = intent.getStringExtra(EXTRA_EPISODE_ID) ?: return
        val startOver = intent.getBooleanExtra(EXTRA_START_OVER, false)
        if (c.currentMediaItem?.mediaId == episodeId && !startOver) {
            // Already the current episode (e.g. tapped again from the show page): just continue.
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
            return
        }
        val (episodes, index) = Library.playlistFor(episodeId) ?: run {
            Toast.makeText(this, "Episode not found in library", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val title = Library.lookup(episodeId)!!.first
        val start = if (startOver) 0 else Library.resumePositionMs(episodeId)
        errorMessage = null
        c.setMediaItems(episodes.map { MediaItems.build(title, it) }, index, start)
        c.prepare()
        c.play()
    }

    private fun exitPlayer() {
        controller?.pause()
        finish()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP by itself via setAutoEnterEnabled.
        if (Build.VERSION.SDK_INT < 31 && Settings.autoPip.value && controller?.isPlaying == true) enterPip()
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        val screenOff = !(getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        val backgrounded = !isInPictureInPictureMode || screenOff
        if (backgrounded && !Settings.backgroundPlay.value) controller?.pause()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        playerView?.let {
            it.useController = !isInPictureInPictureMode
            if (isInPictureInPictureMode) it.hideController()
        }
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            // The PiP window was swiped away / closed: stop like other streaming apps do.
            controller?.pause()
            finish()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pipReceiver)
        controller?.removeListener(controllerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ picture-in-picture

    private fun pipSupported() =
        Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun enterPip() {
        if (!pipSupported()) return
        try {
            enterPictureInPictureMode(buildPipParams())
        } catch (e: IllegalStateException) {
            // PiP disabled for this app in system settings.
        }
    }

    private fun updatePipParams() {
        if (!pipSupported()) return
        try {
            setPictureInPictureParams(buildPipParams())
        } catch (e: Exception) {
            // Ignored: e.g. aspect ratio out of range while the video is still loading.
        }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val c = controller
        val builder = PictureInPictureParams.Builder()
        videoAspect?.let { builder.setAspectRatio(it) }
        playerView?.let { v ->
            val r = Rect()
            if (v.getGlobalVisibleRect(r)) builder.setSourceRectHint(r)
        }
        val playing = c?.playWhenReady == true
        builder.setActions(
            listOf(
                pipAction(R.drawable.ic_skip_previous, "Previous", PIP_PREVIOUS),
                if (playing) pipAction(R.drawable.ic_pause, "Pause", PIP_PLAY_PAUSE)
                else pipAction(R.drawable.ic_play, "Play", PIP_PLAY_PAUSE),
                pipAction(R.drawable.ic_skip_next, "Next", PIP_NEXT),
            ),
        )
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(Settings.autoPip.value && playing && (c?.mediaItemCount ?: 0) > 0)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun pipAction(icon: Int, title: String, control: Int): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_CONTROL, control)
        val pending = PendingIntent.getBroadcast(
            this,
            control,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pending)
    }

    private fun clampAspect(w: Int, h: Int): Rational {
        val ratio = w.toFloat() / h
        return when {
            ratio > 2.39f -> Rational(239, 100)
            ratio < 1 / 2.39f -> Rational(100, 239)
            else -> Rational(w, h)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_START_OVER = "start_over"

        private const val ACTION_PIP_CONTROL = "com.localstream.app.PIP_CONTROL"
        private const val EXTRA_PIP_CONTROL = "control"
        private const val PIP_PLAY_PAUSE = 1
        private const val PIP_NEXT = 2
        private const val PIP_PREVIOUS = 3

        fun intent(context: Context, episodeId: String, startOver: Boolean = false): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_EPISODE_ID, episodeId)
                .putExtra(EXTRA_START_OVER, startOver)
    }
}
