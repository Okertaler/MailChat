package com.mailchat.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mailchat.R

data class VersionEntry(
    val version: String,
    val changesRes: Int
)

private val versionHistory = listOf(
    VersionEntry("2.1", com.mailchat.R.array.version_2_1_changes),
    VersionEntry("2.0", com.mailchat.R.array.version_2_0_changes),
    VersionEntry("1.78", com.mailchat.R.array.version_1_78_changes),
    VersionEntry("1.77", com.mailchat.R.array.version_1_77_changes),
    VersionEntry("1.76", com.mailchat.R.array.version_1_76_changes),
    VersionEntry("1.75", com.mailchat.R.array.version_1_75_changes),
    VersionEntry("1.74", com.mailchat.R.array.version_1_74_changes),
    VersionEntry("1.73", com.mailchat.R.array.version_1_73_changes),
    VersionEntry("1.72", com.mailchat.R.array.version_1_72_changes),
    VersionEntry("1.71", com.mailchat.R.array.version_1_71_changes),
    VersionEntry("1.70", com.mailchat.R.array.version_1_70_changes),
    VersionEntry("1.69", com.mailchat.R.array.version_1_69_changes),
    VersionEntry("1.68", com.mailchat.R.array.version_1_68_changes),
    VersionEntry("1.67", com.mailchat.R.array.version_1_67_changes),
    VersionEntry("1.66", com.mailchat.R.array.version_1_66_changes),
    VersionEntry("1.65", com.mailchat.R.array.version_1_65_changes),
    VersionEntry("1.64", com.mailchat.R.array.version_1_64_changes),
    VersionEntry("1.63", com.mailchat.R.array.version_1_63_changes),
    VersionEntry("1.62", com.mailchat.R.array.version_1_62_changes),
    VersionEntry("1.61", com.mailchat.R.array.version_1_61_changes),
    VersionEntry("1.60", com.mailchat.R.array.version_1_60_changes),
    VersionEntry("1.59", com.mailchat.R.array.version_1_59_changes),
    VersionEntry("1.58", com.mailchat.R.array.version_1_58_changes),
    VersionEntry("1.57", com.mailchat.R.array.version_1_57_changes),
    VersionEntry("1.56", com.mailchat.R.array.version_1_56_changes),
    VersionEntry("1.55", com.mailchat.R.array.version_1_55_changes),
    VersionEntry("1.54", com.mailchat.R.array.version_1_54_changes),
    VersionEntry("1.53", com.mailchat.R.array.version_1_53_changes),
    VersionEntry("1.52", com.mailchat.R.array.version_1_52_changes),
    VersionEntry("1.51", com.mailchat.R.array.version_1_51_changes),
    VersionEntry("1.50", com.mailchat.R.array.version_1_50_changes),
    VersionEntry("1.49", com.mailchat.R.array.version_1_49_changes),
    VersionEntry("1.48", com.mailchat.R.array.version_1_48_changes),
    VersionEntry("1.47", com.mailchat.R.array.version_1_47_changes),
    VersionEntry("1.46", com.mailchat.R.array.version_1_46_changes),
    VersionEntry("1.45", com.mailchat.R.array.version_1_45_changes),
    VersionEntry("1.44", com.mailchat.R.array.version_1_44_changes),
    VersionEntry("1.43", com.mailchat.R.array.version_1_43_changes),
    VersionEntry("1.42", com.mailchat.R.array.version_1_42_changes),
    VersionEntry("1.41", com.mailchat.R.array.version_1_41_changes),
    VersionEntry("1.40", com.mailchat.R.array.version_1_40_changes),
    VersionEntry("1.39", com.mailchat.R.array.version_1_39_changes),
    VersionEntry("1.38", com.mailchat.R.array.version_1_38_changes),
    VersionEntry("1.37", com.mailchat.R.array.version_1_37_changes),
    VersionEntry("1.36", com.mailchat.R.array.version_1_36_changes),
    VersionEntry("1.35", com.mailchat.R.array.version_1_35_changes),
    VersionEntry("1.34", com.mailchat.R.array.version_1_34_changes),
    VersionEntry("1.33", com.mailchat.R.array.version_1_33_changes),
    VersionEntry("1.32", com.mailchat.R.array.version_1_32_changes),
    VersionEntry("1.31", com.mailchat.R.array.version_1_31_changes),
    VersionEntry("1.30", com.mailchat.R.array.version_1_30_changes),
    VersionEntry("1.29", com.mailchat.R.array.version_1_29_changes),
    VersionEntry("1.28", com.mailchat.R.array.version_1_28_changes),
    VersionEntry("1.27", com.mailchat.R.array.version_1_27_changes),
    VersionEntry("1.26", com.mailchat.R.array.version_1_26_changes),
    VersionEntry("1.25", com.mailchat.R.array.version_1_25_changes),
    VersionEntry("1.24", com.mailchat.R.array.version_1_24_changes),
    VersionEntry("1.23", com.mailchat.R.array.version_1_23_changes),
    VersionEntry("1.22.1", com.mailchat.R.array.version_1_22_1_changes),
    VersionEntry("1.22", com.mailchat.R.array.version_1_22_changes),
    VersionEntry("1.21", com.mailchat.R.array.version_1_21_changes),
    VersionEntry("1.20", com.mailchat.R.array.version_1_20_changes),
    VersionEntry("1.19", com.mailchat.R.array.version_1_19_changes),
    VersionEntry("1.18", com.mailchat.R.array.version_1_18_changes),
    VersionEntry("1.17", com.mailchat.R.array.version_1_17_changes),
    VersionEntry("1.16", com.mailchat.R.array.version_1_16_changes),
    VersionEntry("1.15", com.mailchat.R.array.version_1_15_changes),
    VersionEntry("1.14", com.mailchat.R.array.version_1_14_changes),
    VersionEntry("1.13", com.mailchat.R.array.version_1_13_changes),
    VersionEntry("1.12", com.mailchat.R.array.version_1_12_changes),
    VersionEntry("1.11", com.mailchat.R.array.version_1_11_changes),
    VersionEntry("1.10", com.mailchat.R.array.version_1_10_changes),
    VersionEntry("1.9", com.mailchat.R.array.version_1_9_changes),
    VersionEntry("1.8", com.mailchat.R.array.version_1_8_changes),
    VersionEntry("1.7", com.mailchat.R.array.version_1_7_changes),
    VersionEntry("1.6.1", com.mailchat.R.array.version_1_6_1_changes),
    VersionEntry("1.6", com.mailchat.R.array.version_1_6_changes),
    VersionEntry("1.5", com.mailchat.R.array.version_1_5_changes),
    VersionEntry("1.4", com.mailchat.R.array.version_1_4_changes),
    VersionEntry("1.3", com.mailchat.R.array.version_1_3_changes),
    VersionEntry("1.2", com.mailchat.R.array.version_1_2_changes),
    VersionEntry("1.1", com.mailchat.R.array.version_1_1_changes),
    VersionEntry("1.0", com.mailchat.R.array.version_1_0_changes)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.version_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            items(versionHistory) { entry ->
                val changes = androidx.compose.ui.platform.LocalContext.current
                    .resources.getStringArray(entry.changesRes)
                Card(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = entry.version,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        changes.forEach { change ->
                            Text(
                                text = "• $change",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
