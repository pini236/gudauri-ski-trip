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
        // usage and crash reporting (telemetry/Telemetry.kt): the keys come only from GitHub's secret store when a
        // build is made there; a build without them sends nothing
        buildConfigField("String", "POSTHOG_KEY", "\"${System.getenv("POSTHOG_KEY") ?: ""}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${System.getenv("POSTHOG_HOST") ?: "https://eu.i.posthog.com"}\"")
        buildConfigField("String", "SENTRY_DSN", "\"${System.getenv("SENTRY_DSN") ?: ""}\"")
    }

    // The keys live only in GitHub's secret store (docs/APP-NATIVE.md); local builds fall back to the debug key.
    // "preview" is signed with the test key, "store" with the upload key (Google keeps the real signing key).
    val testKeystore = System.getenv("ANDROID_TEST_KEYSTORE_PATH")
    val uploadKeystore = System.getenv("ANDROID_UPLOAD_KEYSTORE_PATH")
    signingConfigs {
        if (testKeystore != null) {
            create("test") {
                storeFile = file(testKeystore)
                storePassword = System.getenv("ANDROID_TEST_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_TEST_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_TEST_KEYSTORE_PASSWORD")
            }
        }
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD")
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
            signingConfig = signingConfigs.findByName("test") ?: signingConfigs.getByName("debug")
        }
        create("store") {
            dimension = "channel"
            resValue("string", "app_name", "סקי")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true; resValues = true; buildConfig = true }
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
    implementation("com.posthog:posthog-android:3.71.4")
    implementation("io.sentry:sentry-android-core:8.59.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
