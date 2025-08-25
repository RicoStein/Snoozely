// core/premium/PremiumManager.kt
package com.tigonic.snoozely.core.premium

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PremiumManager {
    private val _isPremium = MutableStateFlow(false)
    val isPremium = _isPremium.asStateFlow()

    suspend fun restore(context: Context) {
        // TODO: queryPurchasesAsync
    }

    fun startPurchase(activity: Activity, onResult: (Boolean) -> Unit) {
        // TODO: BillingClient launchBillingFlow
        // onResult(true) bei Erfolg
    }

    fun setPremiumCached(v: Boolean) {
        _isPremium.value = v
    }
}
