package com.tigonic.snoozely.core.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PremiumManager(
    context: Context,
    private val productInappId: String = BillingConfig.PREMIUM_INAPP,
    private val productSubsId: String? = null,           // optional: falls Premium via Abo
    private val onPremiumChanged: (Boolean) -> Unit = {} // Callback zu deiner App
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var billingClient: BillingClient? = null
    private var productDetailsInapp: ProductDetails? = null
    private var productDetailsSubs: ProductDetails? = null

    private val _isPremium = MutableStateFlow(false)
    val isPremium = _isPremium.asStateFlow()

    fun start() {
        if (billingClient != null) return
        billingClient = BillingClient.newBuilder(appContext)
            .enablePendingPurchases(
                PendingPurchasesParams
                    .newBuilder()
                    .enableOneTimeProducts()  // für Einmalkäufe (INAPP)
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
                // Reconnect beim nächsten Aufruf von start()/Abfrage
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

        products += QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productInappId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        productSubsId?.let {
            products += QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        val result = client.queryProductDetails(params) // suspend
        productDetailsInapp = result.productDetailsList?.firstOrNull { it.productId == productInappId }
        productDetailsSubs = result.productDetailsList?.firstOrNull { it.productId == productSubsId }
    }

    fun launchPurchase(activity: Activity, preferSubscription: Boolean = false) {
        val client = billingClient ?: return
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
            }
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        client.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
        // Cancel/Fehler: nichts tun
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Optional: Serverseitig verifizieren (hier weggelassen)
        grantPremium(true)

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
                    inapps.any { it.products.contains(productInappId) && it.purchaseState == Purchase.PurchaseState.PURCHASED }

            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { r2, subs ->
                val ownsSubs = r2.responseCode == BillingClient.BillingResponseCode.OK &&
                        subs.any { productSubsId != null && it.products.contains(productSubsId) && it.purchaseState == Purchase.PurchaseState.PURCHASED }

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
        const val PREMIUM_INAPP = "premium_unlock"          // DEINE INAPP-ID
        const val PREMIUM_SUBS = "premium_subscription"     // DEINE SUBS-ID (falls genutzt)
    }
}
