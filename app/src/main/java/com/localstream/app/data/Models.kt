package com.localstream.app.data

enum class TitleType { SHOW, MOVIE }

/** A sidecar subtitle file found next to a video (e.g. `Episode.en.srt`). */
data class SubtitleFile(
    val uri: String,
    val mimeType: String,
    val label: String,
    val language: String?,
)

data class Episode(
    /** Stable document id of the file; also used as the media id and progress key. */
    val id: String,
    val uri: String,
    val fileName: String,
    val title: String,
    val season: Int,
    val number: Int,
    val subtitles: List<SubtitleFile>,
) {
    val label: String get() = "S$season:E$number"
}

/** A TV show (many episodes) or a movie (exactly one "episode"). */
data class Title(
    val id: String,
    val name: String,
    val type: TitleType,
    val rootUri: String,
    val posterUri: String?,
    /** Sorted by season (specials last), then episode number. */
    val episodes: List<Episode>,
) {
    val seasons: List<Int>
        get() = episodes.map { it.season }.distinct().sortedWith(SEASON_ORDER)

    companion object {
        /** Season 0 ("Specials") sorts after all regular seasons. */
        val SEASON_ORDER = Comparator<Int> { a, b ->
            when {
                a == b -> 0
                a == 0 -> 1
                b == 0 -> -1
                else -> a.compareTo(b)
            }
        }
    }
}

data class Progress(
    val positionMs: Long,
    val durationMs: Long,
    /** True once the episode has been watched to the end (or marked watched). */
    val completed: Boolean,
    val updatedAt: Long,
) {
    /** Partially watched: worth resuming from [positionMs]. */
    val inProgress: Boolean
        get() = positionMs > RESUME_THRESHOLD_MS &&
            (durationMs <= 0 || positionMs < durationMs * COMPLETE_FRACTION)

    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        const val RESUME_THRESHOLD_MS = 10_000L
        /** Past this point (end credits) an episode counts as watched. */
        const val COMPLETE_FRACTION = 0.93
    }
}
