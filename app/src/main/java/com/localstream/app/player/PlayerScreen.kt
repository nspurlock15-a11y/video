package com.localstream.app.player

import android.os.SystemClock
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.localstream.app.R
import com.localstream.app.data.Episode
import com.localstream.app.data.Library
import com.localstream.app.data.Settings
import com.localstream.app.data.Title
import com.localstream.app.data.TitleType
import com.localstream.app.playback.PlaybackBridge
import com.localstream.app.playback.SleepTimer
import com.localstream.app.ui.ProgressBar
import com.localstream.app.ui.VideoThumbnail
import com.localstream.app.ui.formatDuration
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    controller: MediaController?,
    inPip: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    onPlayerViewReady: (PlayerView) -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var nextId by remember { mutableStateOf<String?>(null) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var upNextDismissedFor by remember { mutableStateOf<String?>(null) }
    var introSkippedFor by remember { mutableStateOf<String?>(null) }
    var showEpisodes by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    val stillWatching by PlaybackBridge.stillWatching.collectAsState()
    val sleepTimer by PlaybackBridge.sleepTimer.collectAsState()
    val upNextSec by Settings.upNextSec.flow.collectAsState()
    val introSkipSec by Settings.introSkipSec.flow.collectAsState()
    val autoplay by Settings.autoplay.flow.collectAsState()

    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            position = c.currentPosition
            duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
            currentId = c.currentMediaItem?.mediaId
            nextId = if (c.hasNextMediaItem()) c.getMediaItemAt(c.nextMediaItemIndex).mediaId else null
            delay(500)
        }
    }

    val current = currentId?.let { Library.lookup(it) }
    val next = nextId?.let { Library.lookup(it) }?.second

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    setShowSubtitleButton(true)
                    controllerShowTimeoutMs = 3_500
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == View.VISIBLE },
                    )
                    onPlayerViewReady(this)
                }
            },
            update = { view ->
                if (view.player !== controller) view.player = controller
                view.resizeMode = resizeMode
            },
        )

        if (!inPip) {
            // Top bar: back, what's playing, episodes, sleep timer, fill/fit, PiP.
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopBar(
                    title = current?.first,
                    episode = current?.second,
                    sleepActive = sleepTimer != null,
                    zoomed = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    onBack = onBack,
                    onEpisodes = { showEpisodes = true },
                    onSleep = { showSleep = true },
                    onToggleZoom = {
                        resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    },
                    onPip = onEnterPip,
                )
            }

            val bottomEnd = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 24.dp, bottom = 96.dp)

            // "Skip intro"
            val showSkipIntro = introSkipSec > 0 && currentId != null && introSkippedFor != currentId &&
                position in 1_000L..INTRO_WINDOW_MS && duration > INTRO_WINDOW_MS
            if (showSkipIntro) {
                OutlinedButton(
                    onClick = {
                        controller?.seekTo(position + introSkipSec * 1000L)
                        introSkippedFor = currentId
                    },
                    modifier = bottomEnd,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                    ),
                ) { Text("Skip intro") }
            }

            // "Up next" card during the end credits.
            val remaining = duration - position
            val showUpNext = next != null && upNextSec > 0 && duration > 0 &&
                remaining in 1..(upNextSec * 1000L) && upNextDismissedFor != currentId && !showSkipIntro
            if (showUpNext && next != null) {
                UpNextCard(
                    episode = next,
                    secondsLeft = if (autoplay) ((remaining + 999) / 1000).toInt() else null,
                    onPlayNow = { controller?.seekToNextMediaItem() },
                    onDismiss = { upNextDismissedFor = currentId },
                    modifier = bottomEnd,
                )
            }

            if (errorMessage != null) {
                ErrorCard(
                    message = errorMessage,
                    canSkip = next != null,
                    onSkip = {
                        onDismissError()
                        controller?.run {
                            seekToNextMediaItem()
                            prepare()
                            play()
                        }
                    },
                    onDismiss = onDismissError,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    if (stillWatching && !inPip) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Are you still watching?") },
            text = { Text(current?.let { (t, e) -> if (t.type == TitleType.SHOW) "${t.name} · ${e.label}" else t.name } ?: "") },
            confirmButton = {
                Button(onClick = {
                    PlaybackBridge.stillWatching.value = false
                    controller?.play()
                }) { Text("Continue watching") }
            },
            dismissButton = {
                TextButton(onClick = {
                    PlaybackBridge.stillWatching.value = false
                    onBack()
                }) { Text("Exit") }
            },
        )
    }

    if (showEpisodes && current != null) {
        EpisodesDialog(
            title = current.first,
            currentId = current.second.id,
            onPick = { ep ->
                showEpisodes = false
                val c = controller ?: return@EpisodesDialog
                val index = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == ep.id }
                if (index != null) {
                    c.seekTo(index, Library.resumePositionMs(ep.id))
                    c.play()
                } else {
                    val (eps, i) = Library.playlistFor(ep.id) ?: return@EpisodesDialog
                    c.setMediaItems(eps.map { com.localstream.app.playback.MediaItems.build(current.first, it) }, i, Library.resumePositionMs(ep.id))
                    c.prepare()
                    c.play()
                }
            },
            onDismiss = { showEpisodes = false },
        )
    }

    if (showSleep) {
        SleepTimerDialog(current = sleepTimer, onDismiss = { showSleep = false })
    }
}

