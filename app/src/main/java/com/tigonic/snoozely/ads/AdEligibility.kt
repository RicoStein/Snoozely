package com.tigonic.snoozely.ads

data class AdGate(
    val isPremium: Boolean,
    val consent: AdsConsent,
) {
    val isAdsAllowed: Boolean
        get() = !isPremium && (consent == AdsConsent.Personalized || consent == AdsConsent.NonPersonalized)
}
