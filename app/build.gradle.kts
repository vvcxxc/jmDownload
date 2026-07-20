plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jmdl"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jmdl"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // In-app image viewer (async loading + sampling; loads File/webp/jpg/png).
    implementation("io.coil-kt:coil-compose:2.7.0")

    // JMComic engine (API client + Android Bitmap descrambler). Pulls core + api
    // + okhttp + gson + jsoup + commons-lang3 + slf4j transitively.
    implementation("io.github.jukomu:jmcomic-android-support:1.1.7") {
        // Desktop ImageIO/AWT WebP plugin — unusable & un-dexable on Android
        // (Android decodes WebP natively via Bitmap in AndroidImageProcessor).
        exclude(group = "org.sejda.imageio", module = "webp-imageio")
        // Ancient android.jar stub (Maven "provided") — conflicts with the real SDK.
        exclude(group = "com.google.android", module = "android")
    }

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
