package com.localstream.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The app's single source of truth: which folders were added, what shows / movies
 * were found in them, and how far each episode has been watched.
 *
 * Everything is stored as small JSON files in the app's private storage. Watch
 * progress is keyed by the file's document id, so it survives library rescans.
 */
object Library {
    private const val TAG = "Library"

    private lateinit var appContext: Context
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Background task failed", e) },
    )
    private val scanMutex = Mutex()
    private val fileMutex = Mutex()
    private var progressWrite: Job? = null

    private val _roots = MutableStateFlow<List<String>>(emptyList())
    private val _titles = MutableStateFlow<List<Title>>(emptyList())
    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    private val _scanning = MutableStateFlow(false)

    /** Folder tree URIs the user granted access to. */
    val roots: StateFlow<List<String>> get() = _roots
    val titles: StateFlow<List<Title>> get() = _titles
    val progress: StateFlow<Map<String, Progress>> get() = _progress
    val scanning: StateFlow<Boolean> get() = _scanning

    @Volatile
    private var index: Map<String, Pair<Title, Episode>> = emptyMap()

    private val libraryFile get() = File(appContext.filesDir, "library.json")
    private val progressFile get() = File(appContext.filesDir, "progress.json")

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            if (libraryFile.exists()) {
                val json = JSONObject(libraryFile.readText())
                _roots.value = json.optJSONArray("roots").strings()
                setTitles(json.optJSONArray("titles").objects().map { titleFromJson(it) })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read library", e)
        }
        try {
            if (progressFile.exists()) {
                val json = JSONObject(progressFile.readText())
                _progress.value = json.keys().asSequence().associateWith { progressFromJson(json.getJSONObject(it)) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read progress", e)
        }
    }

    // ---------------------------------------------------------------- folders & scanning

    fun addRoot(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not persist permission for $uri", e)
        }
        val s = uri.toString()
        if (s !in _roots.value) _roots.value = _roots.value + s
        rescan()
    }

    fun removeRoot(uri: String) {
        _roots.value = _roots.value - uri
        try {
            appContext.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Log.w(TAG, "Could not release permission for $uri", e)
        }
        setTitles(_titles.value.filter { it.rootUri != uri })
        scope.launch { saveLibrary() }
    }

    /** Re-reads every added folder to pick up new, moved or deleted files. */
    fun rescan() {
        scope.launch {
            // Scans queue up rather than being skipped, so a folder added mid-scan is still picked up.
            scanMutex.withLock {
                _scanning.value = true
                try {
                    scanAll()
                } finally {
                    _scanning.value = false
                }
            }
        }
    }

    private suspend fun scanAll() {
        val scanner = LibraryScanner(appContext)
        val granted = appContext.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
        val found = LinkedHashMap<String, Title>()
        for (root in _roots.value) {
            val previous = _titles.value.filter { it.rootUri == root }
            if (root !in granted) {
                Log.w(TAG, "Lost access to $root, keeping previous entries")
                previous.forEach { found.putIfAbsent(it.id, it) }
                continue
            }
            try {
                scanner.scan(Uri.parse(root)).forEach { found.putIfAbsent(it.id, it) }
            } catch (e: Throwable) {
                Log.e(TAG, "Scan of $root failed", e)
                previous.forEach { found.putIfAbsent(it.id, it) }
            }
        }
        setTitles(found.values.sortedWith(compareBy<Title, String>(NameParser.NATURAL_ORDER) { it.name }))
        Log.i(TAG, "Scan finished: ${found.size} titles from ${_roots.value.size} folder(s)")
        saveLibrary()
    }

    private fun setTitles(list: List<Title>) {
        val idx = HashMap<String, Pair<Title, Episode>>()
        list.forEach { t -> t.episodes.forEach { e -> idx[e.id] = t to e } }
        index = idx
        _titles.value = list
    }

    // ---------------------------------------------------------------- lookups

    fun lookup(episodeId: String): Pair<Title, Episode>? = index[episodeId]

    fun title(id: String): Title? = _titles.value.firstOrNull { it.id == id }

    /**
     * The episodes that autoplay walks through, in order, and the index of [episodeId]
     * in it. Regular seasons chain into each other (last episode of season 1 → first of
     * season 2); specials only play among themselves.
     */
    fun playlistFor(episodeId: String): Pair<List<Episode>, Int>? {
        val (title, episode) = lookup(episodeId) ?: return null
        val list = playOrder(title, episode.season == 0)
        return list to list.indexOfFirst { it.id == episodeId }.coerceAtLeast(0)
    }

    private fun playOrder(title: Title, specials: Boolean): List<Episode> =
        title.episodes.filter { (it.season == 0) == specials }.ifEmpty { title.episodes }

    /** Where playback of [episodeId] should start: the saved spot if it was left part-way. */
    fun resumePositionMs(episodeId: String): Long {
        val p = _progress.value[episodeId] ?: return 0
        return if (p.inProgress) p.positionMs else 0
    }

    /**
     * The episode to offer for [title]: the one being watched if unfinished, otherwise the
     * one after the most recently finished episode. Null when the whole show is watched.
     */
    fun nextUp(title: Title, progress: Map<String, Progress> = _progress.value): Episode? {
        val regular = playOrder(title, specials = false)
        val latest = title.episodes
            .mapNotNull { e -> progress[e.id]?.let { e to it } }
            .maxByOrNull { it.second.updatedAt }
            ?: return regular.firstOrNull()
        val (episode, p) = latest
        if (p.inProgress || !p.completed) return episode
        val order = playOrder(title, episode.season == 0)
        return order.getOrNull(order.indexOfFirst { it.id == episode.id } + 1)
    }

    data class ContinueItem(val title: Title, val episode: Episode, val progress: Progress?, val updatedAt: Long)

    /** Titles with recent activity, most recent first, each with what to play next. */
    fun continueWatching(
        titles: List<Title> = _titles.value,
        progress: Map<String, Progress> = _progress.value,
    ): List<ContinueItem> = titles.mapNotNull { t ->
        val last = t.episodes.mapNotNull { progress[it.id]?.updatedAt }.maxOrNull() ?: return@mapNotNull null
        val next = nextUp(t, progress) ?: return@mapNotNull null
        ContinueItem(t, next, progress[next.id], last)
    }.sortedByDescending { it.updatedAt }

    /** The most recently played episode — what the app was showing when it was last closed. */
    fun lastWatched(): Episode? = _progress.value.entries
        .filter { index.containsKey(it.key) }
        .maxByOrNull { it.value.updatedAt }
        ?.let { index[it.key]?.second }

    // ---------------------------------------------------------------- progress

    fun saveProgress(episodeId: String, positionMs: Long, durationMs: Long, completed: Boolean = false) {
        if (episodeId.isEmpty()) return
        val now = System.currentTimeMillis()
        _progress.update { map ->
            val prev = map[episodeId]
            val duration = if (durationMs > 0) durationMs else prev?.durationMs ?: 0
            val finished = completed || (duration > 0 && positionMs >= duration * Progress.COMPLETE_FRACTION)
            val entry = Progress(
                positionMs = if (finished) 0 else positionMs.coerceAtLeast(0),
                durationMs = duration,
                completed = finished || prev?.completed == true,
                updatedAt = now,
            )
            map + (episodeId to entry)
        }
        scheduleProgressWrite()
    }

    fun setWatched(episodeIds: Collection<String>, watched: Boolean) {
        val now = System.currentTimeMillis()
        _progress.update { map ->
            val m = map.toMutableMap()
            episodeIds.forEachIndexed { i, id ->
                if (watched) {
                    m[id] = Progress(0, map[id]?.durationMs ?: 0, completed = true, updatedAt = now + i)
                } else {
                    m.remove(id)
                }
            }
            m
        }
        scheduleProgressWrite()
    }

    private fun scheduleProgressWrite() {
        progressWrite?.cancel()
        progressWrite = scope.launch {
            delay(1_000)
            writeProgress()
        }
    }

    /** Writes pending progress immediately (used when the playback service shuts down). */
    fun flush() {
        progressWrite?.cancel()
        scope.launch { writeProgress() }
    }

    private suspend fun writeProgress() = fileMutex.withLock {
        val json = JSONObject()
        _progress.value.forEach { (id, p) -> json.put(id, progressToJson(p)) }
        writeAtomically(progressFile, json.toString())
    }

    private suspend fun saveLibrary() = fileMutex.withLock {
        val json = JSONObject()
        json.put("roots", JSONArray(_roots.value))
        json.put("titles", JSONArray().apply { _titles.value.forEach { put(titleToJson(it)) } })
        writeAtomically(libraryFile, json.toString())
    }

    private fun writeAtomically(target: File, text: String) {
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write ${target.name}", e)
        }
    }

    // ---------------------------------------------------------------- JSON

    private fun titleToJson(t: Title) = JSONObject().apply {
        put("id", t.id)
        put("name", t.name)
        put("type", t.type.name)
        put("root", t.rootUri)
        put("poster", t.posterUri ?: JSONObject.NULL)
        put("episodes", JSONArray().apply { t.episodes.forEach { put(episodeToJson(it)) } })
    }

    private fun titleFromJson(o: JSONObject) = Title(
        id = o.getString("id"),
        name = o.getString("name"),
        type = runCatching { TitleType.valueOf(o.getString("type")) }.getOrDefault(TitleType.SHOW),
        rootUri = o.getString("root"),
        posterUri = o.optString("poster").takeIf { !o.isNull("poster") && it.isNotEmpty() },
        episodes = o.optJSONArray("episodes").objects().map { episodeFromJson(it) },
    )

    private fun episodeToJson(e: Episode) = JSONObject().apply {
        put("id", e.id)
        put("uri", e.uri)
        put("file", e.fileName)
        put("title", e.title)
        put("season", e.season)
        put("number", e.number)
        put("subs", JSONArray().apply {
            e.subtitles.forEach { s ->
                put(JSONObject().apply {
                    put("uri", s.uri)
                    put("mime", s.mimeType)
                    put("label", s.label)
                    put("lang", s.language ?: JSONObject.NULL)
                })
            }
        })
    }

    private fun episodeFromJson(o: JSONObject) = Episode(
        id = o.getString("id"),
        uri = o.getString("uri"),
        fileName = o.optString("file"),
        title = o.optString("title"),
        season = o.optInt("season", 1),
        number = o.optInt("number", 1),
        subtitles = o.optJSONArray("subs").objects().map { s ->
            SubtitleFile(
                uri = s.getString("uri"),
                mimeType = s.getString("mime"),
                label = s.optString("label"),
                language = s.optString("lang").takeIf { !s.isNull("lang") && it.isNotEmpty() },
            )
        },
    )

    private fun progressToJson(p: Progress) = JSONObject().apply {
        put("pos", p.positionMs)
        put("dur", p.durationMs)
        put("done", p.completed)
        put("at", p.updatedAt)
    }

    private fun progressFromJson(o: JSONObject) = Progress(
        positionMs = o.optLong("pos"),
        durationMs = o.optLong("dur"),
        completed = o.optBoolean("done"),
        updatedAt = o.optLong("at"),
    )

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
