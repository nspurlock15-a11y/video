package com.localstream.app.ui

import android.Manifest
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
