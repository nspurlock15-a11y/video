package com.localstream.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.localstream.app.data.Thumbnails
import com.localstream.app.data.Title
import java.io.File

/** A frame grabbed from the video, or a dark placeholder while it loads. */
@Composable
fun VideoThumbnail(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file by produceState<File?>(null, uri) { value = Thumbnails.get(context, uri) }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF2A2A33), Color(0xFF17171C))),
            ),
    ) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The title's poster image if the folder has one, else a frame from its first episode. */
@Composable
fun TitleArt(title: Title, modifier: Modifier = Modifier) {
    val poster = title.posterUri
    if (poster != null) {
        AsyncImage(
            model = poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF22222A)),
        )
    } else {
        val first = title.episodes.firstOrNull { it.season != 0 } ?: title.episodes.firstOrNull()
        if (first != null) VideoThumbnail(first.uri, modifier) else Box(modifier.background(Color(0xFF22222A)))
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** 3725000 → "1:02:05", 65000 → "1:05". */
fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 2700000 → "45m", 5400000 → "1h 30m". */
fun formatRuntime(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}