private const val INTRO_WINDOW_MS = 5 * 60_000L

@Composable
private fun TopBar(
    title: Title?,
    episode: Episode?,
    sleepActive: Boolean,
    zoomed: Boolean,
    onBack: () -> Unit,
    onEpisodes: () -> Unit,
    onSleep: () -> Unit,
    onToggleZoom: () -> Unit,
    onPip: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            if (title != null && episode != null) {
                Text(
                    title.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (title.type == TitleType.SHOW) {
                    Text(
                        "${episode.label}  ${episode.title}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        TextButton(onClick = onToggleZoom) {
            Text(if (zoomed) "Fit" else "Fill", color = Color.White)
        }
        if (title?.type == TitleType.SHOW) {
            IconButton(onClick = onEpisodes) {
                Icon(painterResource(R.drawable.ic_episodes), contentDescription = "Episodes", tint = Color.White)
            }
        }
        IconButton(onClick = onSleep) {
            Icon(
                painterResource(R.drawable.ic_timer),
                contentDescription = "Sleep timer",
                tint = if (sleepActive) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        IconButton(onClick = onPip) {
            Icon(painterResource(R.drawable.ic_pip), contentDescription = "Picture-in-picture", tint = Color.White)
        }
    }
}

@Composable
private fun UpNextCard(
    episode: Episode,
    secondsLeft: Int?,
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(340.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xEE1C1C22),
        contentColor = Color.White,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(
                uri = episode.uri,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    if (secondsLeft != null) "Next episode in $secondsLeft" else "Up next",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    "${episode.label}  ${episode.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = onPlayNow, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play now")
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Top)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    canSkip: Boolean,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(32.dp)
            .width(420.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xEE1C1C22),
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("OK") }
                if (canSkip) Button(onClick = onSkip) { Text("Skip to next episode") }
            }
        }
    }
}

@Composable
private fun EpisodesDialog(
    title: Title,
    currentId: String,
    onPick: (Episode) -> Unit,
    onDismiss: () -> Unit,
) {
    val progress by Library.progress.collectAsState()
    val startIndex = title.episodes.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 1).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    title.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 420.dp)) {
                    items(title.episodes, key = { it.id }) { ep ->
                        val p = progress[ep.id]
                        val isCurrent = ep.id == currentId
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(ep) }
                                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                                .padding(vertical = 10.dp, horizontal = 20.dp),
                        ) {
                            Text(
                                "${ep.label}  ${ep.title}",
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (p != null && p.inProgress) {
                                ProgressBar(p.fraction, Modifier.padding(top = 4.dp).fillMaxWidth())
                            } else if (p?.completed == true) {
                                Text("Watched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(current: SleepTimer?, onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }
    val options = listOf<Pair<String, SleepTimer?>>(
        "Off" to null,
        "15 minutes" to SleepTimer.After(15, 0),
        "30 minutes" to SleepTimer.After(30, 0),
        "45 minutes" to SleepTimer.After(45, 0),
        "1 hour" to SleepTimer.After(60, 0),
        "End of episode" to SleepTimer.EndOfEpisode,
    )
    fun matches(option: SleepTimer?): Boolean = when (option) {
        null -> current == null
        is SleepTimer.After -> current is SleepTimer.After && current.minutes == option.minutes
        SleepTimer.EndOfEpisode -> current == SleepTimer.EndOfEpisode
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                if (current is SleepTimer.After) {
                    Text(
                        "Pausing in ${formatDuration(current.endsAtElapsed - now)}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                options.forEach { (label, option) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                PlaybackBridge.sleepTimer.value = when (option) {
                                    is SleepTimer.After -> option.copy(
                                        endsAtElapsed = SystemClock.elapsedRealtime() + option.minutes * 60_000L,
                                    )
                                    else -> option
                                }
                                onDismiss()
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = matches(option), onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
