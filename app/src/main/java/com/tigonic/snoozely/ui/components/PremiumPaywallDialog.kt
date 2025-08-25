package com.tigonic.snoozely.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    onClose: () -> Unit,
    onPurchase: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.premium_title), color = cs.onSurface) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.premium_benefits_title),
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
            }
        },
        confirmButton = {
            Button(onClick = onPurchase) {
                Icon(imageVector = Icons.Filled.Payment, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.premium_buy_with_google_play))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
