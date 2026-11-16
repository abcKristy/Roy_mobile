import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.rustore.push)

    implementation(libs.compose.ui)
    implementation(libs.compose.uiToolingPreview)
    debugRuntimeOnly(libs.compose.uiTooling)
    // PreviewActivity uses Material3 to switch between parameterized previews.
    debugRuntimeOnly(libs.compose.previewMaterial3)
}

android {
    namespace = "com.example.roy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        val pushProject = providers.gradleProperty("royRuStoreProjectId").orElse("").get()
        require(pushProject.matches(Regex("[A-Za-z0-9_-]*"))) { "Invalid RuStore project ID" }
        buildConfigField("String", "RUSTORE_PROJECT_ID", "\"$pushProject\"")
        applicationId = "com.example.roy"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
