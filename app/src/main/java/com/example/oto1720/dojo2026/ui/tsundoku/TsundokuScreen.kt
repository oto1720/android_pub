package com.example.oto1720.dojo2026.ui.tsundoku

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.domain.usecase.AddToTsundokuUseCase
import com.example.oto1720.dojo2026.util.ISO_8601_FORMAT
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val MAX_TSUNDOKU_SLOTS = AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS

@Composable
fun TsundokuScreen(
    onNavigateToDigest: (articleId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TsundokuViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    TsundokuScreenContent(
        uiState = uiState,
        onArticleClick = onNavigateToDigest,
        modifier = modifier,
    )
}

@Composable
internal fun TsundokuScreenContent(
    uiState: TsundokuUiState,
    onArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is TsundokuUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is TsundokuUiState.Success -> {
            val slots = List(MAX_TSUNDOKU_SLOTS) { index -> uiState.articles.getOrNull(index) }
            val usedCount = uiState.articles.size

            Column(modifier = modifier.fillMaxSize()) {
                TsundokuHeader()

                ProgressSection(
                    usedCount = usedCount,
                    maxSlots = MAX_TSUNDOKU_SLOTS,
                )

                Text(
                    text = "現在のスロット",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        items = slots,
                        key = { index, _ -> index },
                    ) { index, article ->
                        val slotNumber = index + 1
                        AnimatedContent(
                            targetState = article,
                            label = "slot-$slotNumber",
                            transitionSpec = {
                                if (targetState != null) {
                                    // 記事追加: フェードイン + 上から展開
                                    (fadeIn(tween(300)) + expandVertically(tween(300))) togetherWith
                                        fadeOut(tween(150))
                                } else {
                                    // 消化完了: 縮小しながらフェードアウト → 空き枠がフェードイン
                                    fadeIn(tween(300, delayMillis = 200)) togetherWith
                                        (fadeOut(tween(200)) + shrinkVertically(tween(300)))
                                }
                            },
                            modifier = Modifier.animateItem(),
                        ) { currentArticle ->
                            if (currentArticle != null) {
                                OccupiedSlotCard(
                                    article = currentArticle,
                                    slotNumber = slotNumber,
                                    onStartLearning = { onArticleClick(currentArticle.id) },
                                )
                            } else {
                                EmptySlotCard(slotNumber = slotNumber)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TsundokuHeader(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = "読書スロット",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ProgressSection(
    usedCount: Int,
    maxSlots: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "空き状況",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "$usedCount / $maxSlots",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "使用中",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "上限: ${maxSlots}記事",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OccupiedSlotCard(
    article: ArticleEntity,
    slotNumber: Int,
    onStartLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = "スロット %02d".format(slotNumber),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = formatSlotMeta(article.cachedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onStartLearning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "学習を開始",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun EmptySlotCard(
    slotNumber: Int,
    modifier: Modifier = Modifier,
) {
    val dashedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .drawBehind {
                drawRoundRect(
                    color = dashedColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                            phase = 0f,
                        ),
                    ),
                )
            }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "空きスロット %02d".format(slotNumber),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
        }
    }
}

private fun formatSlotMeta(cachedAt: String): String {
    val readMinutes = 5 // プレースホルダー
    val addedAgo = formatAddedAgo(cachedAt)
    return "$readMinutes 分で読了 • $addedAgo"
}

private fun formatAddedAgo(isoDate: String): String {
    return try {
        val format = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
        val cached = format.parse(isoDate) ?: return "追加日不明"
        val now = Date()
        val diffMs = now.time - cached.time
        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)

        when {
            diffHours < 1 -> "たった今追加"
            diffDays == 0L -> "今日追加"
            diffDays == 1L -> "昨日追加"
            diffDays < 7 -> "${diffDays}日前に追加"
            else -> "一週間以上前"
        }
    } catch (e: Exception) {
        "追加日不明"
    }
}
