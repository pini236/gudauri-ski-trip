plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val repoRoot = rootDir.parentFile.parentFile

android {
    namespace = "io.github.pini236.skiapp"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.pini236.skiapp"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "0.1-spike"
    }

    // The test key lives only in GitHub's secret store (docs/APP-NATIVE.md); local builds fall back to the debug key.
    val testKeystore = System.getenv("ANDROID_TEST_KEYSTORE_PATH")
    signingConfigs {
        if (testKeystore != null) {
            create("test") {
                storeFile = file(testKeystore)
                storePassword = System.getenv("ANDROID_TEST_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_TEST_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_TEST_KEYSTORE_PASSWORD")
            }
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        // "preview": the test build installed from GitHub, next to the store build (flavor names cannot start with "test")
        create("preview") {
            dimension = "channel"
            applicationIdSuffix = ".test"
            resValue("string", "app_name", "סקי בדיקה")
        }
        create("store") {
            dimension = "channel"
            resValue("string", "app_name", "סקי")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (testKeystore != null) signingConfigs.getByName("test") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true; resValues = true }
    testOptions { unitTests.all { it.systemProperty("site.data", File(repoRoot, "site/data").absolutePath) } }
    sourceSets["main"].assets.directories.add(layout.buildDirectory.dir("generated/siteAssets").get().asFile.path)
}

kotlin { jvmToolchain(21) }

// Read-only copy of the site's data, fonts stay in res/font. Never writes into site/.
val copySiteData by tasks.registering(Exec::class) {
    val out = layout.buildDirectory.dir("generated/siteAssets").get().asFile
    inputs.dir(File(repoRoot, "site/data"))
    inputs.dir(File(repoRoot, "site/audio"))
    inputs.file(File(repoRoot, "site/games/descent/index.html"))
    inputs.file(File(repoRoot, "tools/build-app-data.py"))
    outputs.dir(out)
    commandLine("python3", File(repoRoot, "tools/build-app-data.py").path, out.path)
}
tasks.named("preBuild") { dependsOn(copySiteData) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
