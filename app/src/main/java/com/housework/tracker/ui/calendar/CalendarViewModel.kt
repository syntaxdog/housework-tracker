package com.housework.tracker.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.housework.tracker.data.model.DailyCompletion
import com.housework.tracker.data.repository.AuthRepository
import com.housework.tracker.data.repository.ChecklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val dailyScoresMap: Map<LocalDate, Map<String, Long>> = emptyMap(),
    val monthlyScores: Map<String, Long> = emptyMap(),
    val selectedDate: LocalDate? = null,
    val selectedDateCompletions: List<DailyCompletion> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val currentUserId: String = "",
    val houseId: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val checklistRepository: ChecklistRepository,
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val profile = authRepository.getUserProfile(userId)
                val houseId = profile?.houseId ?: return@launch

                _uiState.value = _uiState.value.copy(
                    currentUserId = userId,
                    houseId = houseId
                )

                loadMemberNames(houseId)
                loadMonthData(houseId, _uiState.value.currentMonth)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
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
        } catch (_: Exception) {}
    }

    private suspend fun loadMonthData(houseId: String, yearMonth: YearMonth) {
        _uiState.value = _uiState.value.copy(isLoading = true)

        try {
            // 월간 요약 + 일별 점수를 각 1회 쿼리로 로드 (기존 30회 → 2회)
            val monthlyScores = checklistRepository.getMonthlyScores(houseId, yearMonth)
            val dailyScoresMap = checklistRepository.getMonthDailyScores(houseId, yearMonth)

            _uiState.value = _uiState.value.copy(
                dailyScoresMap = dailyScoresMap,
                monthlyScores = monthlyScores,
                isLoading = false
            )
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun refresh() {
        val houseId = _uiState.value.houseId
        if (houseId.isBlank()) return
        viewModelScope.launch {
            loadMemberNames(houseId)
            loadMonthData(houseId, _uiState.value.currentMonth)
        }
    }

    fun navigateMonth(offset: Int) {
        val newMonth = _uiState.value.currentMonth.plusMonths(offset.toLong())
        _uiState.value = _uiState.value.copy(
            currentMonth = newMonth,
            selectedDate = null,
            selectedDateCompletions = emptyList()
        )
        val houseId = _uiState.value.houseId
        if (houseId.isNotBlank()) {
            viewModelScope.launch { loadMonthData(houseId, newMonth) }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        val houseId = _uiState.value.houseId
        if (houseId.isBlank()) return

        viewModelScope.launch {
            try {
                // 선택된 날짜의 완료 기록을 일회성으로 로드
                val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val snapshot = firestore.collection("houses").document(houseId)
                    .collection("dailyLogs").document(dateStr)
                    .collection("completions").get().await()

                val completions = snapshot.documents.map { doc ->
                    doc.toObject(DailyCompletion::class.java)?.copy(id = doc.id) ?: DailyCompletion()
                }
                _uiState.value = _uiState.value.copy(selectedDateCompletions = completions)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(selectedDateCompletions = emptyList())
            }
        }
    }
}
