package com.localstream.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localstream.app.R
import com.localstream.app.data.Episode
import com.localstream.app.data.Library
import com.localstream.app.data.Title
import com.localstream.app.data.TitleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTitle: (Title) -> Unit,
    onPlay: (Episode) -> Unit,
    onAddFolder: () -> Unit,
    onSettings: () -> Unit,
) {
    val titles by Library.titles.collectAsState()
    val progress by Library.progress.collectAsState()
    val scanning by Library.scanning.collectAsState()
    val continueItems = remember(titles, progress) { Library.continueWatching(titles, progress) }
    val shows = remember(titles) { titles.filter { it.type == TitleType.SHOW } }
    val movies = remember(titles) { titles.filter { it.type == TitleType.MOVIE } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LocalStream", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                actions = {
                    if (scanning) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = { Library.rescan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan library")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (titles.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddFolder,
                    icon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                    text = { Text("Add folder") },
                )
            }
        },
    ) { padding ->
        if (titles.isEmpty()) {
            EmptyLibrary(scanning, onAddFolder, Modifier.padding(padding))
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            continueItems.firstOrNull()?.let { hero ->
                fullWidth("hero") { HeroCard(hero, onPlay = { onPlay(hero.episode) }, onOpen = { onOpenTitle(hero.title) }) }
            }
            if (continueItems.size > 1) {
                header("continue", "Continue watching")
                fullWidth("continue_row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueItems.drop(1), key = { it.title.id }) { item ->
                            ContinueCard(item, onPlay = { onPlay(item.episode) }, onOpen = { onOpenTitle(item.title) })
                        }
                    }
                }
            }
            if (shows.isNotEmpty()) {
                header("shows", "TV shows")
                items(shows, key = { it.id }) { show ->
                    TitleCard(show, progress = progress, onClick = { onOpenTitle(show) })
                }
            }
            if (movies.isNotEmpty()) {
                header("movies", "Movies")
                items(movies, key = { it.id }) { movie ->
                    TitleCard(
                        movie,
                        progress = progress,
                        onClick = { onPlay(movie.episodes.first()) },
                        onLongClick = { onOpenTitle(movie) },
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.header(key: String, text: String) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun EmptyLibrary(scanning: Boolean, onAddFolder: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (scanning) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Scanning your folders…")
            return@Column
        }
        Icon(
            painterResource(R.drawable.ic_folder),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Your library is empty", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose the folder on your phone that holds your shows (for example \"Movies\" or \"TV\"). " +
                "Folders like Show Name / Season 1 / S01E01.mkv are sorted into seasons and episodes automatically.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddFolder) {
            Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add a folder")
        }
    }
}

@Composable
private fun HeroCard(item: Library.ContinueItem, onPlay: () -> Unit, onOpen: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clickable(onClick = onOpen),
    ) {
        VideoThumbnail(item.episode.uri, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))),
                    RoundedCornerShape(8.dp),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text("Continue watching", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            Text(item.title.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            if (item.title.type == TitleType.SHOW) {
                Text(
                    "${item.episode.label}  ${item.episode.title}",
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPlay) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (item.progress?.inProgress == true) "Resume" else "Play")
                }
                val p = item.progress
                if (p != null && p.inProgress && p.durationMs > 0) {
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.width(120.dp)) {
                        ProgressBar(p.fraction, Modifier.fillMaxWidth())
                        Text(
                            "${formatRuntime(p.durationMs - p.positionMs)} left",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(item: Library.ContinueItem, onPlay: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.width(200.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable(onClick = onPlay),
        ) {
            VideoThumbnail(item.episode.uri, Modifier.fillMaxSize())
            Icon(
                painterResource(R.drawable.ic_play),
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(6.dp),
            )
            item.progress?.takeIf { it.inProgress }?.let {
                ProgressBar(
                    it.fraction,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
        Column(Modifier.clickable(onClick = onOpen).padding(top = 6.dp)) {
            Text(item.title.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            if (item.title.type == TitleType.SHOW) {
                Text(
                    "${item.episode.label}  ${item.episode.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleCard(
    title: Title,
    progress: Map<String, com.localstream.app.data.Progress>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            TitleArt(title, Modifier.fillMaxSize())
            if (title.type == TitleType.MOVIE) {
                val p = progress[title.episodes.first().id]
                if (p != null && p.inProgress) {
                    ProgressBar(
                        p.fraction,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(6.dp),
                    )
                }
            }
        }
        Text(
            title.name,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        val subtitle = if (title.type == TitleType.SHOW) {
            val seasons = title.seasons.count { it != 0 }
            val watched = title.episodes.count { progress[it.id]?.completed == true }
            buildString {
                append(if (seasons == 1) "1 season" else "$seasons seasons")
                append(" · ${title.episodes.size} ep")
                if (watched > 0) append(" · $watched watched")
            }
        } else {
            progress[title.episodes.first().id]?.durationMs?.takeIf { it > 0 }?.let { formatRuntime(it) } ?: "Movie"
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
