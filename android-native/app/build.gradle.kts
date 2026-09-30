import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pini.gudauri"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.pini.gudauri"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    val uploadProperties=rootProject.file("signing.properties")
    if(uploadProperties.exists()) {
        val credentials=Properties().apply { uploadProperties.inputStream().use { load(it) } }
        signingConfigs.create("upload") {
            storeFile=file(credentials.getProperty("storeFile"))
            storePassword=credentials.getProperty("storePassword")
            keyAlias=credentials.getProperty("keyAlias")
            keyPassword=credentials.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            signingConfig=signingConfigs.findByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.systemProperty("gudauri.dataDir",rootProject.file("../site/data").absolutePath) }
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/siteAssets"))
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

// Read-only copy: the web project remains the data source. Never write into site/.
val copySiteData by tasks.registering(Sync::class) {
    from("../../site/data") { include("*.json"); into("data") }
    into(layout.buildDirectory.dir("generated/siteAssets"))
}
tasks.named("preBuild").configure { dependsOn(copySiteData) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
