package com.localstream.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.localstream.app.CrashReporter
import com.localstream.app.data.Episode
import com.localstream.app.data.Library
import com.localstream.app.player.PlayerActivity
import com.localstream.app.ui.theme.LocalStreamTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Media notifications work without this, but asking keeps them visible on every device.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            savedInstanceState == null
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        // Pick up episodes added to the folders since last time.
        if (savedInstanceState == null && Library.roots.value.isNotEmpty()) Library.rescan()

        setContent {
            LocalStreamTheme {
                App()
                CrashReportDialog()
            }
        }
    }
}

private const val HOME = "home"
private const val SETTINGS = "settings"
private const val SHOW_PREFIX = "show:"

@Composable
private fun App() {
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf(HOME) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) Library.addRoot(uri)
    }
    val play: (Episode, Boolean) -> Unit = { episode, startOver ->
        context.startActivity(PlayerActivity.intent(context, episode.id, startOver))
    }

    BackHandler(enabled = route != HOME) { route = HOME }

    when {
        route == SETTINGS -> SettingsScreen(
            onBack = { route = HOME },
            onAddFolder = { pickFolder.launch(null) },
        )
        route.startsWith(SHOW_PREFIX) -> ShowScreen(
            titleId = route.removePrefix(SHOW_PREFIX),
            onBack = { route = HOME },
            onPlay = play,
        )
        else -> HomeScreen(
            onOpenTitle = { route = SHOW_PREFIX + it.id },
            onPlay = { play(it, false) },
            onAddFolder = { pickFolder.launch(null) },
            onSettings = { route = SETTINGS },
        )
    }
}

/** After a crash, shows what went wrong and lets the user send the report. */
@Composable
private fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReporter.lastCrash(context)) }
    val text = report ?: return
    val dismiss = {
        CrashReporter.clear(context)
        report = null
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("LocalStream closed unexpectedly") },
        text = {
            Column {
                Text("Sharing this report helps get the problem fixed.")
                SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "LocalStream crash report")
                    .putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Share crash report"))
                dismiss()
            }) { Text("Share report") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Dismiss") } },
    )
}
