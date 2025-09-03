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

data class DonationUiItem(
    val productId: String,
    val priceText: String
)

@Composable
fun PremiumPaywallDialog(
    isPremium: Boolean = false,
    onClose: () -> Unit,
    onPurchase: () -> Unit,
    onDonateProduct: (String) -> Unit,
    donationProducts: List<DonationUiItem> = emptyList()
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
                    Text(
                        text = stringResource(R.string.donate_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Text(
                        text = stringResource(R.string.premium_thanks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )

                    val items = donationProducts.take(4).ifEmpty {
                        listOf(
                            DonationUiItem("snoozely_donate_small", "€0,99"),
                            DonationUiItem("snoozely_donate_medium", "€2,99"),
                            DonationUiItem("snoozely_donate_large", "€4,99"),
                            DonationUiItem("snoozely_donate_extra", "€9,99")
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DonationButton(items[0], onDonateProduct, Modifier.weight(1f))
                            DonationButton(items[1], onDonateProduct, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DonationButton(items[2], onDonateProduct, Modifier.weight(1f))
                            DonationButton(items[3], onDonateProduct, Modifier.weight(1f))
                        }
                    }
                }

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

@Composable
private fun DonationButton(
    item: DonationUiItem,
    onDonateProduct: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onDonateProduct(item.productId) },
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Icon(Icons.Filled.Favorite, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(item.priceText)
    }
}
