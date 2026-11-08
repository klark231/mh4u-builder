plugins {
    id("com.android.application")
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

    buildFeatures { buildConfig = true }

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
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true  // libzig.so has to be extracted to be exec-able
            keepDebugSymbols += "**/libzig.so"  // executable, dont strip
        }
    }

    androidResources { noCompress += listOf("zip") }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pyc { src = false }
    }
}
