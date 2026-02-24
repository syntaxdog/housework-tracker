package com.housework.tracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.housework.tracker.data.model.ChatMessage
import com.housework.tracker.data.repository.AuthRepository
import com.housework.tracker.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentUserId: String = "",
    val currentUserName: String = "",
    val houseId: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadData()
        setChatActive(true)
    }

    override fun onCleared() {
        super.onCleared()
        setChatActive(false)
    }

    private fun setChatActive(active: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .set(mapOf("chatActive" to active), SetOptions.merge())
    }

    private fun loadData() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val profile = authRepository.getUserProfile(userId)
                val houseId = profile?.houseId ?: return@launch

                _uiState.value = _uiState.value.copy(
                    currentUserId = userId,
                    currentUserName = profile.displayName,
                    houseId = houseId,
                    isLoading = true
                )

                launch {
                    chatRepository.getMessages(houseId).collect { messages ->
                        _uiState.value = _uiState.value.copy(
                            messages = messages,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "메시지 로드 중 오류가 발생했습니다"
                )
            }
        }
    }

    fun sendMessage(text: String) {
        val state = _uiState.value
        if (state.houseId.isBlank() || text.isBlank()) return

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(
                    houseId = state.houseId,
                    senderId = state.currentUserId,
                    senderName = state.currentUserName,
                    text = text.trim()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "메시지 전송 중 오류: ${e.localizedMessage}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
