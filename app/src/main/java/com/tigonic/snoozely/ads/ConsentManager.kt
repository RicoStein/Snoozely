package com.tigonic.snoozely.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "Consent"

enum class AdsConsent {
    Unknown, Personalized, NonPersonalized, NoAds
}

class ConsentManager(
    private val appContext: Context
) {
    private val consentInformation by lazy { UserMessagingPlatform.getConsentInformation(appContext) }

    suspend fun requestConsent(activity: Activity): AdsConsent = withContext(Dispatchers.Main) {
        val params = ConsentRequestParameters.Builder()
            .build()

        val result = CompletableDeferred<AdsConsent>()

        Log.d(TAG, "Requesting consent info update...")
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                Log.d(TAG, "Consent info updated. formAvailable=${consentInformation.isConsentFormAvailable}")
                if (consentInformation.isConsentFormAvailable) {
                    Log.d(TAG, "Loading consent form...")
                    UserMessagingPlatform.loadConsentForm(
                        activity,
                        { form ->
                            Log.d(TAG, "Consent form loaded. Showing...")
                            form.show(activity) { formError: FormError? ->
                                formError?.let { Log.w(TAG, "Form closed with error: code=${it.errorCode}, msg=${it.message}") }
                                val mapped = mapStatusToConsent(consentInformation)
                                Log.d(TAG, "Form closed, mapped status=$mapped")
                                result.complete(mapped)
                            }
                        },
                        { err: FormError? ->
                            Log.w(TAG, "Failed to load form: code=${err?.errorCode}, msg=${err?.message}")
                            result.complete(mapStatusToConsent(consentInformation))
                        }
                    )
                } else {
                    val mapped = mapStatusToConsent(consentInformation)
                    Log.d(TAG, "No form available, mapped status=$mapped")
                    result.complete(mapped)
                }
            },
            { err: FormError? ->
                Log.e(TAG, "Consent info update failed: code=${err?.errorCode}, msg=${err?.message}")
                result.complete(AdsConsent.NoAds)
            }
        )

        result.await()
    }

    fun showPrivacyOptions(activity: Activity, onFinished: () -> Unit = {}) {
        UserMessagingPlatform.loadConsentForm(
            activity,
            { form ->
                Log.d(TAG, "Privacy options form loaded, showing")
                form.show(activity) { _ -> onFinished() }
            },
            { err -> Log.w(TAG, "Privacy options form failed: ${err?.message}"); onFinished() }
        )
    }

    fun canRequestAds(): Boolean = consentInformation.canRequestAds().also {
        Log.d(TAG, "canRequestAds=$it")
    }

    private fun mapStatusToConsent(info: ConsentInformation): AdsConsent {
        val status = info.consentStatus
        Log.d(TAG, "Mapping consent status=$status, canRequestAds=${info.canRequestAds()}")
        return when {
            !info.canRequestAds() -> AdsConsent.NoAds
            status == ConsentInformation.ConsentStatus.OBTAINED -> AdsConsent.NonPersonalized
            status == ConsentInformation.ConsentStatus.NOT_REQUIRED -> AdsConsent.Personalized
            else -> AdsConsent.Unknown
        }
    }
}
