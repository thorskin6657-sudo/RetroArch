package com.thor.gamehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Top-level content for both the main display and the second-screen
 * Presentation — whichever game module is currently active (per
 * GET_STATUS auto-detection, or a manual override) renders its own
 * Screen(); when nothing's detected yet, shows a picker so you can force
 * one while testing.
 */
@Composable
fun HubScreen(viewModel: HubViewModel) {
    val active by viewModel.activeModule.collectAsState()
    val contentName by viewModel.lastContentName.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Thor Game Hub", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "RetroArch reports: ${contentName ?: "nothing loaded"}",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(12.dp))

        if (active == null) {
            Text("No matching game module detected. Force one for testing:")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.registry.modules.forEach { module ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.forceModule(module) },
                        label = { Text(module.displayName) }
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Active: ${active!!.displayName}", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.forceModule(null) }) {
                    Text("Clear override")
                }
            }
            Spacer(Modifier.height(8.dp))
            active!!.Screen()
        }
    }
}
