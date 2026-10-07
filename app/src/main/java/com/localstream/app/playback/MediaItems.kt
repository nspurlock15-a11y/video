package com.localstream.app.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.localstream.app.data.Episode
import com.localstream.app.data.Library
import com.localstream.app.data.Title
import com.localstream.app.data.TitleType

object MediaItems {

    fun build(title: Title, episode: Episode): MediaItem {
        val subs = episode.subtitles.mapIndexed { i, s ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.uri))
                .setMimeType(s.mimeType)
                .setLanguage(s.language)
                .setLabel(s.label)
                .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val isShow = title.type == TitleType.SHOW
        val displayTitle = if (isShow) "${episode.label} · ${episode.title}" else episode.title
        val metadata = MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setDisplayTitle(displayTitle)
            .setArtist(if (isShow) title.name else null)
            .setAlbumTitle(title.name)
            .setSubtitle(if (isShow) title.name else null)
            .setArtworkUri(title.posterUri?.let { Uri.parse(it) })
            .setMediaType(if (isShow) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MOVIE)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(episode.uri)
            .setSubtitleConfigurations(subs)
            .setMediaMetadata(metadata)
            .build()
    }

    /** Rebuilds a full, playable item from just its media id. */
    fun resolve(item: MediaItem): MediaItem =
        Library.lookup(item.mediaId)?.let { (t, e) -> build(t, e) } ?: item
}
