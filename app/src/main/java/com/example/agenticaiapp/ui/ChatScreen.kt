package com.example.agenticaiapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onMicTap: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Agentic AI",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO drawer */ }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "A",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.messages.isEmpty()) {
                    WelcomeContent()
                } else {
                    MessageList(messages = state.messages)
                }
            }

            InputBar(
                draft = state.draft,
                isSending = state.isSending,
                isRecording = state.isRecording,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::sendText,
                onMicTap = onMicTap,
            )
        }
    }
}

@Composable
private fun WelcomeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        val gradient = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary,
            )
        )
        Text(
            text = "Hello",
            fontSize = 56.sp,
            fontWeight = FontWeight.SemiBold,
            style = LocalTextStyle.current.copy(brush = gradient),
        )
        Text(
            text = "How can I help you today?",
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        // Spacer(modifier = Modifier.height(32.dp))
        //
        // Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        //     SuggestionChip("Brainstorm ideas\nfor a side project")
        //     SuggestionChip("Summarize a long\narticle for me")
        // }
    }
}

// @Composable
// private fun SuggestionChip(text: String) {
//     Surface(
//         shape = RoundedCornerShape(16.dp),
//         color = MaterialTheme.colorScheme.surfaceVariant,
//         modifier = Modifier
//             .width(170.dp)
//             .heightIn(min = 110.dp),
//     ) {
//         Box(
//             modifier = Modifier
//                 .padding(14.dp)
//                 .fillMaxSize(),
//             contentAlignment = Alignment.BottomStart,
//         ) {
//             Text(
//                 text = text,
//                 fontSize = 14.sp,
//                 color = MaterialTheme.colorScheme.onSurface,
//             )
//         }
//     }
// }

@Composable
private fun MessageList(messages: List<ChatMessage>) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { msg ->
            MessageBubble(msg)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Surface(
            color = bubbleColor,
            shape = shape,
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thinking…", color = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    val prefix = if (message.kind == MessageKind.VOICE) "🎙  " else ""
                    Text(
                        text = prefix + message.text,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    draft: String,
    isSending: Boolean,
    isRecording: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicTap: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (draft.isEmpty()) {
                            Text(
                                text = if (isRecording) "Listening…" else "Ask Agentic AI",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            enabled = !isRecording,
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Mic / Stop button
            IconButton(
                onClick = onMicTap,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
            ) {
                Icon(
                    imageVector = when {
                        isRecording -> Icons.Filled.Stop
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = if (isRecording) "Stop recording" else "Start voice prompt",
                    tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }

            // Send button — appears when text is present
            if (draft.isNotBlank() && !isRecording) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = !isSending,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

