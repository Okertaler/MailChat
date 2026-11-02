package com.mailchat.ui.screen

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.mailchat.R
import java.util.Locale

/**
 * Displays a legal document (privacy policy, disclaimer, imprint) in a WebView.
 * The document is loaded from the app assets, localized to the device language
 * (German or English).
 *
 * @param doc one of "privacy", "disclaimer", "imprint"
 * @param onBack called when the user taps the back button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    doc: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val title = when (doc) {
        "privacy" -> stringResource(R.string.legal_privacy)
        "disclaimer" -> stringResource(R.string.legal_disclaimer)
        "imprint" -> stringResource(R.string.legal_imprint)
        "licenses" -> stringResource(R.string.legal_licenses)
        else -> stringResource(R.string.info)
    }

    // Determine the language folder (de/en) from the device locale.
    val lang = Locale.getDefault().language.lowercase()
    val langFolder = if (lang.startsWith("de")) "de" else "en"
    val fileName = when (doc) {
        "privacy" -> if (langFolder == "de") "datenschutz.html" else "privacy.html"
        "disclaimer" -> if (langFolder == "de") "hinweise.html" else "disclaimer.html"
        "imprint" -> if (langFolder == "de") "impressum.html" else "imprint.html"
        "licenses" -> if (langFolder == "de") "lizenzen.html" else "licenses.html"
        else -> return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = true
                    // Load the localized HTML from assets.
                    loadUrl("file:///android_asset/legal/$langFolder/$fileName")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
