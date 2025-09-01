package com.tigonic.snoozely

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.ads.MobileAds
import com.tigonic.snoozely.ads.AdsConsent
import com.tigonic.snoozely.ads.ConsentManager
import com.tigonic.snoozely.ads.InterstitialManager
import com.tigonic.snoozely.core.premium.PremiumManager
import com.tigonic.snoozely.service.TimerEngineService
import com.tigonic.snoozely.ui.NotificationSettingsScreen
import com.tigonic.snoozely.ui.components.PremiumPaywallDialog
import com.tigonic.snoozely.ui.screens.HomeScreen
import com.tigonic.snoozely.ui.screens.SettingsScreen
import com.tigonic.snoozely.ui.screens.ShakeExtendSettingsScreen
import com.tigonic.snoozely.ui.screens.ShakeStrengthScreen
import com.tigonic.snoozely.ui.screens.startForegroundServiceCompat
import com.tigonic.snoozely.ui.theme.SnoozelyTheme
import com.tigonic.snoozely.ui.theme.registerDefaultThemes
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import com.tigonic.snoozely.util.TimerPreferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG_ADS = "MainActivityAds"
private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

/**
 * App-Einstieg/Navigation:
 * - Initialisiert Ads/Consent, PremiumManager und Themes
 * - Hält InterstitialManager konditioniert auf Consent/Premium bereit
 * - Startet TimerEngineService neu, falls Timer im Hintergrund bereits läuft
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var showPaywall by mutableStateOf(false)
    private val consentManager by lazy { ConsentManager(applicationContext) }

    private var interstitialManager: InterstitialManager? = null
    private var nonPersonalized by mutableStateOf(false)
    private var isAdsAllowed by mutableStateOf(false)

    private lateinit var premiumManager: PremiumManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Google Mobile Ads
        MobileAds.initialize(this) { Log.d(TAG_ADS, "MobileAds.initialize completed") }

        installSplashScreen().apply { setKeepOnScreenCondition { viewModel.isLoading.value } }
        super.onCreate(savedInstanceState)

        // Ersteinrichtungs-Dialog für Akku-Optimierungen (nur einmal)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lifecycleScope.launch {
                val handled = SettingsPreferenceHelper.getBatteryOptPromptHandled(applicationContext).first()
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val alreadyIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
                if (!handled && !alreadyIgnoring) {
                    val ok = runCatching {
                        startActivity(Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS").apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }.isSuccess
                    SettingsPreferenceHelper.setBatteryOptPromptHandled(applicationContext, ok || true)
                }
            }
        }

        enableEdgeToEdge()

        handleIntent(intent)
        registerDefaultThemes()
        lifecycleScope.launch { maybeStartEngineIfTimerRunning() }

        // Consent/Ads laden
        lifecycleScope.launch {
            Log.d(TAG_ADS, "Requesting UMP consent...")
            val consent = consentManager.requestConsent(this@MainActivity)
            val typeString = when (consent) {
                AdsConsent.Personalized -> "Personalized"
                AdsConsent.NonPersonalized -> "NonPersonalized"
                AdsConsent.NoAds -> "NoAds"
                else -> "Unknown"
            }
            SettingsPreferenceHelper.setAdsConsent(applicationContext, resolved = true, type = typeString)
        }

        setContent {
            // Theme
            val themeId by SettingsPreferenceHelper.getThemeMode(this).collectAsState(initial = "system")
            val dynamic by SettingsPreferenceHelper.getThemeDynamic(this).collectAsState(initial = true)

            // Premium/Ads
            val premium by SettingsPreferenceHelper.getPremiumActive(this).collectAsState(initial = false)
            val consentResolved by SettingsPreferenceHelper.getAdsConsentResolved(this).collectAsState(initial = false)
            val consentType by SettingsPreferenceHelper.getAdsConsentType(this).collectAsState(initial = "Unknown")

            nonPersonalized = (consentType == "NonPersonalized") || (consentType == "NoAds")

            // Ads nur zeigen, wenn kein Premium und Consent für Ads vorliegt
            isAdsAllowed = !premium && consentResolved && (
                    consentType == "Personalized" ||
                            consentType == "NonPersonalized"
                    // Optional nur in Debug/Tests: (BuildConfig.ADGATE_ALLOW_NOADS_IN_DEBUG && consentType == "NoAds")
                    )

            // InterstitialManager lazy initialisieren und „vorwärmen“
            LaunchedEffect(isAdsAllowed, nonPersonalized) {
                if (isAdsAllowed) {
                    if (interstitialManager == null) {
                        interstitialManager = InterstitialManager(
                            appContext = applicationContext,
                            scope = lifecycleScope,
                            activityProvider = { this@MainActivity },
                            isAdsAllowed = { isAdsAllowed },
                            adUnitId = TEST_INTERSTITIAL
                        )
                    }
                    interstitialManager?.preloadIfEligible(nonPersonalized = nonPersonalized)
                }
            }

            SnoozelyTheme(themeId = themeId, dynamicColor = dynamic) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    val navController = rememberNavController()

                    LaunchedEffect(Unit) {
                        SettingsPreferenceHelper.incrementAdsOpenCounter(applicationContext)
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(200)) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) }
                    ) {
                        composable("home") {
                            HomeScreen(
                                onSettingsClick = { navController.navigate("settings") },
                                adsGateIsAllowed = isAdsAllowed,
                                adsGateIsNonPersonalized = nonPersonalized,
                                consentResolved = consentResolved,
                                consentType = consentType,
                                premium = premium,
                                onOpenPrivacyOptions = { consentManager.showPrivacyOptions(this@MainActivity) {} },
                                onRequestAdThenStart = { action -> showInterstitialThenStart(action) },
                                onRequestPurchase = { showPaywall = true }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateShakeSettings = { navController.navigate("settings/shake") },
                                onNavigateNotificationSettings = { navController.navigate("settings/notifications") }
                            )
                        }
                        composable("settings/notifications") {
                            NotificationSettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable("settings/shake") {
                            ShakeExtendSettingsScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateShakeStrength = { navController.navigate("settings/shake/strength") },
                                onPickSound = { }
                            )
                        }
                        composable("settings/shake/strength") {
                            ShakeStrengthScreen(onBack = { navController.popBackStack() })
                        }
                    }

                    if (showPaywall) {
                        PremiumPaywallDialog(
                            isPremium = premium, // zeigt Spendenbereich bei aktivem Premium
                            onClose = { showPaywall = false },
                            onPurchase = {
                                premiumManager.launchPurchase(this@MainActivity)
                                showPaywall = false
                            },
                            onDonateClick = {
                                val url = "https://buymeacoffee.com/DEIN_LINK" // TODO: eigene URL
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        )
                    }
                }
            }
        }

        premiumManager = PremiumManager(
            context = applicationContext,
            productInappId = PremiumManager.BillingConfig.PREMIUM_INAPP,
            productSubsId = null,
            onPremiumChanged = { isPremium ->
                lifecycleScope.launch { SettingsPreferenceHelper.setPremiumActive(applicationContext, isPremium) }
            }
        )
        premiumManager.start()
    }

    /**
     * Zeigt (zufällig) Interstitial und ruft danach die Aktion auf.
     */
    fun showInterstitialThenStart(onAfter: () -> Unit) {
        val roll = (1..100).random()
        val shouldShowAd = isAdsAllowed && roll > 70
        android.util.Log.d("Interstitial", "randomRoll=$roll shouldShowAd=$shouldShowAd")

        val manager = interstitialManager
        if (!shouldShowAd || manager == null) {
            onAfter(); return
        }

        manager.showOrFallback {
            android.util.Log.d("Interstitial", "After/fallback -> start now")
            onAfter()
        }
        manager.warmPreload(nonPersonalized = nonPersonalized)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let { if (it.getBooleanExtra("showPaywall", false)) this.showPaywall = true }
    }

    /**
     * Falls TimerStatus „running“: Engine-Service im Vordergrund starten (z. B. nach App-Neustart).
     */
    private suspend fun maybeStartEngineIfTimerRunning() {
        val ctx = applicationContext
        val running = TimerPreferenceHelper.getTimerRunning(ctx).first()
        val start = TimerPreferenceHelper.getTimerStartTime(ctx).first()
        val minutes = TimerPreferenceHelper.getTimer(ctx).first()

        if (running && start > 0L && minutes > 0) {
            val intent = Intent(ctx, TimerEngineService::class.java)
            ctx.startForegroundServiceCompat(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        premiumManager.restoreEntitlements()
    }

    override fun onDestroy() {
        super.onDestroy()
        premiumManager.release()
    }
}
