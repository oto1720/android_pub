package com.example.oto1720.dojo2026.ui.digest

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import io.noties.markwon.Markwon

@Composable
fun DigestScreen(
    articleId: String,
    onNavigateToTsundoku: () -> Unit,
    onNavigateToDone: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DigestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                DigestEvent.AddedToTsundoku -> onNavigateToTsundoku()
                DigestEvent.SlotFull -> snackbarHostState.showSnackbar("スロットがいっぱいです")
                DigestEvent.NavigateToDone -> onNavigateToDone()
            }
        }
    }

    DigestScreenContent(
        uiState = uiState,
        isOnline = isOnline,
        onNavigateBack = onNavigateBack,
        onAddToTsundoku = viewModel::addToTsundoku,
        onShowQuestion = viewModel::showQuestion,
        onSubmitMemo = viewModel::submitMemo,
        onConsumeArticle = viewModel::consumeArticle,
        onToggleViewMode = viewModel::toggleViewMode,
        onShowSummary = viewModel::showSummary,
        onDismissSummary = viewModel::dismissSummary,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DigestScreenContent(
    uiState: DigestUiState,
    isOnline: Boolean = true,
    onNavigateBack: () -> Unit,
    onAddToTsundoku: () -> Unit,
    onShowQuestion: () -> Unit,
    onSubmitMemo: (String) -> Unit,
    onConsumeArticle: () -> Unit,
    onToggleViewMode: () -> Unit,
    onShowSummary: () -> Unit = {},
    onDismissSummary: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    val article = when (uiState) {
        is DigestUiState.ReadMode -> uiState.article
        is DigestUiState.QuestionMode -> uiState.article
        is DigestUiState.FeedbackMode -> uiState.article
        else -> null
    }
    val readMode = uiState as? DigestUiState.ReadMode

    Scaffold(
        modifier = modifier,
        topBar = {
            DigestTopBar(
                article = article,
                onNavigateBack = onNavigateBack,
                isMarkdownMode = readMode?.isMarkdownMode ?: false,
                onToggleViewMode = if (readMode?.article?.body != null) onToggleViewMode else null,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState is DigestUiState.ReadMode) {
                DigestBottomBar(
                    article = uiState.article,
                    isLoading = uiState.isLoading,
                    isOnline = isOnline,
                    onAddToTsundoku = onAddToTsundoku,
                    onShowQuestion = onShowQuestion,
                    onShowSummary = onShowSummary,
                )
            }
        },
    ) { innerPadding ->
        when (uiState) {
            is DigestUiState.WebLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is DigestUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is DigestUiState.ReadMode -> {
                if (uiState.isMarkdownMode && uiState.article.body != null) {
                    MarkdownContent(
                        markdown = uiState.article.body,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                } else {
                    ArticleWebView(
                        url = uiState.article.url,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
                if (uiState.summaryDialogState != SummaryDialogState.Hidden) {
                    SummaryDialog(
                        state = uiState.summaryDialogState,
                        onDismiss = onDismissSummary,
                    )
                }
            }

            is DigestUiState.QuestionMode -> {
                QuestionModeContent(
                    question = uiState.question,
                    isLoading = uiState.isLoading,
                    onSubmitMemo = onSubmitMemo,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            is DigestUiState.FeedbackMode -> {
                FeedbackModeContent(
                    question = uiState.question,
                    memo = uiState.memo,
                    feedback = uiState.feedback,
                    canDigest = uiState.canDigest,
                    onConsumeArticle = onConsumeArticle,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DigestTopBar(
    article: ArticleEntity?,
    onNavigateBack: () -> Unit,
    isMarkdownMode: Boolean = false,
    onToggleViewMode: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    TopAppBar(
        title = {
            if (article != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val tags = article.tags
                        .split(",")
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                    if (tags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(tags.take(3)) { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "#$tag",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "戻る",
                )
            }
        },
        actions = {
            if (article != null) {
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.url}")
                    }
                    context.startActivity(Intent.createChooser(intent, "記事を共有"))
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "共有",
                    )
                }
            }
            if (onToggleViewMode != null) {
                TextButton(onClick = onToggleViewMode) {
                    Text(
                        text = if (isMarkdownMode) "Web表示" else "MD表示",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    )
}

@Composable
private fun DigestBottomBar(
    article: ArticleEntity,
    isLoading: Boolean = false,
    isOnline: Boolean = true,
    onAddToTsundoku: () -> Unit,
    onShowQuestion: () -> Unit,
    onShowSummary: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
    ) {
        when (article.status) {
            ArticleStatus.PORTAL -> {
                Button(
                    onClick = onAddToTsundoku,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = "積読に追加", style = MaterialTheme.typography.labelLarge)
                }
            }

            ArticleStatus.TSUNDOKU -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onShowSummary,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(text = "AI要約", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onShowQuestion,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isLoading && isOnline,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(text = "読了宣言", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun QuestionModeContent(
    question: String,
    isLoading: Boolean = false,
    onSubmitMemo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var memo by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FocusQuestionBlock(question = question)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "あなたの考察",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "簡潔な要約を入力してください...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            Button(
                onClick = { onSubmitMemo(memo) },
                modifier = Modifier.fillMaxWidth(),
                enabled = memo.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = "考察を送信", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun FeedbackModeContent(
    question: String,
    memo: String,
    feedback: String,
    canDigest: Boolean,
    onConsumeArticle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FocusQuestionBlock(question = question)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "あなたの考察",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = memo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "フィードバック",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!canDigest) {
                    Text(
                        text = "理解がまだ不十分です。もう一度考察してメモを書き直してみましょう。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onConsumeArticle,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canDigest,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(text = "習得済みとしてマーク", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SummaryDialog(
    state: SummaryDialogState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "AI要約") },
        text = {
            when (state) {
                is SummaryDialogState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is SummaryDialogState.Loaded -> {
                    Text(
                        text = state.summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is SummaryDialogState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                SummaryDialogState.Hidden -> {}
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "閉じる")
            }
        },
    )
}

@Composable
private fun FocusQuestionBlock(
    question: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "AIからの質問",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = question,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "これはAIが生成した提案です",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                textSize = 15f
                setLineSpacing(0f, 1.5f)
            }
        },
        update = { textView ->
            Markwon.create(textView.context).setMarkdown(textView, markdown)
        },
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

@Composable
private fun ArticleWebView(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) webView.loadUrl(url)
        },
        modifier = modifier,
    )
}
