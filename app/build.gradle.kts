plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tigonic.snoozely"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tigonic.snoozely"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // BuildConfig-Flags
            buildConfigField("Boolean", "DEBUG_UI_OVERLAY", "false")
            buildConfigField("Boolean", "ADGATE_ALLOW_NOADS_IN_DEBUG", "false")
        }
        debug {
            isMinifyEnabled = false
            // BuildConfig-Flags (Debug an)
            buildConfigField("Boolean", "DEBUG_UI_OVERLAY", "true")
            buildConfigField("Boolean", "ADGATE_ALLOW_NOADS_IN_DEBUG", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.accompanist:accompanist-navigation-animation:0.34.0")
    implementation(libs.androidx.core.splashscreen)

    // Google Mobile Ads SDK - einheitlich
    implementation("com.google.android.gms:play-services-ads:24.5.0")
    // UMP (User Messaging Platform)
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")

}
