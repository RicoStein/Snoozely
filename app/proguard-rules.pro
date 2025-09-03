########################################
# Allgemein: Logging & Annotations
########################################
# Behalte häufig genutzte Annotationen
-keepattributes *Annotation*
# Hilft bei Kotlin-Reflection/Compose-Metadaten
-keepattributes InnerClasses,EnclosingMethod,Signature,LineNumberTable,SourceFile

# Behalte Logging-Methoden-Aufrufe (optional – nur wenn du Logs im Release brauchst)
# Entferne sie sonst per -assumenosideeffects (s. unten, Variante B)
# -keep class android.util.Log { *; }

########################################
# Jetpack Compose (häufig nötig)
########################################
# Compose runtime/preview metadata
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin Coroutines und stdlib
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**
-keep class kotlin.** { *; }

########################################
# Google Play Billing
########################################
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

########################################
# Google Mobile Ads (AdMob)
########################################
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.internal.ads.** { *; }   # Adapter/mediation nutzen oft interne Klassen
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.gms.internal.ads.**

########################################
# UMP (User Messaging Platform)
########################################
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

########################################
# Play Services allgemein
########################################
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

########################################
# Material, AppCompat, AndroidX (vorsorglich)
########################################
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }

-dontwarn androidx.**
-keep class androidx.appcompat.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.datastore.** { *; }

########################################
# Dein Code, der per Reflection/Bundle-Keys genutzt wird
########################################
# Falls du irgendwo Class.forName() o. ä. nutzt, entsprechende Klassen whitelisten.
# Beispiel: Services/Receiver, die vom System per Name geladen werden:
-keep class com.tigonic.snoozely.service.** { *; }
-keep class com.tigonic.snoozely.widget.** { *; }
-keep class com.tigonic.snoozely.util.ScreenOffAdminReceiver { *; }

# Falls BuildConfig-Konstanten aus anderen Modulen gebraucht werden:
-keep class com.tigonic.snoozely.BuildConfig { *; }

########################################
# Hilt/Dagger/Mediation Adapter (falls verwendet)
########################################
# -keep class dagger.** { *; }
# -dontwarn dagger.**
# -keep class javax.inject.** { *; }
# -dontwarn javax.inject.**
# Adapters (wenn du spezifische Mediation SDKs nutzt – entsprechende Pakete ergänzen)
# -keep class com.google.ads.mediation.** { *; }
# -dontwarn com.google.ads.mediation.**

########################################
# Optional: Logging im Release entfernen (kleinere APK, aber Vorsicht bei Debugging)
########################################
# Variante A: Nur Log-Methode zu No-Op
#-assumenosideeffects class android.util.Log {
#    public static int v(...);
#    public static int d(...);
#    public static int i(...);
#    public static int w(...);
#    public static int e(...);
#}

# Variante B: Auch Timber etc. (falls genutzt)
#-assumenosideeffects class timber.log.Timber {
#    public static void d(...);
#    public static void v(...);
#    public static void i(...);
#    public static void w(...);
#    public static void e(...);
#}
