package com.housework.tracker.ui.house

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.housework.tracker.data.repository.HouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseSetupUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val houseReady: Boolean = false,
    val createdInviteCode: String? = null
)

@HiltViewModel
class HouseViewModel @Inject constructor(
    private val houseRepository: HouseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HouseSetupUiState())
    val uiState: StateFlow<HouseSetupUiState> = _uiState.asStateFlow()

    fun createHouse(name: String) {
        val userId = auth.currentUser?.uid ?: return
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "가정 이름을 입력해주세요")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val house = houseRepository.createHouse(name, userId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    createdInviteCode = house.inviteCode
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "가정 생성 중 오류가 발생했습니다"
                )
            }
        }
    }

    fun joinHouse(inviteCode: String) {
        val userId = auth.currentUser?.uid ?: return
        if (inviteCode.length != 8) {
            _uiState.value = _uiState.value.copy(error = "8자리 초대 코드를 입력해주세요")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val house = houseRepository.joinHouse(inviteCode, userId)
                if (house != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        houseReady = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "유효하지 않은 초대 코드이거나, 이미 멤버가 가득 찼습니다"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "참여 중 오류가 발생했습니다"
                )
            }
        }
    }

    fun confirmHouseCreated() {
        _uiState.value = _uiState.value.copy(houseReady = true)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
