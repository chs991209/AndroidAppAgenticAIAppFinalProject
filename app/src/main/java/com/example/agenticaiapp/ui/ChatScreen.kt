package com.example.agenticaiapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Agentic AI", // Gemini 스타일 타이포그래피
                            fontWeight = FontWeight.Medium,
                            fontSize = 22.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO drawer */ }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    // Profile Placeholder
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "U",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
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
                    MessageList(
                        messages = state.messages,
                        onVideoCardClick = { url -> viewModel.openVideo(url) },
                    )
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
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        val gradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF4285F4), // Google Blue
                Color(0xFFEA4335), // Google Red
                Color(0xFFFBBC05), // Google Yellow
            )
        )
        Text(
            text = "Hello,",
            fontSize = 48.sp,
            fontWeight = FontWeight.Medium,
            style = LocalTextStyle.current.copy(brush = gradient),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "How can I help you today?",
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    onVideoCardClick: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    val tailLength = messages.lastOrNull()?.text?.length ?: 0
    val tailHasCard = messages.lastOrNull()?.videoCard != null

    LaunchedEffect(messages.size, tailLength, tailHasCard) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp), // 제미나이는 메시지 간격이 넓은 편입니다.
    ) {
        items(messages, key = { it.id }) { msg ->
            MessageBubble(
                message = msg,
                onVideoCardClick = onVideoCardClick,
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(durationMillis = 300),
                    placementSpec = tween(durationMillis = 300),
                ),
            )
        }
    }
}

@Composable
private fun LazyItemScope.MessageBubble(
    message: ChatMessage,
    onVideoCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Agent: 왼쪽 반짝임 아이콘
        if (!isUser) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Agent",
                tint = MaterialTheme.colorScheme.primary, // 혹은 그라데이션 적용 가능
                modifier = Modifier
                    .padding(end = 16.dp, top = 2.dp)
                    .size(24.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (isUser) {
                // User: 둥글고 모던한 버블 형태 (Surface 사용)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(start = 32.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = message.text,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
            } else {
                // Agent: 버블 없이 텍스트만 표시 (제미나이 고유 스타일)
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .animateContentSize(animationSpec = tween(durationMillis = 200))
                ) {
                    if (message.isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        val prefix = if (message.kind == MessageKind.VOICE) "🎙  " else ""
                        Text(
                            text = prefix + message.text,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 26.sp // 가독성을 위해 줄간격 확대
                        )
                    }
                }
            }

            // Video Card (Agent가 첨부한 경우)
            AnimatedVisibility(
                visible = message.videoCard != null,
                enter = fadeIn(animationSpec = tween(durationMillis = 400)) +
                        expandVertically(animationSpec = tween(durationMillis = 400)) +
                        slideInVertically(
                            initialOffsetY = { -it / 4 },
                            animationSpec = tween(durationMillis = 400),
                        ),
            ) {
                val card = message.videoCard
                if (card != null) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        VideoCard(card = card, onClick = { onVideoCardClick(card.videoUrl) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(card: VideoCardData, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .widthIn(max = 280.dp) // 모바일에 어울리는 적절한 너비
            .clickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                AsyncImage(
                    model = card.thumbnailUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = card.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${card.channel} · ${card.viewCount}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    // 하단을 꽉 채우는 대신 알약(Pill) 모양의 통합 컨테이너를 사용 (제미나이 스타일)
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom, // 여러 줄 입력 시 아이콘들이 하단에 위치하도록
            ) {
                // Text Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .heightIn(min = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = if (isRecording) "Listening..." else "Type, talk, or share...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        enabled = !isRecording,
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5 // 너무 길어지지 않도록 제한
                    )
                }

                // Mic or Send Button
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        // 텍스트가 있으면 전송 버튼, 없으면 마이크 버튼으로 자연스럽게 교체
                        AnimatedContent(
                            targetState = draft.isNotBlank() || isRecording,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(150)) togetherWith
                                        fadeOut(animationSpec = tween(150)) using
                                        SizeTransform(clip = false)
                            },
                            label = "SendOrMic"
                        ) { showSendOrStop ->
                            if (showSendOrStop) {
                                val isStop = isRecording
                                IconButton(
                                    onClick = if (isStop) onMicTap else onSend,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isStop) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Icon(
                                        imageVector = if (isStop) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                                        contentDescription = if (isStop) "Stop" else "Send",
                                        tint = if (isStop) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                IconButton(onClick = onMicTap) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = "Mic",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}