package com.localstream.app.data

/** Pulls season / episode numbers and readable names out of file and folder names. */
object NameParser {

    data class Parsed(
        val season: Int?,
        val episode: Int?,
        /** True when the name carries an explicit episode marker (S01E02, 1x02, "Episode 2"). */
        val strong: Boolean,
        /** Text before the episode marker, e.g. "Breaking Bad" in "Breaking.Bad.S01E01.mkv". */
        val showName: String?,
        /** Text after the episode marker, cleaned up. */
        val title: String?,
    )

    // S01E02, s1.e2, S01 E02
    private val SXE = Regex("""(?<![a-z0-9])s(\d{1,3})[ ._-]*e(\d{1,4})""", RegexOption.IGNORE_CASE)
    // 1x02 (but not 1920x1080)
    private val NXN = Regex("""(?<![0-9])(\d{1,2})x(\d{2,3})(?![0-9])""", RegexOption.IGNORE_CASE)
    // "Episode 2", "Ep.02", "E02"
    private val EP_ONLY = Regex("""(?<![a-z0-9])(?:episode|ep|e)[ ._-]*(\d{1,4})(?![0-9])""", RegexOption.IGNORE_CASE)
    // "02 - Title"
    private val LEADING = Regex("""^(\d{1,3})(?=[ ._\-)\]]|$)""")
    // Extra episode markers in multi-episode files: S01E01E02 / S01E01-E02
    private val EXTRA_EPISODES = Regex("""^(?:[ ._-]*-?e\d{1,4})+""", RegexOption.IGNORE_CASE)

    private val SEASON_DIR = Regex(
        """^(?:season|series|staffel|saison|temporada|stagione|s)[ ._-]*(\d{1,3})(?![0-9])""",
        RegexOption.IGNORE_CASE,
    )
    private val SEASON_ANYWHERE = Regex("""(?<![a-z])season[ ._-]*(\d{1,3})(?![0-9])""", RegexOption.IGNORE_CASE)
    private val SPECIALS_DIR = Regex("""^(?:specials?|season[ ._-]*0+)$""", RegexOption.IGNORE_CASE)

    private val JUNK = Regex(
        """(?i)(?<![a-z0-9])(480p|576p|720p|1080p|2160p|4k|uhd|hdr10?|dv|x264|x265|h ?26[45]|hevc|avc|web[ -]?dl|webrip|bluray|blu ?ray|brrip|bdrip|dvdrip|hdtv|hdrip|amzn|dsnp|hmax|atvp|aac(?:2 0|5 1)?|ac3|eac3|dts|ddp?(?:2|5) ?[01]|10bit|proper|repack|internal|remux)(?![a-z0-9])""",
    )
    private val BRACKETS = Regex("""\[[^\]]*]|\{[^}]*}""")

    fun parseEpisode(fileName: String): Parsed {
        val base = fileName.substringBeforeLast('.', fileName)

        SXE.find(base)?.let { m ->
            val rest = base.substring(m.range.last + 1).replaceFirst(EXTRA_EPISODES, "")
            return Parsed(
                season = m.groupValues[1].toInt(),
                episode = m.groupValues[2].toInt(),
                strong = true,
                showName = clean(base.substring(0, m.range.first)).ifBlank { null },
                title = clean(rest).ifBlank { null },
            )
        }
        NXN.find(base)?.let { m ->
            return Parsed(
                season = m.groupValues[1].toInt(),
                episode = m.groupValues[2].toInt(),
                strong = true,
                showName = clean(base.substring(0, m.range.first)).ifBlank { null },
                title = clean(base.substring(m.range.last + 1)).ifBlank { null },
            )
        }
        EP_ONLY.find(base)?.let { m ->
            return Parsed(
                season = null,
                episode = m.groupValues[1].toInt(),
                strong = true,
                showName = clean(base.substring(0, m.range.first)).ifBlank { null },
                title = clean(base.substring(m.range.last + 1)).ifBlank { null },
            )
        }
        LEADING.find(base)?.let { m ->
            return Parsed(
                season = null,
                episode = m.groupValues[1].toInt(),
                strong = false,
                showName = null,
                title = clean(base.substring(m.range.last + 1)).ifBlank { null },
            )
        }
        return Parsed(null, null, false, null, clean(base).ifBlank { null })
    }

    /** Season number for folders such as "Season 2", "S02", "Series 2" or "Specials" (= 0). */
    fun seasonFromFolder(name: String): Int? {
        val n = name.trim()
        if (SPECIALS_DIR.matches(n)) return 0
        SEASON_DIR.find(n)?.let { return it.groupValues[1].toInt() }
        SEASON_ANYWHERE.find(n)?.let { return it.groupValues[1].toInt() }
        return null
    }

    /** Turns "Some.Show.2008.1080p.WEB-DL" into "Some Show 2008". */
    fun clean(raw: String): String {
        var s = raw.replace(BRACKETS, " ").replace('_', ' ').replace('.', ' ')
        JUNK.find(s)?.let { s = s.substring(0, it.range.first) }
        s = s.replace(Regex("""\s+"""), " ")
        return s.trim { it.isWhitespace() || it == '-' || it == '(' || it == '–' || it == ',' }
            .let { if (it.count { c -> c == '(' } > it.count { c -> c == ')' }) it.substringBeforeLast('(').trim() else it }
    }

    /** Orders "Episode 2" before "Episode 10". */
    val NATURAL_ORDER = Comparator<String> { a, b ->
        val ca = chunks(a.lowercase())
        val cb = chunks(b.lowercase())
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val xn = x[0].isDigit()
            val yn = y[0].isDigit()
            val c = if (xn && yn) {
                val xi = x.trimStart('0')
                val yi = y.trimStart('0')
                if (xi.length != yi.length) xi.length - yi.length else xi.compareTo(yi)
            } else {
                x.compareTo(y)
            }
            if (c != 0) return@Comparator c
        }
        ca.size - cb.size
    }

    private fun chunks(s: String): List<String> {
        if (s.isEmpty()) return listOf(" ")
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var digit = s[0].isDigit()
        for (ch in s) {
            if (ch.isDigit() != digit) {
                out += sb.toString()
                sb.clear()
                digit = ch.isDigit()
            }
            sb.append(ch)
        }
        out += sb.toString()
        return out
    }
}
