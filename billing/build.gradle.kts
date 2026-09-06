plugins {
    alias(conventions.plugins.convention.android.library)
    alias(conventions.plugins.convention.publishing)
}

android {
    namespace = "de.charlex.billing"

    defaultConfig {
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

mavenPublishConfig {
    name = "billing-suspend"
    description = "Suspendable Helper for access BillingClient. It also includes a client-side token validation, if there is not server available"
    artifactId = "billing-suspend"

    developers {
        developer {
            id = "ch4rl3x"
            name = "Alexander Karkossa"
            email = "alexander.karkossa@googlemail.com"
        }
    }
}

dependencies {
    api(libs.android.billingclient.billing)
    api(libs.android.billingclient.billing.ktx)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
