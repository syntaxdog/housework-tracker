package com.housework.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.housework.tracker.data.model.ChecklistItem
import com.housework.tracker.data.repository.AuthRepository
import com.housework.tracker.data.repository.NotificationPreferences
import com.housework.tracker.data.repository.NotificationRepository
import com.housework.tracker.data.repository.ThemeMode
import com.housework.tracker.data.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String = "",
    val email: String = "",
    val houseId: String = "",
    val houseName: String = "",
    val inviteCode: String = "",
    val memberNames: List<String> = emptyList(),
    val checklistItems: List<ChecklistItem> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationPrefs: NotificationPreferences = NotificationPreferences(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val loggedOut: Boolean = false,
    val showEditItemDialog: EditItemDialogState? = null
)

data class EditItemDialogState(
    val item: ChecklistItem,
    val isNew: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val themeRepository: ThemeRepository,
    private val notificationRepository: NotificationRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeTheme()
        observeNotificationPrefs()
    }

    private fun observeTheme() {
        viewModelScope.launch {
            themeRepository.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
    }

    private fun loadSettings() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val profile = authRepository.getUserProfile(userId)
                val houseId = profile?.houseId ?: ""

                _uiState.value = _uiState.value.copy(
                    displayName = profile?.displayName ?: "",
                    email = profile?.email ?: "",
                    houseId = houseId
                )

                if (houseId.isNotBlank()) {
                    loadHouseInfo(houseId)
                    loadChecklistItems(houseId)
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage
                )
            }
        }
    }

    private suspend fun loadHouseInfo(houseId: String) {
        val houseDoc = firestore.collection("houses").document(houseId).get().await()
        val houseName = houseDoc.getString("name") ?: ""
        val inviteCode = houseDoc.getString("inviteCode") ?: ""
        @Suppress("UNCHECKED_CAST")
        val memberIds = houseDoc.get("members") as? List<String> ?: emptyList()

        val names = memberIds.mapNotNull { memberId ->
            authRepository.getUserProfile(memberId)?.displayName
        }

        _uiState.value = _uiState.value.copy(
            houseName = houseName,
            inviteCode = inviteCode,
            memberNames = names
        )
    }

    private suspend fun loadChecklistItems(houseId: String) {
        val snapshot = firestore.collection("houses").document(houseId)
            .collection("checklistItems").orderBy("order").get().await()

        val items = snapshot.documents.map { doc ->
            doc.toObject(ChecklistItem::class.java)?.copy(id = doc.id) ?: ChecklistItem()
        }
        _uiState.value = _uiState.value.copy(checklistItems = items)
    }

    fun refreshChecklistItems() {
        val houseId = _uiState.value.houseId
        if (houseId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    loadChecklistItems(houseId)
                } catch (_: Exception) {}
            }
        }
    }

    private fun observeNotificationPrefs() {
        viewModelScope.launch {
            notificationRepository.preferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(notificationPrefs = prefs)
            }
        }
    }

    fun setPartnerCheckNotification(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepository.setPartnerCheckEnabled(enabled)
        }
    }

    fun setDailyReminderNotification(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepository.setDailyReminderEnabled(enabled)
        }
    }

    fun setChatNotification(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepository.setChatNotificationEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
        }
    }

    fun showEditItemDialog(item: ChecklistItem) {
        _uiState.value = _uiState.value.copy(
            showEditItemDialog = EditItemDialogState(item)
        )
    }

    fun hideEditItemDialog() {
        _uiState.value = _uiState.value.copy(showEditItemDialog = null)
    }

    fun updateItem(item: ChecklistItem, newName: String, newPoints: Int) {
        val houseId = _uiState.value.houseId
        if (houseId.isBlank()) return

        viewModelScope.launch {
            try {
                firestore.collection("houses").document(houseId)
                    .collection("checklistItems").document(item.id)
                    .update(
                        mapOf(
                            "name" to newName,
                            "points" to newPoints
                        )
                    ).await()

                hideEditItemDialog()
                loadChecklistItems(houseId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "수정 실패: ${e.localizedMessage}")
            }
        }
    }

    fun deleteItem(item: ChecklistItem) {
        val houseId = _uiState.value.houseId
        if (houseId.isBlank()) return

        viewModelScope.launch {
            try {
                firestore.collection("houses").document(houseId)
                    .collection("checklistItems").document(item.id)
                    .delete().await()

                hideEditItemDialog()
                loadChecklistItems(houseId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "삭제 실패: ${e.localizedMessage}")
            }
        }
    }

    fun updateDisplayName(newName: String) {
        val userId = auth.currentUser?.uid ?: return
        if (newName.isBlank()) return

        viewModelScope.launch {
            try {
                firestore.collection("users").document(userId)
                    .update("displayName", newName).await()
                _uiState.value = _uiState.value.copy(displayName = newName)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "이름 변경 실패: ${e.localizedMessage}")
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = _uiState.value.copy(loggedOut = true)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
