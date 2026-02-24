package com.housework.tracker.ui.checklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.housework.tracker.data.model.Category
import com.housework.tracker.data.model.ChecklistItem
import com.housework.tracker.data.model.DailyCompletion
import com.housework.tracker.ui.theme.SuccessGreen
import com.housework.tracker.ui.theme.User1Blue
import com.housework.tracker.ui.theme.User2Pink
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 탭이 활성화될 때마다 멤버 이름 새로고침
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshMemberNames()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 카테고리별로 항목 그룹화
    val groupedItems = remember(uiState.items) {
        val grouped = uiState.items.groupBy { Category.fromLabel(it.category) }
        Category.orderedEntries
            .filter { category -> grouped.containsKey(category) }
            .map { category -> category to (grouped[category] ?: emptyList()) }
    }

    val lazyListState = rememberLazyListState()

    // 편집 모드에서 드래그앤드롭 - 아이템 ID 기반으로 이동
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromKey = from.key as? String
        val toKey = to.key as? String
        if (fromKey != null && toKey != null &&
            fromKey.startsWith("item_") && toKey.startsWith("item_")
        ) {
            val fromId = fromKey.removePrefix("item_")
            val toId = toKey.removePrefix("item_")
            viewModel.moveItemById(fromId, toId)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isEditMode) {
                FloatingActionButton(
                    onClick = { viewModel.showAddDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "항목 추가")
                }
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 오늘 날짜 + 편집 버튼
                item(key = "header_date") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = LocalDate.now().format(
                                DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                if (uiState.isEditMode) viewModel.exitEditMode()
                                else viewModel.enterEditMode()
                            }
                        ) {
                            Text(
                                text = if (uiState.isEditMode) "완료" else "편집",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 점수 요약 카드
                item(key = "header_score") {
                    ScoreSummaryCard(
                        dailyScores = uiState.dailyScores,
                        memberNames = uiState.memberNames,
                        currentUserId = uiState.currentUserId
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 카테고리별 그룹화된 체크리스트 항목
                groupedItems.forEach { (category, categoryItems) ->
                    val isCollapsed = category.label in uiState.collapsedCategories
                    val completedCount = categoryItems.count { item ->
                        uiState.completions.any { it.itemId == item.id }
                    }

                    // 카테고리 헤더
                    item(key = "category_${category.label}") {
                        CategoryHeader(
                            category = category,
                            itemCount = categoryItems.size,
                            completedCount = completedCount,
                            isCollapsed = isCollapsed,
                            onClick = { viewModel.toggleCategory(category.label) }
                        )
                    }

                    // 카테고리 내 항목들 (접혀있지 않을 때만)
                    if (!isCollapsed) {
                        items(
                            items = categoryItems,
                            key = { item -> "item_${item.id}" }
                        ) { item ->
                            ReorderableItem(
                                reorderableLazyListState,
                                key = "item_${item.id}"
                            ) { isDragging ->
                                val itemCompletions = uiState.completions.filter { it.itemId == item.id }
                                val myCompletions = itemCompletions.filter { it.userId == uiState.currentUserId }
                                val partnerCompletions = itemCompletions.filter { it.userId != uiState.currentUserId }
                                ChecklistItemRow(
                                    item = item,
                                    myCompletions = myCompletions,
                                    partnerCompletions = partnerCompletions,
                                    memberNames = uiState.memberNames,
                                    currentUserId = uiState.currentUserId,
                                    isEditMode = uiState.isEditMode,
                                    isDragging = isDragging,
                                    onCheck = { viewModel.checkItem(item) },
                                    onUncheck = { completion -> viewModel.uncheckItem(completion) },
                                    dragModifier = if (uiState.isEditMode) Modifier.draggableHandle() else Modifier
                                )
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        // 항목 추가 다이얼로그
        if (uiState.showAddDialog) {
            AddItemDialog(
                onDismiss = { viewModel.hideAddDialog() },
                onAdd = { name, points, category -> viewModel.addItem(name, points, category) }
            )
        }
    }
}

@Composable
private fun CategoryHeader(
    category: Category,
    itemCount: Int,
    completedCount: Int,
    isCollapsed: Boolean,
    onClick: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(200),
        label = "arrowRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = category.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$completedCount/$itemCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (isCollapsed) "펼치기" else "접기",
            modifier = Modifier
                .size(20.dp)
                .rotate(arrowRotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreSummaryCard(
    dailyScores: Map<String, Long>,
    memberNames: Map<String, String>,
    currentUserId: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "오늘의 점수",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                memberNames.entries.forEachIndexed { index, (memberId, name) ->
                    val color = if (memberId == currentUserId) User1Blue else User2Pink
                    val dailyScore = dailyScores[memberId] ?: 0L
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = color
                        )
                        Text(
                            text = "${dailyScore}점",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                    if (index == 0 && memberNames.size > 1) {
                        Text(
                            text = "vs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    myCompletions: List<DailyCompletion>,
    partnerCompletions: List<DailyCompletion>,
    memberNames: Map<String, String>,
    currentUserId: String,
    isEditMode: Boolean,
    isDragging: Boolean,
    onCheck: () -> Unit,
    onUncheck: (DailyCompletion) -> Unit,
    dragModifier: Modifier = Modifier
) {
    val totalCount = myCompletions.size + partnerCompletions.size
    val hasAnyCompletion = totalCount > 0
    val cardColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
            hasAnyCompletion -> SuccessGreen.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(300),
        label = "cardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditMode) {
                // 드래그 핸들
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(40.dp)
                        .then(dragModifier)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "순서 변경",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // 체크 버튼 (항상 체크 가능)
                IconButton(
                    onClick = { onCheck() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (hasAnyCompletion) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (hasAnyCompletion) "체크됨" else "미완료",
                        tint = if (hasAnyCompletion) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 항목명
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            if (!isEditMode) {
                // 수행 횟수 표시
                if (hasAnyCompletion) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (myCompletions.isNotEmpty()) {
                            val myName = memberNames[currentUserId] ?: "나"
                            Text(
                                text = "$myName ${myCompletions.size}회",
                                style = MaterialTheme.typography.labelMedium,
                                color = User1Blue
                            )
                        }
                        if (partnerCompletions.isNotEmpty()) {
                            val partnerId = partnerCompletions.first().userId
                            val partnerName = memberNames[partnerId] ?: partnerCompletions.first().userName
                            Text(
                                text = "$partnerName ${partnerCompletions.size}회",
                                style = MaterialTheme.typography.labelMedium,
                                color = User2Pink
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // 점수 뱃지
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "${item.points}점",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // 내 체크 취소 (편집 모드가 아닐 때만)
        if (!isEditMode && myCompletions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 64.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "내 체크 ${myCompletions.size}회",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onUncheck(myCompletions.last()) }
                        .padding(4.dp)
                )
            }
        }
    }
}
