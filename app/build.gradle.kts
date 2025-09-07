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
        versionCode = 12
        versionName = "1.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    bundle {
        language {
            enableSplit = false
        }
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
    implementation(platform(libs.androidx.compose.bom.v20250100))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.accompanist.navigation.animation)
    implementation(libs.androidx.core.splashscreen)

    // Google Mobile Ads SDK - einheitlich
    implementation(libs.play.services.ads)
    // UMP (User Messaging Platform)
    implementation(libs.user.messaging.platform)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)

    // Google Play Billing
    implementation(libs.billing.ktx)

}
