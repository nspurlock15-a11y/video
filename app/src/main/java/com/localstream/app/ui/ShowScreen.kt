package com.localstream.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localstream.app.R
import com.localstream.app.data.Episode
import com.localstream.app.data.Library
import com.localstream.app.data.Progress
import com.localstream.app.data.TitleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowScreen(
    titleId: String,
    onBack: () -> Unit,
    onPlay: (Episode, Boolean) -> Unit,
) {
    val titles by Library.titles.collectAsState()
    val progress by Library.progress.collectAsState()
    val title = remember(titles, titleId) { titles.firstOrNull { it.id == titleId } }

    if (title == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val nextUp = remember(title, progress) { Library.nextUp(title, progress) }
    val seasons = title.seasons
    var selectedSeason by rememberSaveable(titleId) { mutableStateOf(nextUp?.season ?: seasons.firstOrNull() ?: 1) }
    val season = if (selectedSeason in seasons || seasons.isEmpty()) selectedSeason else seasons.first()
    val episodes = remember(title, season) { title.episodes.filter { it.season == season } }
    val isShow = title.type == TitleType.SHOW
    var seasonMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isShow) {
                        Box {
                            IconButton(onClick = { seasonMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Mark ${seasonName(season)} as watched") },
                                    onClick = {
                                        Library.setWatched(episodes.map { it.id }, true)
                                        seasonMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Mark ${seasonName(season)} as unwatched") },
                                    onClick = {
                                        Library.setWatched(episodes.map { it.id }, false)
                                        seasonMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset progress for whole show") },
                                    onClick = {
                                        Library.setWatched(title.episodes.map { it.id }, false)
                                        seasonMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(16f / 9f),
                ) {
                    TitleArt(title, Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
                    )
                }
                Column(Modifier.padding(16.dp)) {
                    Text(title.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (isShow) {
                        val regular = seasons.count { it != 0 }
                        val watched = title.episodes.count { progress[it.id]?.completed == true }
                        Text(
                            "${if (regular == 1) "1 season" else "$regular seasons"} · ${title.episodes.size} episodes · $watched watched",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val target = nextUp ?: title.episodes.firstOrNull()
                    if (target != null) {
                        val p = progress[target.id]
                        val resuming = p?.inProgress == true
                        Button(onClick = { onPlay(target, false) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    nextUp == null -> if (isShow) "Watch again from ${target.label}" else "Watch again"
                                    resuming && isShow -> "Resume ${target.label}"
                                    resuming -> "Resume"
                                    isShow -> "Play ${target.label}"
                                    else -> "Play"
                                },
                            )
                        }
                        if (resuming && p != null) {
                            Spacer(Modifier.height(6.dp))
                            ProgressBar(p.fraction, Modifier.fillMaxWidth())
                            Text(
                                "${formatDuration(p.positionMs)} of ${formatDuration(p.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { onPlay(target, true) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Start from beginning")
                            }
                        }
                    }
                }
            }

            if (isShow) {
                if (seasons.size > 1) {
                    item(key = "seasons") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(seasons) { s ->
                                FilterChip(
                                    selected = s == season,
                                    onClick = { selectedSeason = s },
                                    label = { Text(seasonName(s)) },
                                )
                            }
                        }
                    }
                }
                items(episodes, key = { it.id }) { ep ->
                    EpisodeRow(
                        episode = ep,
                        progress = progress[ep.id],
                        isNextUp = ep.id == nextUp?.id,
                        onPlay = { onPlay(ep, false) },
                        onStartOver = { onPlay(ep, true) },
                    )
                }
            } else {
                item(key = "file") {
                    val ep = title.episodes.first()
                    Text(
                        ep.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        val watched = progress[ep.id]?.completed == true
                        androidx.compose.material3.TextButton(onClick = { Library.setWatched(listOf(ep.id), !watched) }) {
                            Text(if (watched) "Mark as unwatched" else "Mark as watched")
                        }
                    }
                }
            }
        }
    }
}

private fun seasonName(season: Int) = if (season == 0) "Specials" else "Season $season"

@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: Progress?,
    isNextUp: Boolean,
    onPlay: () -> Unit,
    onStartOver: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .background(if (isNextUp) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f),
        ) {
            VideoThumbnail(episode.uri, Modifier.fillMaxSize())
            Icon(
                painterResource(R.drawable.ic_play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
                    .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape)
                    .padding(4.dp),
            )
            if (progress != null && progress.inProgress) {
                ProgressBar(
                    progress.fraction,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(4.dp),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                "${episode.number}. ${episode.title}",
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val info = buildList {
                add(episode.label)
                progress?.durationMs?.takeIf { it > 0 }?.let { add(formatRuntime(it)) }
                if (progress != null && progress.inProgress && progress.durationMs > 0) {
                    add("${formatRuntime(progress.durationMs - progress.positionMs)} left")
                }
                if (isNextUp) add("Up next")
            }.joinToString(" · ")
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progress?.completed == true) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Watched",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Episode options")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (progress?.completed == true) {
                    DropdownMenuItem(text = { Text("Mark as unwatched") }, onClick = {
                        Library.setWatched(listOf(episode.id), false)
                        menu = false
                    })
                } else {
                    DropdownMenuItem(text = { Text("Mark as watched") }, onClick = {
                        Library.setWatched(listOf(episode.id), true)
                        menu = false
                    })
                }
                DropdownMenuItem(text = { Text("Play from beginning") }, onClick = {
                    menu = false
                    onStartOver()
                })
            }
        }
    }
}
