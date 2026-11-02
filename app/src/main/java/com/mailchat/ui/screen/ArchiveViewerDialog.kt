package com.mailchat.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mailchat.R
import com.mailchat.util.AttachmentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.io.File

@Composable
fun ArchiveViewerDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember(file) { mutableStateOf<List<FileHeader>?>(null) }
    var loadError by remember(file) { mutableStateOf(false) }
    var password by remember(file) { mutableStateOf("") }
    var showPassword by remember(file) { mutableStateOf(false) }
    var busy by remember(file) { mutableStateOf(false) }
    var actionError by remember(file) { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        entries = withContext(Dispatchers.IO) {
            try {
                ZipFile(file).fileHeaders.filter { !it.isDirectory }
            } catch (_: Exception) {
                loadError = true
                null
            }
        }
    }

    val isEncrypted = entries?.any { it.isEncrypted } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archive_viewer_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (loadError) {
                    Text(
                        text = stringResource(R.string.archive_load_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (entries == null) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 12.dp))
                } else if (entries!!.isEmpty()) {
                    Text(
                        text = stringResource(R.string.archive_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    if (isEncrypted) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.archive_password_label)) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    entries!!.forEach { header ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                            .clickable(enabled = !busy) {
                                busy = true
                                actionError = null
                                scope.launch {
                                    val extracted = withContext(Dispatchers.IO) {
                                        extractEntry(context.cacheDir, file, header, password)
                                    }
                                    if (extracted != null) {
                                        AttachmentUtils.openFile(context, extracted)
                                    } else {
                                        actionError = context.getString(R.string.archive_extract_error)
                                    }
                                    busy = false
                                }
                            }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (header.isEncrypted) Icons.Default.Description else Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = header.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2
                                )
                                Text(
                                    text = "${header.uncompressedSize / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (header.isEncrypted) {
                                Text(
                                    text = stringResource(R.string.archive_encrypted_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    actionError?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { AttachmentUtils.openFile(context, file, "application/zip") }) {
                Text(stringResource(R.string.archive_open_with_app))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

private fun extractEntry(
    cacheDir: File,
    zipPath: File,
    header: FileHeader,
    password: String
): File? {
    return try {
        val destDir = File(cacheDir, "zip_extract/${System.currentTimeMillis()}")
        destDir.mkdirs()
        val zipFile = ZipFile(zipPath)
        if (password.isNotBlank()) zipFile.setPassword(password.toCharArray())
        val safeName = header.fileName.replace("..", "_").trimStart('/', '\\').replace(Regex("[/\\\\]"), "_")
        zipFile.extractFile(header, destDir.absolutePath, safeName)
        File(destDir, safeName).takeIf { it.exists() }
    } catch (_: Exception) {
        null
    }
}
