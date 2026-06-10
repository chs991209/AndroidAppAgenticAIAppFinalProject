package com.example.agenticaiapp

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.agenticaiapp.ui.ChatEvent
import com.example.agenticaiapp.ui.ChatScreen
import com.example.agenticaiapp.ui.ChatViewModel
import com.example.agenticaiapp.ui.theme.AgenticAITheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            chatViewModel.toggleRecording()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgenticAITheme {
                ChatScreen(
                    viewModel = chatViewModel,
                    onMicTap = ::handleMicTap,
                )
            }
        }
        observeAgentEvents()
    }

    private fun observeAgentEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.events.collect { event ->
                    when (event) {
                        is ChatEvent.OpenYouTube -> openYouTube(event.url)
                    }
                }
            }
        }
    }

    private fun openYouTube(url: String) {
        val uri = Uri.parse(url)
        val videoId = uri.getQueryParameter("v")

        // 1) Preferred: YouTube deep-link scheme — opens the app directly.
        if (videoId != null) {
            val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                setPackage(YOUTUBE_PACKAGE)
            }
            try {
                startActivity(deepLink); return
            } catch (_: ActivityNotFoundException) { /* fall through */ }
        }

        // 2) Same https URL, but pinned to the YouTube app package.
        val pinnedHttps = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(YOUTUBE_PACKAGE) }
        try {
            startActivity(pinnedHttps); return
        } catch (_: ActivityNotFoundException) { /* fall through */ }

        // 3) Last resort — any handler (typically the browser).
        // Only reached when the YouTube app is not installed (e.g. AOSP/non-Play emulator image).
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun handleMicTap() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            chatViewModel.toggleRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}
