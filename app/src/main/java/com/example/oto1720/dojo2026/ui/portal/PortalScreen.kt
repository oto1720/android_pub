package com.example.oto1720.dojo2026.ui.portal

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.domain.model.AppError
import com.example.oto1720.dojo2026.domain.model.SortOrder
import com.example.oto1720.dojo2026.domain.model.toAppError
import com.example.oto1720.dojo2026.domain.usecase.AddToTsundokuUseCase

private val MAX_TSUNDOKU_SLOTS = AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS

@Composable
fun PortalScreen(
    onNavigateToTsundoku: () -> Unit,
    onNavigateToDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortalViewModel = hiltViewModel(),
) {
    val pagingItems = viewModel.portalArticles.collectAsLazyPagingItems()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val tsundokuCount by viewModel.tsundokuCount.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                PortalEvent.SlotFull -> snackbarHostState.showSnackbar("スロットがいっぱいです")
                PortalEvent.AddedToTsundoku -> snackbarHostState.showSnackbar("スロットに追加しました")
                PortalEvent.ShowOfflineMessage -> snackbarHostState.showSnackbar("オフラインです。キャッシュを表示しています")
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PortalScreenContent(
            pagingItems = pagingItems,
            selectedTag = selectedTag,
            availableTags = availableTags,
            sortOrder = sortOrder,
            tsundokuCount = tsundokuCount,
            isOnline = isOnline,
            onTagSelected = viewModel::selectTag,
            onSortOrderChange = viewModel::onSortOrderChange,
            onRetry = { pagingItems.retry() },
            onAddToSlot = viewModel::addToTsundoku,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
internal fun PortalScreenContent(
    pagingItems: LazyPagingItems<ArticleEntity>,
    selectedTag: String?,
    availableTags: List<String>,
    sortOrder: SortOrder,
    tsundokuCount: Int,
    isOnline: Boolean = true,
    onTagSelected: (String?) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onRetry: () -> Unit,
    onAddToSlot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshState = pagingItems.loadState.refresh

    when {
        refreshState is LoadState.Loading && pagingItems.itemCount == 0 -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        refreshState is LoadState.Error && pagingItems.itemCount == 0 -> {
            val appError = refreshState.error as? AppError ?: refreshState.error.toAppError()
            ErrorContent(
                error = appError,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        else -> {
            val isSlotFull = tsundokuCount >= MAX_TSUNDOKU_SLOTS
            val listState = rememberLazyListState()

            // ソート順が変わったら先頭にスクロール
            LaunchedEffect(sortOrder) {
                listState.scrollToItem(0)
            }

            Column(modifier = modifier.fillMaxSize()) {
                // Header: タイトル + 検索バー + フィルタチップ + ソート
                PortalHeader(
                    availableTags = availableTags,
                    selectedTag = selectedTag,
                    isOnline = isOnline,
                    onTagSelected = onTagSelected,
                    sortOrder = sortOrder,
                    onSortOrderChange = onSortOrderChange,
                )

                // 空きスロット表示バー
                SlotsCounterBar(
                    usedCount = tsundokuCount,
                    maxSlots = MAX_TSUNDOKU_SLOTS,
                )

                // 満杯バナー
                if (isSlotFull) {
                    SlotFullBanner()
                }

                // 記事一覧
                if (pagingItems.itemCount == 0 && refreshState is LoadState.NotLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "記事がありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        items(count = pagingItems.itemCount) { index ->
                            pagingItems[index]?.let { article ->
                                ArticleCard(
                                    article = article,
                                    isSlotFull = isSlotFull,
                                    onAddToSlot = { onAddToSlot(article.id) },
                                )
                            }
                        }

                        // 追加ロード中インジケーター
                        if (pagingItems.loadState.append is LoadState.Loading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        // リスト末尾インジケーター
                        if (pagingItems.loadState.append.endOfPaginationReached) {
                            item {
                                EndOfListIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalHeader(
    availableTags: List<String>,
    selectedTag: String?,
    isOnline: Boolean = true,
    onTagSelected: (String?) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Tech-Digest ポータル",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!isOnline) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = "オフライン",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (availableTags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChipPill(
                        label = "すべて",
                        selected = selectedTag == null,
                        onClick = { onTagSelected(null) },
                    )
                }
                items(availableTags) { tag ->
                    FilterChipPill(
                        label = tag,
                        selected = selectedTag == tag,
                        onClick = { onTagSelected(tag) },
                    )
                }
            }
        }

        // ソート切り替え
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipPill(
                label = "新着順",
                selected = sortOrder == SortOrder.BY_DATE_DESC,
                onClick = { onSortOrderChange(SortOrder.BY_DATE_DESC) },
            )
            FilterChipPill(
                label = "いいね順",
                selected = sortOrder == SortOrder.BY_LIKES_DESC,
                onClick = { onSortOrderChange(SortOrder.BY_LIKES_DESC) },
            )
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
        shape = RoundedCornerShape(9999.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SlotsCounterBar(
    usedCount: Int,
    maxSlots: Int,
    modifier: Modifier = Modifier,
) {
    val availableCount = (maxSlots - usedCount).coerceIn(0, maxSlots)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "空きスロット",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "$availableCount / $maxSlots",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun ArticleCard(
    article: ArticleEntity,
    isSlotFull: Boolean,
    onAddToSlot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tags = article.tags.split(",").map(String::trim).filter(String::isNotEmpty)
    val firstTag = tags.firstOrNull()?.let { "#$it" } ?: ""


    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSlotFull)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 画像エリア（プレースホルダー）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ),
                    ),
            ) {
                if (isSlotFull) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "スロット制限中",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopStart),
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        ),
                    ) {
                        Text(
                            text = "トレンド",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSlotFull)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "読了まで 5分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (firstTag.isNotEmpty()) {
                        Text(
                            text = firstTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (!isSlotFull) onAddToSlot()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSlotFull,
                    colors = if (isSlotFull) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isSlotFull) "スロットがいっぱいです" else "スロットに追加",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error.message ?: "エラーが発生しました",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            // 401/403 はリトライしても解決しないためボタンを非表示
            if (error !is AppError.Unauthorized && error !is AppError.Forbidden) {
                Button(onClick = onRetry) {
                    Text("リトライ")
                }
            }
        }
    }
}

@Composable
private fun SlotFullBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "積読スロットが満杯です",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "スロット一覧から記事を消化すると追加できます",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun EndOfListIndicator(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "今日のキュレーションは以上です。\n新しい記事は明日届きます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
