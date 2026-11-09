plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

val toolsVersion = rootProject.file("VERSION").readText().trim()
val ks = System.getenv("KEYSTORE_FILE")

android {
    namespace = "io.github.klark231.mh4ubuilder"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.klark231.mh4ubuilder"
        minSdk = 28
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "$toolsVersion-${System.getenv("GITHUB_RUN_NUMBER") ?: "local"}"
        buildConfigField("String", "TOOLS_VERSION", "\"$toolsVersion\"")
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        if (ks != null && file(ks).exists()) {
            create("release") {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint { checkReleaseBuilds = false }

    packaging {
        jniLibs {
            useLegacyPackaging = true  // libzig.so has to be extracted to be exec-able
            keepDebugSymbols += "**/libzig.so"  // executable, dont strip
        }
    }

    androidResources {
        noCompress += listOf("zip")
        generateLocaleConfig = true  // per-app language in Android 13+ settings
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pyc { src = false }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
}
