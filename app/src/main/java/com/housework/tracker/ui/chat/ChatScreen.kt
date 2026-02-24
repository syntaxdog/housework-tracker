package com.housework.tracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.res.painterResource
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.housework.tracker.R
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.housework.tracker.data.model.ChatMessage
import com.housework.tracker.ui.theme.User1Blue
import com.housework.tracker.ui.theme.User2Pink
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.housework.tracker.service.HouseworkMessagingService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class EmojiType(val key: String) {
    class Png(key: String, val resId: Int) : EmojiType(key)
    class Lottie(key: String, val rawResId: Int) : EmojiType(key)
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var inputText by rememberSaveable { mutableStateOf("") }
    var showEmojiPanel by rememberSaveable { mutableStateOf(false) }

    val emojis: List<EmojiType> = listOf(
        EmojiType.Png("[EMOJI:happy]", R.drawable.happy),
        EmojiType.Png("[EMOJI:sad]", R.drawable.sad),
        EmojiType.Png("[EMOJI:angry]", R.drawable.angry),
        EmojiType.Png("[EMOJI:surprised]", R.drawable.surprised),
        EmojiType.Png("[EMOJI:basic]", R.drawable.basic),
        EmojiType.Lottie("[EMOJI:bear]", R.raw.bear),
        EmojiType.Lottie("[EMOJI:dog]", R.raw.dog)
    )

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        // 채팅 화면 진입 시 알림 히스토리 클리어 & 기존 알림 취소
        HouseworkMessagingService.clearChatNotificationHistory()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1001)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 헤더
            Text(
                text = "채팅",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 메시지 목록
                val listState = rememberLazyListState()

                // 새 메시지 올 때 자동 스크롤
                LaunchedEffect(uiState.messages.size) {
                    if (uiState.messages.isNotEmpty()) {
                        listState.animateScrollToItem(0)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    reverseLayout = true,
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    val messages = uiState.messages
                    items(messages.size, key = { messages[it].id }) { index ->
                        val message = messages[index]
                        val isMe = message.senderId == uiState.currentUserId

                        // 같은 사람 + 같은 분(minute)이어야 같은 그룹
                        val nextMessage = messages.getOrNull(index + 1)
                        val prevMessage = messages.getOrNull(index - 1)

                        val isSameGroupAsNext = nextMessage != null &&
                            nextMessage.senderId == message.senderId &&
                            isSameMinute(nextMessage, message)
                        val isSameGroupAsPrev = prevMessage != null &&
                            prevMessage.senderId == message.senderId &&
                            isSameMinute(prevMessage, message)

                        val isFirstInGroup = !isSameGroupAsNext
                        val isLastInGroup = !isSameGroupAsPrev

                        // 다른 사람 메시지 그룹 사이 간격
                        if (isFirstInGroup && index < messages.size - 1 && nextMessage?.senderId != message.senderId) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        MessageBubble(
                            message = message,
                            isMe = isMe,
                            showName = isFirstInGroup && !isMe,
                            showTime = isLastInGroup
                        )

                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            // 입력창 구분선
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )

            // 입력 영역
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showEmojiPanel = !showEmojiPanel }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Face,
                                contentDescription = "이모티콘",
                                tint = if (showEmojiPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("메시지를 입력하세요") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                    if (showEmojiPanel) {
                        HorizontalDivider()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(emojis) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clickable {
                                            viewModel.sendMessage(emoji.key)
                                            showEmojiPanel = false
                                        }
                                ) {
                                    when (emoji) {
                                        is EmojiType.Png -> {
                                            Image(
                                                painter = painterResource(id = emoji.resId),
                                                contentDescription = "이모티콘",
                                                modifier = Modifier.size(56.dp)
                                            )
                                        }
                                        is EmojiType.Lottie -> {
                                            val composition by rememberLottieComposition(
                                                LottieCompositionSpec.RawRes(emoji.rawResId)
                                            )
                                            LottieAnimation(
                                                composition = composition,
                                                iterations = LottieConstants.IterateForever,
                                                modifier = Modifier.size(56.dp)
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

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    showName: Boolean = true,
    showTime: Boolean = true
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.72f
    val timeFormat = remember { SimpleDateFormat("a h:mm", Locale.KOREAN) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        // 발신자 이름
        if (showName) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = User2Pink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isMe && showTime) {
                message.sentAt?.let {
                    Text(
                        text = timeFormat.format(it.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                    )
                }
            }

            val emojiInfo = getEmojiInfo(message.text)

            // 말풍선 — 더 진한 배경 + 그림자로 구분감 강화
            if (emojiInfo != null) {
                when (emojiInfo) {
                    is EmojiType.Png -> {
                        Image(
                            painter = painterResource(id = emojiInfo.resId),
                            contentDescription = "이모티콘",
                            modifier = Modifier
                                .size(120.dp)
                                .padding(4.dp)
                        )
                    }
                    is EmojiType.Lottie -> {
                        val composition by rememberLottieComposition(
                            LottieCompositionSpec.RawRes(emojiInfo.rawResId)
                        )
                        LottieAnimation(
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                            modifier = Modifier
                                .size(120.dp)
                                .padding(4.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .widthIn(max = maxBubbleWidth)
                        .shadow(
                            elevation = 1.dp,
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 4.dp,
                                bottomEnd = if (isMe) 4.dp else 16.dp
                            )
                        )
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 4.dp,
                                bottomEnd = if (isMe) 4.dp else 16.dp
                            )
                        )
                        .background(
                            if (isMe) User1Blue.copy(alpha = 0.25f)
                            else User2Pink.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!isMe && showTime) {
                message.sentAt?.let {
                    Text(
                        text = timeFormat.format(it.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}

private fun isSameMinute(a: ChatMessage, b: ChatMessage): Boolean {
    val tsA = a.sentAt ?: return false
    val tsB = b.sentAt ?: return false
    val calA = Calendar.getInstance().apply { time = tsA.toDate() }
    val calB = Calendar.getInstance().apply { time = tsB.toDate() }
    return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
        calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR) &&
        calA.get(Calendar.HOUR_OF_DAY) == calB.get(Calendar.HOUR_OF_DAY) &&
        calA.get(Calendar.MINUTE) == calB.get(Calendar.MINUTE)
}

private fun getEmojiInfo(text: String): EmojiType? {
    return when (text) {
        "[EMOJI:happy]" -> EmojiType.Png(text, R.drawable.happy)
        "[EMOJI:sad]" -> EmojiType.Png(text, R.drawable.sad)
        "[EMOJI:angry]" -> EmojiType.Png(text, R.drawable.angry)
        "[EMOJI:surprised]" -> EmojiType.Png(text, R.drawable.surprised)
        "[EMOJI:basic]" -> EmojiType.Png(text, R.drawable.basic)
        "[EMOJI:bear]" -> EmojiType.Lottie(text, R.raw.bear)
        "[EMOJI:dog]" -> EmojiType.Lottie(text, R.raw.dog)
        else -> null
    }
}
