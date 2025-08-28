package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.R
import com.tigonic.snoozely.ui.theme.LocalExtraColors

@Composable
fun PremiumPaywallDialog(
    isPremium: Boolean = false,
    onClose: () -> Unit,
    onPurchase: () -> Unit = {},
    onDonate: (Int) -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.premium_title), color = cs.onSurface) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    if (isPremium) stringResource(R.string.premium_benefits_title_active)
                    else stringResource(R.string.premium_benefits_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )
                Spacer(Modifier.height(8.dp))

                val benefits = listOf(
                    stringResource(R.string.premium_benefit_widget),
                    stringResource(R.string.premium_benefit_no_ads),
                    stringResource(R.string.premium_benefit_support)
                )
                benefits.forEach {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = extra.iconActive
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = cs.onSurface)
                    }
                }

                if (isPremium) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.premium_thanks),
                        color = cs.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.donate_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 5, 10).forEach { amount ->
                            OutlinedButton(onClick = { onDonate(amount) }) {
                                Text(stringResource(R.string.donate_amount_eur, amount))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isPremium) {
                Button(onClick = onPurchase) {
                    Icon(imageVector = Icons.Filled.Payment, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.premium_buy_with_google_play))
                }
            } else {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.close))
                }
            }
        },
        dismissButton = {
            if (!isPremium) {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    )
}
