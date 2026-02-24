package com.housework.tracker.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.housework.tracker.data.model.DailyCompletion
import com.housework.tracker.ui.theme.User1Blue
import com.housework.tracker.ui.theme.User2Pink
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 탭이 활성화될 때마다 데이터 새로고침
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    if (uiState.isLoading && uiState.dailyScoresMap.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // 월 네비게이션
        item {
            MonthNavigationHeader(
                currentMonth = uiState.currentMonth,
                onPrevious = { viewModel.navigateMonth(-1) },
                onNext = { viewModel.navigateMonth(1) }
            )
        }

        // 월간 점수 요약
        item {
            MonthlyScoreSummary(
                monthlyScores = uiState.monthlyScores,
                memberNames = uiState.memberNames,
                currentUserId = uiState.currentUserId
            )
        }

        // 달력 그리드
        item {
            CalendarGrid(
                yearMonth = uiState.currentMonth,
                dailyScoresMap = uiState.dailyScoresMap,
                selectedDate = uiState.selectedDate,
                onDateClick = { viewModel.selectDate(it) }
            )
        }

        // 선택된 날짜 상세
        if (uiState.selectedDate != null) {
            item {
                SelectedDateDetail(
                    date = uiState.selectedDate!!,
                    completions = uiState.selectedDateCompletions,
                    memberNames = uiState.memberNames,
                    currentUserId = uiState.currentUserId
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun MonthNavigationHeader(
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
        }
        Text(
            text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
        }
    }
}

@Composable
private fun MonthlyScoreSummary(
    monthlyScores: Map<String, Long>,
    memberNames: Map<String, String>,
    currentUserId: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            memberNames.entries.forEach { (memberId, name) ->
                val color = if (memberId == currentUserId) User1Blue else User2Pink
                val score = monthlyScores[memberId] ?: 0L
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = name, style = MaterialTheme.typography.bodyMedium, color = color)
                    Text(
                        text = "${score}점",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    dailyScoresMap: Map<LocalDate, Map<String, Long>>,
    selectedDate: LocalDate?,
    onDateClick: (LocalDate) -> Unit
) {
    val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")
    val firstDayOfMonth = yearMonth.atDay(1)
    // 일요일=0 기준 오프셋
    val startOffset = (firstDayOfMonth.dayOfWeek.value % 7)
    val totalDays = yearMonth.lengthOfMonth()

    // 최대 점수 계산 (잔디 밀도 기준)
    val maxDailyTotal = dailyScoresMap.values.maxOfOrNull { scores ->
        scores.values.sum()
    } ?: 1L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 요일 헤더
            Row(modifier = Modifier.fillMaxWidth()) {
                daysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 날짜 셀
            var dayCounter = 1
            val totalCells = startOffset + totalDays
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        if (cellIndex < startOffset || dayCounter > totalDays) {
                            // 빈 셀
                            Box(modifier = Modifier.weight(1f))
                        } else {
                            val date = yearMonth.atDay(dayCounter)
                            val scores = dailyScoresMap[date]
                            val totalScore = scores?.values?.sum() ?: 0L
                            val intensity = if (totalScore > 0) {
                                (totalScore.toFloat() / maxDailyTotal).coerceIn(0.2f, 1f)
                            } else 0f

                            CalendarDayCell(
                                day = dayCounter,
                                intensity = intensity,
                                isSelected = date == selectedDate,
                                isToday = date == LocalDate.now(),
                                onClick = { onDateClick(date) },
                                modifier = Modifier.weight(1f)
                            )
                            dayCounter++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDateDetail(
    date: LocalDate,
    completions: List<DailyCompletion>,
    memberNames: Map<String, String>,
    currentUserId: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (completions.isEmpty()) {
                Text(
                    text = "기록된 활동이 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                completions.forEach { completion ->
                    val isMe = completion.userId == currentUserId
                    val color = if (isMe) User1Blue else User2Pink
                    val name = memberNames[completion.userId] ?: completion.userName

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                color = color,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "  ${completion.itemName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = "${completion.points}점",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 날짜별 합산
                val userTotals = completions.groupBy { it.userId }
                    .mapValues { (_, comps) -> comps.sumOf { it.points } }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    memberNames.entries.forEach { (memberId, name) ->
                        val color = if (memberId == currentUserId) User1Blue else User2Pink
                        val total = userTotals[memberId] ?: 0
                        Text(
                            text = "$name ${total}점",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
        }
    }
}
