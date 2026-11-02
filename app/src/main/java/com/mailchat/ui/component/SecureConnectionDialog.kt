package com.mailchat.ui.component

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mailchat.R
import com.mailchat.crypto.KeyExchangeService
import com.mailchat.crypto.OpenPgpKeyService

enum class ScanResult { SUCCESS, INVALID, DECODE_ERROR, PERMISSION, CANCELLED }

data class ContactItem(
    val name: String,
    val email: String,
    val photoUri: String?
)

@Composable
fun SecureConnectionDialog(
    ownEmails: List<String> = emptyList(),
    contacts: List<ContactItem> = emptyList(),
    onDismiss: () -> Unit,
    onConnected: (fingerprint: String) -> Unit,
    onSendKeyByEmail: (recipient: String, keyData: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var showContactPicker by remember { mutableStateOf(false) }
    var selectedOwnEmail by remember { mutableStateOf(ownEmails.firstOrNull() ?: "") }
    var pinInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var pasteKeyInput by remember { mutableStateOf("") }
    var targetEmail by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var verificationSuccess by remember { mutableStateOf(false) }

    val hasKey = OpenPgpKeyService.hasIdentity(context)
    val myFingerprint = OpenPgpKeyService.getFingerprint(context)
    val isVerified = targetEmail.isNotBlank() && OpenPgpKeyService.getPeer(context, targetEmail) != null
    val isOwnAddress = targetEmail.isNotBlank() && ownEmails.any { it.lowercase() == targetEmail.lowercase() }
    val ownAddressError = stringResource(R.string.secure_peer_own_address)
    val emailRequiredError = stringResource(R.string.secure_peer_email)

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val qrData = result.contents
            val parsed = KeyExchangeService.parseQrScanResult(qrData)
            if (parsed != null) {
                val (qrEmail, fingerprint, pubKey) = parsed
                if (qrEmail.isNotBlank()) {
                    targetEmail = qrEmail
                }
                val effectiveEmail = if (qrEmail.isNotBlank()) qrEmail else targetEmail
                if (ownEmails.any { it.lowercase() == effectiveEmail.lowercase() }) {
                    error = ownAddressError
                } else if (effectiveEmail.isBlank()) {
                    error = emailRequiredError
                } else {
                    OpenPgpKeyService.addPeer(
                        context,
                        OpenPgpKeyService.OpenPgpPeer(
                            email = effectiveEmail,
                            publicKeyArmored = pubKey,
                            fingerprint = fingerprint
                        )
                    )
                    scanResult = ScanResult.SUCCESS
                }
            } else {
                scanResult = ScanResult.INVALID
            }
        } else {
            scanResult = ScanResult.CANCELLED
        }
    }

    // NFC: just show status, actual tag reading handled at Activity level

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isVerified) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.secure_connection),
                    modifier = Modifier.weight(1f)
                )
                com.mailchat.ui.component.HelpIcon(
                    title = stringResource(R.string.help_secure_title),
                    text = stringResource(R.string.help_secure_text)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isVerified) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.secure_connected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                if (!hasKey) {
                    Text(
                        stringResource(R.string.secure_no_key),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.secure_my_fingerprint, myFingerprint.take(4)),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " (i)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                android.widget.Toast.makeText(context, myFingerprint, android.widget.Toast.LENGTH_LONG).show()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SecureTabButton(
                            label = stringResource(R.string.secure_tab_qr),
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 }
                        ) {
                            Icon(Icons.Default.QrCode, null, modifier = Modifier.size(16.dp))
                        }
                        SecureTabButton(
                            label = stringResource(R.string.secure_tab_scan),
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 }
                        ) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                        }
                        SecureTabButton(
                            label = stringResource(R.string.secure_tab_nfc),
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 }
                        ) {
                            Icon(Icons.Default.Nfc, null, modifier = Modifier.size(16.dp))
                        }
                        SecureTabButton(
                            label = stringResource(R.string.secure_tab_email),
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 }
                        ) {
                            Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp))
                        }
                    }

                    val tabHelpText = when (selectedTab) {
                        0 -> stringResource(R.string.help_secure_qr)
                        1 -> stringResource(R.string.help_secure_scan)
                        2 -> stringResource(R.string.help_secure_nfc)
                        3 -> stringResource(R.string.help_secure_email)
                        else -> ""
                    }
                    if (tabHelpText.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tabHelpText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            com.mailchat.ui.component.HelpIcon(
                                title = stringResource(R.string.help_secure_title),
                                text = tabHelpText
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            val qrEmail = selectedOwnEmail.ifBlank { ownEmails.firstOrNull() ?: "" }
                            val qrBitmap = remember(qrEmail) {
                                KeyExchangeService.generateQrBitmap(context, qrEmail, 300)
                            }
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.secure_qr_desc),
                                        modifier = Modifier.size(180.dp)
                                    )
                                }
                                Text(
                                    stringResource(R.string.secure_qr_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    stringResource(R.string.secure_qr_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        1 -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.secure_scan_hint),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                val scanPromptText = stringResource(R.string.secure_scan_hint)
                                TextButton(
                                    onClick = {
                                        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            val options = ScanOptions().apply {
                                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                                setPrompt(scanPromptText)
                                                setBeepEnabled(false)
                                                setOrientationLocked(true)
                                            }
                                            scanLauncher.launch(options)
                                        } else {
                                            scanResult = ScanResult.PERMISSION
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.secure_scan_button))
                                }
                                scanResult?.let { result ->
                                    Spacer(modifier = Modifier.size(8.dp))
                                    val (msg, color) = when (result) {
                                        ScanResult.SUCCESS -> stringResource(R.string.secure_peer_imported) to Color(0xFF4CAF50)
                                        ScanResult.INVALID -> stringResource(R.string.secure_qr_invalid) to MaterialTheme.colorScheme.error
                                        ScanResult.DECODE_ERROR -> stringResource(R.string.secure_qr_decode_error) to MaterialTheme.colorScheme.error
                                        ScanResult.PERMISSION -> stringResource(R.string.secure_camera_permission) to MaterialTheme.colorScheme.error
                                        ScanResult.CANCELLED -> "" to Color.Transparent
                                    }
                                    if (msg.isNotBlank()) {
                                        Text(
                                            msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = color
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
                                if (nfcAdapter != null && nfcAdapter.isEnabled) {
                                    Button(
                                        onClick = {
                                            context.startActivity(android.content.Intent(context, com.mailchat.ui.screen.NfcExchangeActivity::class.java))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Nfc, null)
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(stringResource(R.string.nfc_phone_to_phone))
                                    }
                                    Spacer(modifier = Modifier.size(12.dp))
                                    Text(
                                        stringResource(R.string.secure_nfc_hint),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        stringResource(R.string.secure_nfc_instructions),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.secure_nfc_unavailable),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        3 -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.secure_email_hint),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = emailInput,
                                    onValueChange = {
                                        emailInput = it
                                        targetEmail = it.trim().lowercase()
                                    },
                                    label = { Text(stringResource(R.string.secure_peer_email)) },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                                    ),
                                    trailingIcon = {
                                        Row {
                                            if (emailInput.isNotBlank()) {
                                                androidx.compose.material3.IconButton(onClick = {
                                                    emailInput = ""
                                                    targetEmail = ""
                                                }) {
                                                    Icon(Icons.Default.Close, contentDescription = null)
                                                }
                                            }
                                            androidx.compose.material3.IconButton(onClick = { showContactPicker = true }) {
                                                Icon(Icons.Default.Person, contentDescription = null)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (ownEmails.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.secure_email_select_own),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ownEmails.forEach { email ->
                                            Text(
                                                text = email,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (selectedOwnEmail.lowercase() == email.lowercase())
                                                            MaterialTheme.colorScheme.primaryContainer
                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                    .clickable { selectedOwnEmail = email }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        if (targetEmail.isBlank()) {
                                            error = emailRequiredError
                                        } else {
                                            val pubKey = OpenPgpKeyService.getPublicKeyForQr(context, selectedOwnEmail.ifBlank { ownEmails.firstOrNull() ?: "" })
                                            onSendKeyByEmail(targetEmail, pubKey)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Email, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.secure_email_send))
                                }
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    stringResource(R.string.secure_email_paste_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = pasteKeyInput,
                                    onValueChange = { pasteKeyInput = it },
                                    label = { Text(stringResource(R.string.secure_email_paste_label)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(
                                    onClick = {
                                        val parsed = KeyExchangeService.parseQrScanResult(pasteKeyInput.trim())
                                        if (parsed != null) {
                                            val (pasteEmail, fingerprint, pubKey) = parsed
                                            if (pasteEmail.isNotBlank()) {
                                                targetEmail = pasteEmail
                                            }
                                            val effectiveEmail = if (pasteEmail.isNotBlank()) pasteEmail else targetEmail
                                            if (ownEmails.any { it.lowercase() == effectiveEmail.lowercase() }) {
                                                error = ownAddressError
                                            } else if (effectiveEmail.isBlank()) {
                                                error = emailRequiredError
                                            } else {
                                                OpenPgpKeyService.addPeer(
                                                    context,
                                                    OpenPgpKeyService.OpenPgpPeer(
                                                        email = effectiveEmail,
                                                        publicKeyArmored = pubKey,
                                                        fingerprint = fingerprint
                                                    )
                                                )
                                                scanResult = ScanResult.SUCCESS
                                                pasteKeyInput = ""
                                            }
                                        } else {
                                            scanResult = ScanResult.INVALID
                                        }
                                    },
                                    enabled = pasteKeyInput.isNotBlank()
                                ) {
                                    Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.secure_email_import))
                                }
                                scanResult?.let { result ->
                                    if (result == ScanResult.SUCCESS || result == ScanResult.INVALID) {
                                        val (msg, color) = when (result) {
                                            ScanResult.SUCCESS -> stringResource(R.string.secure_peer_imported) to Color(0xFF4CAF50)
                                            ScanResult.INVALID -> stringResource(R.string.secure_qr_invalid) to MaterialTheme.colorScheme.error
                                            else -> "" to Color.Transparent
                                        }
                                        if (msg.isNotBlank()) {
                                            Text(
                                                msg,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = color
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val peerFingerprint = OpenPgpKeyService.getPeer(context, targetEmail)?.fingerprint ?: ""
                    if (peerFingerprint.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.secure_verify_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.secure_my_fingerprint, myFingerprint.take(4)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = " (i)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cb.setPrimaryClip(android.content.ClipData.newPlainText("Fingerprint", myFingerprint))
                                        android.widget.Toast.makeText(context, myFingerprint, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.size(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.secure_peer_fingerprint, peerFingerprint.take(4)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = " (i)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cb.setPrimaryClip(android.content.ClipData.newPlainText("Fingerprint", peerFingerprint))
                                        android.widget.Toast.makeText(context, peerFingerprint, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.secure_verify_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (targetEmail.isNotBlank()) {
                        Text(
                            stringResource(R.string.secure_exchange_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (error.isNotBlank()) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    val noPeerText = stringResource(R.string.secure_no_peer_found)
                    TextButton(
                        onClick = {
                            val existingPeer = OpenPgpKeyService.getPeer(context, targetEmail)
                            if (existingPeer != null) {
                                OpenPgpKeyService.addPeer(context, existingPeer)
                                onConnected(myFingerprint)
                                verificationSuccess = true
                            } else {
                                error = noPeerText
                            }
                        },
                        enabled = peerFingerprint.isNotBlank()
                    ) {
                        Text(stringResource(R.string.secure_verify_fingerprint))
                    }
                    if (verificationSuccess) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.secure_verified_success, targetEmail),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        Text(
                            stringResource(R.string.secure_verified_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.secure_close))
            }
        }
    )

    if (showContactPicker) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showContactPicker = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text(stringResource(R.string.select_contact), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(contacts) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        targetEmail = contact.email.lowercase()
                                        emailInput = contact.email.lowercase()
                                        showContactPicker = false
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val contactBitmap = remember(contact.photoUri) {
                                    if (!contact.photoUri.isNullOrBlank()) {
                                        try {
                                            val uri = android.net.Uri.parse(contact.photoUri)
                                            val inputStream = context.contentResolver.openInputStream(uri)
                                            if (inputStream != null) {
                                                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                                                inputStream.close()
                                                bitmap?.asImageBitmap()
                                            } else null
                                        } catch (_: Exception) { null }
                                    } else null
                                }
                                if (contactBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = contactBitmap,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp).clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(contact.name.ifBlank { contact.email }, style = MaterialTheme.typography.bodyMedium)
                                    Text(contact.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SecureTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
