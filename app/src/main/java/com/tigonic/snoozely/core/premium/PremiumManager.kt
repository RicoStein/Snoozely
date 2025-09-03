package com.tigonic.snoozely.core.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PremiumManager(
    context: Context,
    private val productInappId: String = BillingConfig.PREMIUM_INAPP,
    private val productSubsId: String? = null,
    private val onPremiumChanged: (Boolean) -> Unit = {},
    private val donationProductIds: List<String> = BillingConfig.DONATION_INAPPS
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var billingClient: BillingClient? = null
    private var productDetailsInapp: ProductDetails? = null
    private var productDetailsSubs: ProductDetails? = null

    private val donationDetails = mutableMapOf<String, ProductDetails>()
    val donationDetailsSnapshot: Map<String, ProductDetails> get() = donationDetails.toMap()

    private val _isPremium = MutableStateFlow(false)
    val isPremium = _isPremium.asStateFlow()

    fun start() {
        if (billingClient != null) return
        billingClient = BillingClient.newBuilder(appContext)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts() // INAPP
                    .build()
            )
            .setListener(this)
            .build()

        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProductDetails()
                        restoreEntitlements()
                    }
                }
            }
            override fun onBillingServiceDisconnected() {
                // Reconnect erfolgt beim nächsten Bedarf
            }
        })
    }

    fun release() {
        billingClient?.endConnection()
        billingClient = null
    }

    private suspend fun queryProductDetails() {
        val client = billingClient ?: return
        val products = mutableListOf<QueryProductDetailsParams.Product>()

        // Premium INAPP
        products += QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productInappId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        // SUBS optional
        productSubsId?.let {
            products += QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        // Spenden INAPPs
        donationProductIds.forEach { pid ->
            products += QueryProductDetailsParams.Product.newBuilder()
                .setProductId(pid)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        val result = client.queryProductDetails(params)

        productDetailsInapp = result.productDetailsList?.firstOrNull { it.productId == productInappId }
        productDetailsSubs = result.productDetailsList?.firstOrNull { it.productId == productSubsId }

        donationDetails.clear()
        result.productDetailsList.orEmpty()
            .filter { it.productType == BillingClient.ProductType.INAPP && donationProductIds.contains(it.productId) }
            .forEach { donationDetails[it.productId] = it }
    }

    fun launchPurchase(activity: Activity, preferSubscription: Boolean = false) {
        val client = billingClient ?: return

        if (productDetailsInapp == null && (productSubsId == null || productDetailsSubs == null)) {
            scope.launch { queryProductDetails() }
        }

        val pd = when {
            preferSubscription && productDetailsSubs != null -> productDetailsSubs
            else -> productDetailsInapp
        } ?: productDetailsInapp ?: productDetailsSubs ?: return

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .apply {
                if (pd.productType == BillingClient.ProductType.SUBS) {
                    val offerToken = pd.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    if (offerToken != null) setOfferToken(offerToken)
                }
            }.build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        client.launchBillingFlow(activity, flowParams)
    }

    fun launchDonation(activity: Activity, productId: String) {
        val client = billingClient ?: return
        val pd = donationDetails[productId] ?: return

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        client.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restoreEntitlements()
            else -> { /* cancel/fehler */ }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Premium freischalten, wenn Premium-Produkt dabei ist
        if (purchase.products.contains(productInappId)) {
            grantPremium(true)
        }
        // Spenden: keine Feature-Änderung (optional: Danke-Logik)

        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(params) { /* no-op */ }
        }
    }

    fun restoreEntitlements() {
        val client = billingClient ?: return

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { r1, inapps ->
            val ownsInapp = r1.responseCode == BillingClient.BillingResponseCode.OK &&
                    inapps.any {
                        it.products.contains(productInappId) &&
                                it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }

            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { r2, subs ->
                val ownsSubs = r2.responseCode == BillingClient.BillingResponseCode.OK &&
                        subs.any {
                            productSubsId != null &&
                                    it.products.contains(productSubsId) &&
                                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                        }

                grantPremium(ownsInapp || ownsSubs)
            }
        }
    }

    private fun grantPremium(enabled: Boolean) {
        if (_isPremium.value == enabled) return
        _isPremium.value = enabled
        onPremiumChanged(enabled)
    }

    object BillingConfig {
        const val PREMIUM_INAPP = "snoozely_premium"
        const val PREMIUM_SUBS = "premium_subscription"
        // Passe die IDs an deine Play-Console-Produkte an:
        val DONATION_INAPPS = listOf("snoozely_donate_small", "snoozely_donate_medium", "snoozely_donate_large", "snoozely_donate_extra")
    }
}
