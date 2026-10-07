package com.localstream.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.media3.common.MimeTypes

/**
 * Walks a folder the user granted access to (Storage Access Framework tree) and
 * works out which sub-folders are TV shows (with seasons) and which videos are movies.
 *
 * Supported layouts include:
 *  - Show/Season 1/Show.S01E01.mkv
 *  - Show/S01E01 - Pilot.mp4 (flat)
 *  - Show/Season 2/03 - Title.mkv (number from the file, season from the folder)
 *  - TV/<many shows>/... and Movies/<movie>.mkv when a parent folder is picked
 */
class LibraryScanner(private val context: Context) {

    private class FileEntry(val docId: String, val name: String, val mime: String)

    private class Node(val docId: String, val name: String) {
        val files = ArrayList<FileEntry>()
        val dirs = ArrayList<Node>()
    }

    fun scan(treeUri: Uri): List<Title> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = Node(rootId, displayName(treeUri, rootId) ?: "Library")
        fill(treeUri, root, 0)
        return classify(treeUri, root, null)
    }

    private fun displayName(tree: Uri, docId: String): String? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        return context.contentResolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun fill(tree: Uri, node: Node, depth: Int) {
        if (depth > MAX_DEPTH) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, node.docId)
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = try {
            context.contentResolver.query(childrenUri, cols, null, null, null)
        } catch (e: Exception) {
            null
        } ?: return
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                if (name.startsWith(".")) continue
                if (mime == Document.MIME_TYPE_DIR) {
                    node.dirs += Node(id, name)
                } else {
                    node.files += FileEntry(id, name, mime)
                }
            }
        }
        node.dirs.sortWith { a, b -> NameParser.NATURAL_ORDER.compare(a.name, b.name) }
        node.files.sortWith { a, b -> NameParser.NATURAL_ORDER.compare(a.name, b.name) }
        node.dirs.forEach { fill(tree, it, depth + 1) }
    }

    private fun hasVideos(node: Node): Boolean =
        node.files.any { isVideo(it) } || node.dirs.any { hasVideos(it) }

    private fun isShow(node: Node): Boolean {
        if (node.dirs.any { NameParser.seasonFromFolder(it.name) != null && hasVideos(it) }) return true
        if (NameParser.seasonFromFolder(node.name) != null && node.files.any { isVideo(it) }) return true
        return node.files.any { isVideo(it) && NameParser.parseEpisode(it.name).strong }
    }

    private fun classify(tree: Uri, node: Node, parentName: String?): List<Title> {
        if (!hasVideos(node)) return emptyList()
        if (isShow(node)) return listOf(buildShow(tree, node, parentName))
        val out = ArrayList<Title>()
        node.dirs.forEach { out += classify(tree, it, node.name) }
        val videos = node.files.filter { isVideo(it) }
        videos.forEach { out += buildMovie(tree, node, it, singleInFolder = videos.size == 1 && parentName != null) }
        return out
    }

    private fun buildShow(tree: Uri, node: Node, parentName: String?): Title {
        class Candidate(val file: FileEntry, val folder: Node, val folderSeason: Int?)

        val candidates = ArrayList<Candidate>()
        fun walk(n: Node, season: Int?) {
            n.files.filter { isVideo(it) }.forEach { candidates += Candidate(it, n, season) }
            n.dirs.forEach { d -> walk(d, NameParser.seasonFromFolder(d.name) ?: season) }
        }
        walk(node, NameParser.seasonFromFolder(node.name))

        class Draft(val c: Candidate, val season: Int, val number: Int?, val title: String?)

        val drafts = candidates.map { c ->
            val p = NameParser.parseEpisode(c.file.name)
            Draft(c, p.season ?: c.folderSeason ?: 1, p.episode, p.title)
        }

        val episodes = ArrayList<Episode>()
        drafts.groupBy { it.season }.forEach { (season, list) ->
            val sorted = list.sortedWith(
                compareBy<Draft> { it.number ?: Int.MAX_VALUE }
                    .thenComparator { a, b -> NameParser.NATURAL_ORDER.compare(a.c.file.name, b.c.file.name) },
            )
            var last = 0
            for (d in sorted) {
                val number = d.number ?: (last + 1)
                last = maxOf(last, number)
                episodes += Episode(
                    id = d.c.file.docId,
                    uri = docUri(tree, d.c.file.docId),
                    fileName = d.c.file.name,
                    title = d.title ?: "Episode $number",
                    season = season,
                    number = number,
                    subtitles = subtitlesFor(tree, d.c.folder, d.c.file),
                )
            }
        }
        episodes.sortWith(compareBy<Episode, Int>(Title.SEASON_ORDER) { it.season }.thenBy { it.number })

        val name = if (NameParser.seasonFromFolder(node.name) != null) {
            drafts.firstNotNullOfOrNull { NameParser.parseEpisode(it.c.file.name).showName }
                ?: parentName?.let { NameParser.clean(it) }
                ?: node.name
        } else {
            NameParser.clean(node.name).ifBlank { node.name }
        }

        return Title(
            id = "show:${node.docId}",
            name = name,
            type = TitleType.SHOW,
            rootUri = tree.toString(),
            posterUri = posterIn(tree, node),
            episodes = episodes,
        )
    }

    private fun buildMovie(tree: Uri, folder: Node, file: FileEntry, singleInFolder: Boolean): Title {
        val fromFile = NameParser.clean(file.name.substringBeforeLast('.'))
        val name = if (singleInFolder) NameParser.clean(folder.name).ifBlank { fromFile } else fromFile
        val episode = Episode(
            id = file.docId,
            uri = docUri(tree, file.docId),
            fileName = file.name,
            title = name.ifBlank { file.name },
            season = 1,
            number = 1,
            subtitles = subtitlesFor(tree, folder, file),
        )
        return Title(
            id = "movie:${file.docId}",
            name = episode.title,
            type = TitleType.MOVIE,
            rootUri = tree.toString(),
            posterUri = if (singleInFolder) posterIn(tree, folder) else null,
            episodes = listOf(episode),
        )
    }

    private fun posterIn(tree: Uri, node: Node): String? {
        val poster = node.files.firstOrNull { f ->
            val ext = f.name.substringAfterLast('.', "").lowercase()
            val base = f.name.substringBeforeLast('.').lowercase()
            ext in IMAGE_EXT && base in POSTER_NAMES
        }
        return poster?.let { docUri(tree, it.docId) }
    }

    private fun subtitlesFor(tree: Uri, folder: Node, video: FileEntry): List<SubtitleFile> {
        val base = video.name.substringBeforeLast('.')
        return folder.files.mapNotNull { f ->
            val ext = f.name.substringAfterLast('.', "").lowercase()
            val mime = SUBTITLE_MIME[ext] ?: return@mapNotNull null
            val subBase = f.name.substringBeforeLast('.')
            if (!subBase.startsWith(base)) return@mapNotNull null
            val tag = subBase.removePrefix(base).trim('.', ' ', '_', '-')
            val lang = tag.takeIf { it.length in 2..3 && it.all { c -> c.isLetter() } }?.lowercase()
            SubtitleFile(
                uri = docUri(tree, f.docId),
                mimeType = mime,
                label = tag.ifBlank { "External (${ext.uppercase()})" },
                language = lang,
            )
        }
    }

    private fun docUri(tree: Uri, docId: String): String =
        DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString()

    private fun isVideo(f: FileEntry): Boolean {
        val ext = f.name.substringAfterLast('.', "").lowercase()
        if (ext in SUBTITLE_MIME || ext in IMAGE_EXT) return false
        return f.mime.startsWith("video/") || ext in VIDEO_EXT
    }

    companion object {
        private const val MAX_DEPTH = 8
        private val VIDEO_EXT = setOf(
            "mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mts", "wmv", "flv", "3gp", "mpg", "mpeg", "ogv",
        )
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
        private val POSTER_NAMES = setOf("poster", "folder", "cover", "show", "movie")
        private val SUBTITLE_MIME = mapOf(
            "srt" to MimeTypes.APPLICATION_SUBRIP,
            "vtt" to MimeTypes.TEXT_VTT,
            "ass" to MimeTypes.TEXT_SSA,
            "ssa" to MimeTypes.TEXT_SSA,
            "ttml" to MimeTypes.APPLICATION_TTML,
        )
    }
}
