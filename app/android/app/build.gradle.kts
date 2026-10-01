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
    // the words, from i18n/strings.json (tools/build-app-strings.py): every language in test builds, only the
    // released ones in store builds
    sourceSets["debug"].res.directories.add(layout.buildDirectory.dir("generated/i18n/debug").get().asFile.path)
    sourceSets["release"].res.directories.add(layout.buildDirectory.dir("generated/i18n/release").get().asFile.path)
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

/**
 * The languages of each build. Debug builds (the emulator run, development) carry all four, including the ones
 * still waiting for a native speaker. Release builds (the GitHub test build and the store build) carry only the
 * released ones: Hebrew today. English joins when the left-to-right signs and screens pass the canvas
 * (decision 32); Russian and Georgian also need the speakers' review (i18n/REVIEW.md), and the generator refuses
 * them in a release build until then.
 */
val releaseLanguages = "he"
val debugLanguages = "he,en,ru,ka"
fun stringsTask(name: String, langs: String, unreviewed: Boolean) = tasks.registering(Exec::class) {
    val out = layout.buildDirectory.dir("generated/i18n/$name").get().asFile
    inputs.file(File(repoRoot, "i18n/strings.json"))
    inputs.file(File(repoRoot, "tools/build-app-strings.py"))
    inputs.property("langs", langs)
    outputs.dir(out)
    doFirst { out.deleteRecursively() }
    commandLine(listOf("python3", File(repoRoot, "tools/build-app-strings.py").path, out.path, langs) + if (unreviewed) listOf("--unreviewed") else emptyList())
}
val debugStrings by stringsTask("debug", debugLanguages, unreviewed = true)
val releaseStrings by stringsTask("release", releaseLanguages, unreviewed = false)
tasks.named("preBuild") { dependsOn(debugStrings, releaseStrings) }

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
