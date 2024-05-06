plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("kotlin-kapt")
}

android {
    namespace = "com.bignerdranch.android.photogallery"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bignerdranch.android.photogallery"
        minSdk = 24
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    runtimeOnly("androidx.lifecycle:lifecycle-extensions:2.2.0")
    runtimeOnly("androidx.lifecycle:lifecycle-livedata-core:2.4.0")

    implementation ("androidx.core:core-ktx:1.0.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    // Using RecyclerView from the version catalog
    implementation(libs.androidx.recyclerview)
    // Other dependencies...
    // Using Retrofit from the version catalog
    implementation(libs.retrofit)
    // Other dependencies...
    // Dagger 2 dependencies
    implementation(libs.dagger)
    kapt(libs.dagger.compiler) // If using Kotlin, use kapt instead of annotationProcessor
    //dagger2
    implementation(libs.gson)
    implementation(libs.retrofitGsonConverter)
    implementation(libs.retrofit.scalars.converter)
    testImplementation("junit:junit:4.12")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}