package com.tigonic.snoozely.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "Interstitial"

class InterstitialManager(
    private val appContext: Context,
    private val scope: CoroutineScope,                 // übergebe lifecycleScope!
    private val activityProvider: () -> Activity?,
    private val isAdsAllowed: suspend () -> Boolean,
    private val adUnitId: String
) : DefaultLifecycleObserver {

    private var interstitial: InterstitialAd? = null
    private var loading = false

    fun preloadIfEligible(nonPersonalized: Boolean) {
        scope.launch {
            if (loading || interstitial != null || !isAdsAllowed()) {
                Log.d(TAG, "Skip preload: loading=$loading ready=${interstitial!=null}")
                return@launch
            }
            val act = activityProvider() ?: return@launch
            loading = true
            val reqBuilder = AdRequest.Builder()
            if (nonPersonalized) {
                val extras = android.os.Bundle().apply { putString("npa", "1") }
                reqBuilder.addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
            }
            Log.d(TAG, "Loading interstitial... npa=$nonPersonalized")
            InterstitialAd.load(
                act,
                adUnitId,
                reqBuilder.build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitial = ad
                        loading = false
                        Log.d(TAG, "Interstitial loaded")
                    }
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        interstitial = null
                        loading = false
                        Log.w(TAG, "Failed to load: ${adError.code} ${adError.message}")
                    }
                }
            )
        }
    }

    // Achtung: hier KEIN scope.launch – direkt ausführen (Caller ist MainActivity/Main-Thread)
    fun showOrFallback(onDismissedOrUnavailable: () -> Unit) {
        // Wir erwarten hier Main-Thread. Falls nicht, ist das dennoch okay, ad.show() fordert eine Activity und ruft intern Main auf.
        val act = activityProvider()
        val ad = interstitial

        Log.d(TAG, "Trying to show... act=${act!=null} ready=${ad!=null}")

        if (act == null || ad == null) {
            Log.d(TAG, "Unavailable -> fallback")
            onDismissedOrUnavailable()
            // fürs nächste Mal laden
            scope.launch { preloadIfEligible(nonPersonalized = false) }
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial shown")
            }
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed -> fallback")
                interstitial = null
                scope.launch { preloadIfEligible(nonPersonalized = false) }
                onDismissedOrUnavailable()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Failed to show: ${adError.code} ${adError.message} -> fallback")
                interstitial = null
                onDismissedOrUnavailable()
            }
        }
        ad.show(act)
    }

    fun warmPreload(nonPersonalized: Boolean) {
        scope.launch { preloadIfEligible(nonPersonalized) }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        interstitial = null
        loading = false
    }
}
