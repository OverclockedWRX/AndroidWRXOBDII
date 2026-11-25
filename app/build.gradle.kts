import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.wrxhud"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wrxhud"
        minSdk = 35
        targetSdk = 36
        versionCode = 37
        versionName = "0.36.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // android.util.Log in poller code must not throw under plain JVM unit tests.
        unitTests.isReturnDefaultValues = true
        // Robolectric needs merged resources/manifest for the Compose screenshot tests.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("wrx.shots", providers.gradleProperty("wrxShots").getOrElse("verify"))
            it.systemProperty("wrx.shots.dir", file("src/test/screenshots").absolutePath)
            it.systemProperty("wrx.shots.report", layout.buildDirectory.file("screenshot-report.txt").get().asFile.absolutePath)
            it.maxHeapSize = "2g"
            // Robolectric SDK 35 needs a Java 21 runtime; compilation stays on 17.
            it.javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

baselineProfile {
    // The profile shipped today is hand-written (src/main/baseline-prof.txt); the :baselineprofile
    // generator needs an emulator/device, so it never runs as part of a normal build.
    automaticGenerationDuringBuild = false
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    // Android Auto (template instrument HUD)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    // USB serial GPS dongles (FTDI / CP210x / CH340 / CDC ACM / etc.)
    implementation(libs.usb.serial)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
