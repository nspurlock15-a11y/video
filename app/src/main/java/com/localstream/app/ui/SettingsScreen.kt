package com.localstream.app.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localstream.app.R
import com.localstream.app.data.Library
import com.localstream.app.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onAddFolder: () -> Unit) {
    val roots by Library.roots.collectAsState()
    val scanning by Library.scanning.collectAsState()
    var removing by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item { Section("Library folders") }
            if (roots.isEmpty()) {
                item {
                    Text(
                        "No folders added yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            roots.forEach { root ->
                item(key = root) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(folderLabel(root), modifier = Modifier.weight(1f))
                        IconButton(onClick = { removing = root }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove folder")
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = onAddFolder) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add folder")
                    }
                    TextButton(onClick = { Library.rescan() }, enabled = !scanning) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (scanning) "Scanning…" else "Rescan now")
                    }
                }
            }

            item { Section("Playback") }
            item {
                ToggleRow(
                    "Autoplay next episode",
                    "Plays the next episode when one ends, continuing into the next season.",
                    Settings.autoplay,
                )
            }
            item {
                ToggleRow(
                    "Background playback",
                    "Keep the sound playing when you leave the app or lock the screen. Controls appear on the lock screen and in notifications.",
                    Settings.backgroundPlay,
                )
            }
            item {
                ToggleRow(
                    "Automatic picture-in-picture",
                    "Shrink the video into a floating window when you go to the home screen while watching.",
                    Settings.autoPip,
                )
            }
            item {
                ChoiceRow(
                    "Are you still watching?",
                    Settings.stillWatchingAfter,
                    listOf(0 to "Never", 2 to "After 2 episodes", 3 to "After 3 episodes", 5 to "After 5 episodes"),
                )
            }
            item {
                ChoiceRow(
                    "\"Up next\" card",
                    Settings.upNextSec,
                    listOf(0 to "Off", 15 to "15 s before the end", 30 to "30 s before the end", 60 to "1 min before the end", 120 to "2 min before the end"),
                )
            }
            item {
                ChoiceRow(
                    "Skip intro button",
                    Settings.introSkipSec,
                    listOf(0 to "Off", 30 to "Skips 30 s", 60 to "Skips 60 s", 85 to "Skips 85 s", 90 to "Skips 90 s", 120 to "Skips 2 min"),
                )
            }
            item {
                ChoiceRow(
                    "Rewind / fast-forward step",
                    Settings.seekIncrementSec,
                    listOf(5 to "5 seconds", 10 to "10 seconds", 15 to "15 seconds", 30 to "30 seconds"),
                    note = "Takes effect the next time the app starts playing from scratch.",
                )
            }

            item { Section("About") }
            item {
                val context = LocalContext.current
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
                }
                Text(
                    "LocalStream $version\nPlays the video files on your phone like a streaming service. " +
                        "Nothing is uploaded anywhere — the app only reads the folders you add here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    removing?.let { root ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove folder?") },
            text = { Text("\"${folderLabel(root)}\" and its shows will disappear from the app. Your files are not deleted, and watch progress is kept if you add it again.") },
            confirmButton = {
                TextButton(onClick = {
                    Library.removeRoot(root)
                    removing = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

/** "content://…/tree/primary%3AMovies%2FTV" → "Movies/TV". */
private fun folderLabel(uri: String): String {
    val segment = Uri.parse(uri).lastPathSegment ?: return uri
    val path = segment.substringAfter(':', segment)
    val volume = segment.substringBefore(':', "")
    val prefix = if (volume.isNotEmpty() && volume != "primary") "SD card · " else ""
    return prefix + path.ifEmpty { "Internal storage" }
}

@Composable
private fun Section(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String, pref: Settings.BoolPref) {
    val value by pref.flow.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { pref.set(!value) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = { pref.set(it) })
    }
}

@Composable
private fun ChoiceRow(title: String, pref: Settings.IntPref, options: List<Pair<Int, String>>, note: String? = null) {
    val value by pref.flow.collectAsState()
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title)
        Text(
            options.firstOrNull { it.first == value }?.second ?: value.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (v, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pref.set(v)
                                    open = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = v == value, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                    if (note != null) {
                        Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}
