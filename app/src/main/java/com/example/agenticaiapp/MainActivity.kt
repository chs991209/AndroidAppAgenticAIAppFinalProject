package com.example.agenticaiapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.agenticaiapp.ui.ChatScreen
import com.example.agenticaiapp.ui.ChatViewModel
import com.example.agenticaiapp.ui.theme.AgenticAITheme

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
}
