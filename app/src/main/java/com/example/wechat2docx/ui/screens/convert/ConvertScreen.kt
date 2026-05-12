package com.example.wechat2docx.ui.screens.convert

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.wechat2docx.R
import com.example.wechat2docx.data.share.DoubaoLauncher
import com.example.wechat2docx.domain.ConversionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    nav: NavHostController,
    url: String,
) {
    val ctx = LocalContext.current
    val application = remember { ctx.applicationContext as Application }
    val factory = remember(url) { ConvertViewModel.Factory(application, url) }
    val vm: ConvertViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.convert_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        @Suppress("DEPRECATION")
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.convert_back),
                        )
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // URL line
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Source",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = url, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Progress / status
            val step = ConversionState.progressStep(state)
            val progress = step / 5f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = ConversionState.label(ctx, state),
                style = MaterialTheme.typography.bodyLarge,
            )

            // Step list (compact summary)
            StageList(state)

            // Terminal states
            when (state) {
                is ConversionState.Success -> {
                    val s = state as ConversionState.Success
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.convert_success),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "${s.fileName}.docx",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = s.location,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = { nav.popBackStack() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.convert_done))
                        }
                        Button(
                            onClick = {
                                val uri = s.contentUri
                                if (uri != null) {
                                    DoubaoLauncher.openInDoubao(ctx, uri, s.fileName)
                                }
                            },
                            enabled = s.contentUri != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.open_in_doubao))
                        }
                    }
                }
                is ConversionState.Failure -> {
                    val s = state as ConversionState.Failure
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.convert_failure),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = s.message,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = { vm.retry() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.convert_retry)) }
                        OutlinedButton(
                            onClick = { nav.popBackStack() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.convert_back)) }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun StageList(state: ConversionState) {
    val ctx = LocalContext.current
    val current = ConversionState.progressStep(state)
    val isFailed = state is ConversionState.Failure
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StageRow(ctx.getString(R.string.stage_fetching), 1, current, isFailed)
        StageRow(ctx.getString(R.string.stage_parsing), 2, current, isFailed)
        StageRow(
            label = if (state is ConversionState.DownloadingImages)
                ctx.getString(R.string.stage_downloading_images, state.done, state.total)
            else
                ctx.getString(R.string.stage_downloading_images, 0, 0),
            stage = 3,
            current = current,
            failed = isFailed,
        )
        StageRow(ctx.getString(R.string.stage_building_docx), 4, current, isFailed)
        StageRow(
            label = if (state is ConversionState.Saving)
                ctx.getString(R.string.stage_saving, state.fileName)
            else
                ctx.getString(R.string.stage_saving, ""),
            stage = 5,
            current = current,
            failed = isFailed,
        )
    }
}

@Composable
private fun StageRow(label: String, stage: Int, current: Int, failed: Boolean) {
    val marker = when {
        failed && stage == current + 1 -> "✗"
        current >= stage -> "✓"
        current + 1 == stage -> "•"
        else -> "·"
    }
    Text(
        text = "$marker  $label",
        style = MaterialTheme.typography.bodyLarge,
        color = when {
            failed && stage > current -> MaterialTheme.colorScheme.error
            current >= stage -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
