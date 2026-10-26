import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signature de l'APK : clé de l'Institut fournie par les « secrets » GitHub
// (voir LISEZMOI.md). Sans elle, l'APK est signé avec la clé de débogage.
val signature = Properties().apply {
    val f = rootProject.file("signature.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "cd.istboma.sco"
    compileSdk = 35

    defaultConfig {
        applicationId = "cd.istboma.sco"
        minSdk = 26 // Android 8.0
        targetSdk = 35 // Android 15
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
    }

    signingConfigs {
        if (signature.getProperty("storeFile") != null) {
            create("institut") {
                storeFile = rootProject.file(signature.getProperty("storeFile"))
                storePassword = signature.getProperty("storePassword")
                keyAlias = signature.getProperty("keyAlias")
                keyPassword = signature.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("institut") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Aucune bibliothèque native : le même APK marche en 32 et 64 bits.
    packaging {
        resources.excludes += setOf("DebugProbesKt.bin")
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
