package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tigonic.snoozely.R

@Composable
fun PremiumPaywallDialog(
    isPremium: Boolean = false,
    onClose: () -> Unit,
    onPurchase: () -> Unit,
    onDonate: (Int) -> Unit = {},
    onDonateClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onClose) {
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
                Text(
                    text = stringResource(R.string.premium_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onSurface
                )

                Text(
                    text = if (isPremium) stringResource(R.string.premium_benefits_title_active)
                    else stringResource(R.string.premium_benefits_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )

                val benefits = listOf(
                    stringResource(R.string.premium_benefit_widget),
                    stringResource(R.string.premium_benefit_no_ads),
                    stringResource(R.string.premium_benefit_support)
                )
                benefits.forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = cs.onSurface)
                    }
                }

                if (!isPremium) {
                    // Kauf-CTA
                    Button(
                        onClick = onPurchase,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(Icons.Filled.Payment, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.premium_buy_with_google_play))
                    }
                } else {
                    // Spendenbereich
                    OutlinedCard(
                        colors = CardDefaults.outlinedCardColors(containerColor = cs.surface),
                        shape = MaterialTheme.shapes.large,
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = cs.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.donate_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.onSurface
                                )
                            }
                            Text(
                                text = stringResource(R.string.premium_thanks),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant
                            )
                            Button(
                                onClick = { onDonateClick?.invoke() ?: onDonate(0) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Text(stringResource(R.string.donate))
                            }
                        }
                    }
                }

                // Schließen
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = onClose,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
