package com.mailchat.ui.screen

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mailchat.R
import com.mailchat.crypto.OpenPgpKeyService
import com.mailchat.ui.theme.MailchatTheme

@OptIn(ExperimentalMaterial3Api::class)
class NfcExchangeActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var receivedPeerEmail: String? = null
    private var receivedFingerprint: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setupNfc()

        handleIntent(intent)

        setContent {
            MailchatTheme {
                NfcExchangeContent(
                    onBack = { finish() },
                    receivedEmail = receivedPeerEmail,
                    receivedFingerprint = receivedFingerprint
                )
            }
        }
    }

    private fun setupNfc() {
        if (nfcAdapter == null) return
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE
        )
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action
        ) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                readNfcTag(tag)
            }
        }
    }

    private fun readNfcTag(tag: Tag) {
        try {
            val ndef = Ndef.get(tag) ?: return
            ndef.connect()
            val message = ndef.ndefMessage
            ndef.close()
            if (message != null && message.records.isNotEmpty()) {
                val payload = message.records[0].payload
                val data = String(payload, Charsets.UTF_8)
                val parsed = OpenPgpKeyService.parseQrData(data)
                if (parsed != null) {
                    val (email, fingerprint, pubKey) = parsed
                    val effectiveEmail = if (email.isNotBlank()) email else ""
                    if (effectiveEmail.isNotBlank()) {
                        OpenPgpKeyService.addPeer(
                            this,
                            OpenPgpKeyService.OpenPgpPeer(
                                email = effectiveEmail,
                                publicKeyArmored = pubKey,
                                fingerprint = fingerprint
                            )
                        )
                        receivedPeerEmail = effectiveEmail
                        receivedFingerprint = fingerprint
                        Toast.makeText(this, R.string.nfc_key_received, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore read errors
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter != null && pendingIntent != null) {
            nfcAdapter?.enableForegroundDispatch(
                this,
                pendingIntent,
                null,
                arrayOf(arrayOf("android.nfc.tech.Ndef"))
            )
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcExchangeContent(
    onBack: () -> Unit,
    receivedEmail: String?,
    receivedFingerprint: String?
) {
    val context = LocalContext.current
    val nfcAvailable = remember {
        NfcAdapter.getDefaultAdapter(context) != null && NfcAdapter.getDefaultAdapter(context)!!.isEnabled
    }
    val myEmail = remember { OpenPgpKeyService.getKeyEmail(context) ?: "" }
    val myFingerprint = remember { OpenPgpKeyService.getFingerprint(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nfc_exchange_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!nfcAvailable) {
                Icon(
                    Icons.Default.Nfc,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.secure_nfc_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                return@Scaffold
            }

            Icon(
                Icons.Default.Nfc,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(16.dp))

            Text(
                stringResource(R.string.nfc_exchange_instructions),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(24.dp))

            // My info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.nfc_my_info),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    if (myEmail.isNotBlank()) {
                        Text(myEmail, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (myFingerprint != null) {
                        Text(
                            stringResource(R.string.nfc_fingerprint, myFingerprint.take(4)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(24.dp))

            // Received info
            if (receivedEmail != null && receivedFingerprint != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE8F5E9).copy(alpha = if (android.os.Build.VERSION.SDK_INT >= 23) 1f else 1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.nfc_exchange_success),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(receivedEmail, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.nfc_fingerprint, receivedFingerprint.take(4)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.nfc_auto_verified),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(16.dp))

                // Now show instructions for the other direction
                Text(
                    stringResource(R.string.nfc_exchange_other_direction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Waiting indicator
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.nfc_waiting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
