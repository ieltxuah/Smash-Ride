plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    // alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.smash_ride"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smash_ride"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")

    // GOOGLE SIGN-IN (Base)
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // ML Kit Translate
    implementation("com.google.mlkit:translate:17.0.3")

    // AndroidX
    implementation("androidx.work:work-runtime:2.8.1")
    implementation("androidx.core:core:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Default
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.database)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}