package com.housework.tracker.ui.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.housework.tracker.data.model.Category
import com.housework.tracker.data.model.ChecklistItem
import com.housework.tracker.data.model.DailyCompletion
import com.housework.tracker.data.repository.AuthRepository
import com.housework.tracker.data.repository.ChecklistRepository
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ChecklistUiState(
    val items: List<ChecklistItem> = emptyList(),
    val completions: List<DailyCompletion> = emptyList(),
    val dailyScores: Map<String, Long> = emptyMap(),
    val currentUserId: String = "",
    val currentUserName: String = "",
    val houseId: String = "",
    val memberNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val isEditMode: Boolean = false,
    val collapsedCategories: Set<String> = emptySet()
)

@HiltViewModel
class ChecklistViewModel @Inject constructor(
    private val checklistRepository: ChecklistRepository,
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChecklistUiState())
    val uiState: StateFlow<ChecklistUiState> = _uiState.asStateFlow()

    private val today: LocalDate get() = LocalDate.now()

    init {
        loadData()
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

                // 멤버 이름 로드
                loadMemberNames(houseId)

                // 체크리스트 항목 + 완료 기록을 실시간으로 구독
                launch {
                    combine(
                        checklistRepository.getChecklistItems(houseId),
                        checklistRepository.getCompletions(houseId, today)
                    ) { items, completions ->
                        Pair(items, completions)
                    }.collect { (items, completions) ->
                        // 일간 점수 계산
                        val dailyScores = mutableMapOf<String, Long>()
                        completions.forEach { completion ->
                            dailyScores[completion.userId] =
                                (dailyScores[completion.userId] ?: 0L) + completion.points
                        }

                        _uiState.value = _uiState.value.copy(
                            items = items,
                            completions = completions,
                            dailyScores = dailyScores,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "데이터 로드 중 오류가 발생했습니다"
                )
            }
        }
    }

    private suspend fun loadMemberNames(houseId: String) {
        try {
            val house = firestore.collection("houses").document(houseId).get().await()
            @Suppress("UNCHECKED_CAST")
            val memberIds = house.get("members") as? List<String> ?: emptyList()

            val names = mutableMapOf<String, String>()
            memberIds.forEach { memberId ->
                val memberProfile = authRepository.getUserProfile(memberId)
                if (memberProfile != null) {
                    names[memberId] = memberProfile.displayName
                }
            }
            _uiState.value = _uiState.value.copy(memberNames = names)
        } catch (_: Exception) {
            // 멤버 이름 로드 실패는 무시
        }
    }

    fun refreshMemberNames() {
        val houseId = _uiState.value.houseId
        if (houseId.isBlank()) return
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            val profile = authRepository.getUserProfile(userId)
            if (profile != null) {
                _uiState.value = _uiState.value.copy(
                    currentUserName = profile.displayName
                )
            }
            loadMemberNames(houseId)
        }
    }

    fun checkItem(item: ChecklistItem) {
        val state = _uiState.value
        if (state.houseId.isBlank()) return

        viewModelScope.launch {
            try {
                checklistRepository.checkItem(
                    houseId = state.houseId,
                    date = today,
                    item = item,
                    userId = state.currentUserId,
                    userName = state.currentUserName
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "체크 중 오류: ${e.localizedMessage}"
                )
            }
        }
    }

    fun uncheckItem(completion: DailyCompletion) {
        val state = _uiState.value
        if (state.houseId.isBlank()) return

        // 본인이 체크한 항목만 언체크 가능
        if (completion.userId != state.currentUserId) return

        viewModelScope.launch {
            try {
                checklistRepository.uncheckItem(
                    houseId = state.houseId,
                    date = today,
                    completion = completion
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "언체크 중 오류: ${e.localizedMessage}"
                )
            }
        }
    }

    fun addItem(name: String, points: Int, category: String) {
        val state = _uiState.value
        if (state.houseId.isBlank() || name.isBlank()) return

        viewModelScope.launch {
            try {
                checklistRepository.addChecklistItem(state.houseId, name, points, category)
                _uiState.value = _uiState.value.copy(showAddDialog = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "항목 추가 중 오류: ${e.localizedMessage}"
                )
            }
        }
    }

    fun toggleCategory(categoryLabel: String) {
        val current = _uiState.value.collapsedCategories
        _uiState.value = _uiState.value.copy(
            collapsedCategories = if (categoryLabel in current) {
                current - categoryLabel
            } else {
                current + categoryLabel
            }
        )
    }

    fun enterEditMode() {
        _uiState.value = _uiState.value.copy(isEditMode = true)
    }

    fun exitEditMode() {
        val state = _uiState.value
        if (state.houseId.isBlank()) return

        viewModelScope.launch {
            try {
                checklistRepository.updateItemOrders(state.houseId, state.items)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "순서 저장 중 오류: ${e.localizedMessage}"
                )
            }
            _uiState.value = _uiState.value.copy(isEditMode = false)
        }
    }

    fun moveItemById(fromId: String, toId: String) {
        val allItems = _uiState.value.items.toMutableList()
        val fromIndex = allItems.indexOfFirst { it.id == fromId }
        val toIndex = allItems.indexOfFirst { it.id == toId }
        if (fromIndex < 0 || toIndex < 0) return

        // 같은 카테고리 내에서만 이동 허용
        if (allItems[fromIndex].category != allItems[toIndex].category) return

        val item = allItems.removeAt(fromIndex)
        allItems.add(toIndex, item)
        _uiState.value = _uiState.value.copy(items = allItems)
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
