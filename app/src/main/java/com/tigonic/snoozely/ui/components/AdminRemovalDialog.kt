package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tigonic.snoozely.R

@Composable
fun AdminRemovalDialog(
    onDismiss: () -> Unit,
    onConfirmRemove: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = cs.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Titel
                Text(
                    text = stringResource(R.string.admin_remove_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onSurface
                )

                // Einleitung
                Text(
                    text = stringResource(R.string.admin_remove_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )

                // Hinweis
                Surface(
                    color = cs.surfaceVariant,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        modifier = Modifier.padding(12.dp),
                        text = stringResource(R.string.admin_remove_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }

                // Aktionen
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Primär: Entfernen
                    Button(
                        onClick = onConfirmRemove,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.admin_remove_confirm),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Sekundär: Abbrechen
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                }
            }
        }
    }
}
