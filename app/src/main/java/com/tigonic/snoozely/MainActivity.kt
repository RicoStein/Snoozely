package com.tigonic.snoozely

import android.content.Intent
import android.os.Bundle
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

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var showPaywall by mutableStateOf(false)
    private val consentManager by lazy { ConsentManager(applicationContext) }

    private var interstitialManager: InterstitialManager? = null
    private var nonPersonalized by mutableStateOf(false)
    private var isAdsAllowed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        MobileAds.initialize(this) {
            Log.d(TAG_ADS, "MobileAds.initialize completed")
        }

        installSplashScreen().apply { setKeepOnScreenCondition { viewModel.isLoading.value } }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)
        registerDefaultThemes()
        lifecycleScope.launch { maybeStartEngineIfTimerRunning() }

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
            val themeId by SettingsPreferenceHelper.getThemeMode(this).collectAsState(initial = "system")
            val dynamic by SettingsPreferenceHelper.getThemeDynamic(this).collectAsState(initial = true)

            val premium by SettingsPreferenceHelper.getPremiumActive(this).collectAsState(initial = false)
            val consentResolved by SettingsPreferenceHelper.getAdsConsentResolved(this).collectAsState(initial = false)
            val consentType by SettingsPreferenceHelper.getAdsConsentType(this).collectAsState(initial = "Unknown")

            nonPersonalized = (consentType == "NonPersonalized") || (consentType == "NoAds")
            isAdsAllowed = !premium && consentResolved && (
                    consentType == "Personalized" || consentType == "NonPersonalized" ||
                            (BuildConfig.ADGATE_ALLOW_NOADS_IN_DEBUG && consentType == "NoAds")
                    )

            LaunchedEffect(isAdsAllowed, nonPersonalized) {
                if (isAdsAllowed) {
                    if (interstitialManager == null) {
                        interstitialManager = InterstitialManager(
                            appContext = applicationContext,
                            scope = lifecycleScope,            // WICHTIG: stabiler Scope
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
                                onRequestAdThenStart = { action -> showInterstitialThenStart(action) } // übergebe Lambda
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
                            onClose = { showPaywall = false },
                            onPurchase = {
                                lifecycleScope.launch {
                                    SettingsPreferenceHelper.setPremiumActive(applicationContext, true)
                                }
                                showPaywall = false
                            }
                        )
                    }
                }
            }
        }
    }

    fun showInterstitialThenStart(onAfter: () -> Unit) {
        Log.d("Interstitial", "showInterstitialThenStart invoked")
        val manager = interstitialManager
        val allowed = isAdsAllowed
        Log.d("Interstitial", "manager=${manager!=null} allowed=$allowed")
        if (manager == null || !allowed) {
            Log.d("Interstitial", "No manager or not allowed -> start now")
            onAfter()
            return
        }
        // Direkt aufrufen, kein zusätzliches launch – der Manager kümmert sich
        manager.showOrFallback {
            Log.d("Interstitial", "After/fallback -> start now")
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
}
