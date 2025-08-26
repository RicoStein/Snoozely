package com.tigonic.snoozely.ads

import android.app.Activity
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.updateLayoutParams
import com.google.android.gms.ads.*

private const val TAG = "HomeBanner"

private fun Activity.adaptiveBannerSize(): AdSize {
    @Suppress("DEPRECATION")
    val display = windowManager.defaultDisplay
    val outMetrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    display.getMetrics(outMetrics)
    val density = outMetrics.density
    val adWidthPixels = outMetrics.widthPixels.toFloat()
    val adWidth = (adWidthPixels / density).toInt()
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
}

private fun findActivity(context: android.content.Context): Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun HomeBanner(
    isAdsAllowed: Boolean,
    adUnitId: String,
    nonPersonalized: Boolean
) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        factory = { ctx ->
            if (!isAdsAllowed) {
                Log.d(TAG, "Gate blocked: isAdsAllowed=false (ui will show placeholder)")
                // Optional: sichtbarer 1px Placeholder zum Erkennen der Position
                FrameLayout(ctx).apply {
                    addView(TextView(ctx).apply {
                        text = "Ads disabled by gate"
                        alpha = 0.0f // unsichtbar, aber debugbar
                    })
                }
            } else if (activity == null) {
                Log.w(TAG, "No Activity found, cannot compute adaptive size -> skip")
                FrameLayout(ctx)
            } else {
                Log.d(TAG, "Creating AdView with unitId=$adUnitId, npa=$nonPersonalized")
                val adView = AdView(ctx).apply {
                    this.adUnitId = adUnitId
                    val size = activity.adaptiveBannerSize()
                    Log.d(TAG, "Adaptive size: ${size.width}x${size.height}")
                    this.setAdSize(size)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Log.d(TAG, "onAdLoaded")
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.e(TAG, "onAdFailedToLoad code=${error.code}, msg=${error.message}, domain=${error.domain}")
                        }
                        override fun onAdImpression() { Log.d(TAG, "onAdImpression") }
                        override fun onAdClicked() { Log.d(TAG, "onAdClicked") }
                        override fun onAdOpened() { Log.d(TAG, "onAdOpened") }
                        override fun onAdClosed() { Log.d(TAG, "onAdClosed") }
                    }
                }
                val requestBuilder = AdRequest.Builder()
                if (nonPersonalized) {
                    val extras = android.os.Bundle().apply { putString("npa", "1") }
                    Log.d(TAG, "Request extras: npa=1")
                    requestBuilder.addNetworkExtrasBundle(
                        com.google.ads.mediation.admob.AdMobAdapter::class.java,
                        extras
                    )
                }
                val req = requestBuilder.build()
                Log.d(TAG, "Loading banner request...")
                adView.loadAd(req)
                adView
            }
        },
        update = { view ->
            if (view is AdView) {
                view.updateLayoutParams<ViewGroup.LayoutParams> { /* rotation/size hook */ }
            }
        },
        onRelease = { v ->
            if (v is AdView) {
                Log.d(TAG, "Destroy AdView")
                v.destroy()
            }
        }
    )
}
