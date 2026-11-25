package com.example.ui.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.PackageExporter
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PaperBorder
import com.example.ui.theme.PaperCream
import com.example.ui.theme.PaperLinen
import com.example.ui.theme.PaperSheet
import com.example.ui.theme.StudioButterSoft
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioLavender
import com.example.ui.theme.StudioMint
import com.example.ui.theme.StudioMintSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExportPreviewScreen(
    projectId: String,
    onBack: () -> Unit,
    exportViewModel: ExportViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by exportViewModel.uiState.collectAsStateWithLifecycle()
    var selectedFile by remember { mutableStateOf("OPENCODE-PROMPT.md") }

    LaunchedEffect(projectId) {
        selectedFile = "OPENCODE-PROMPT.md"
        exportViewModel.loadProject(projectId)
    }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val p = uiState.project ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.Default) { PackageExporter.exportToZipBytes(p) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                snackbarHostState.showSnackbar("Saved FramePilot ZIP package (${bytes.size / 1024} KB)")
            }
        }
    }

    Scaffold(
        containerColor = PaperCream,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val pkg = uiState.exportedPackage
        val proj = uiState.project
        val warningCount = uiState.warningCount
        val err = uiState.loadError
        if (err != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Unable to prepare export: $err",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("export_error_text")
                    )
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            }
            return@Scaffold
        }
        if (pkg == null || proj == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = StudioCoral)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaperSheet)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("export_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("OpenCode Package Export", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${pkg.files.size} structured files · Text-only AI ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSlate
                    )
                }
            }

            // Readiness Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (warningCount == 0) StudioMintSoft else StudioButterSoft,
                border = BorderStroke(1.dp, PaperBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (warningCount == 0) Icons.Rounded.CheckCircleOutline else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (warningCount == 0) StudioMint else InkCharcoal
                    )
                    Text(
                        if (warningCount == 0)
                            "All layers and backgrounds have clear descriptions for OpenCode."
                        else
                            "$warningCount unlabelled notice(s) documented honestly in missing-info-warnings.md.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkCharcoal
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val content = pkg.files[selectedFile] ?: pkg.files["OPENCODE-PROMPT.md"].orEmpty()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(selectedFile, content))
                        scope.launch { snackbarHostState.showSnackbar("Copied $selectedFile") }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_export_file_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StudioLavender)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Copy File", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { saveZipLauncher.launch("${pkg.rootFolderName}.zip") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_zip_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioCoral)
                    Spacer(Modifier.width(5.dp))
                    Text("Save ZIP")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val bytes = withContext(Dispatchers.Default) { PackageExporter.exportToZipBytes(proj) }
                            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
                            val zipFile = File(exportsDir, "${pkg.rootFolderName}.zip")
                            withContext(Dispatchers.IO) { zipFile.writeBytes(bytes) }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share FramePilot Package"))
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("share_zip_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share ZIP", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // File Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pkg.files.keys.forEach { path ->
                    FilterChip(
                        selected = selectedFile == path,
                        onClick = { selectedFile = path },
                        label = { Text(path.substringAfterLast('/')) },
                        modifier = Modifier.testTag("export_tab_${path.replace('/', '_')}")
                    )
                }
            }

            // Monospace Document Preview
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PaperSheet,
                border = BorderStroke(1.dp, PaperBorder),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    Text(
                        selectedFile,
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioCoral
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pkg.files[selectedFile].orEmpty(),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkCharcoal,
                        modifier = Modifier.testTag("export_file_content_text")
                    )
                }
            }
        }
    }
}
