plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.realwearv6"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.realwearv6"
        minSdk = 27
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures{
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("androidx.camera:camera-camera2:1.1.0-alpha10")
    implementation ("androidx.camera:camera-lifecycle:1.1.0-alpha10")
    implementation ("androidx.camera:camera-view:1.0.0-alpha10")


    implementation ("com.github.pedroSG94.rtmp-rtsp-stream-client-java:rtplibrary:2.2.4")
    implementation ("com.google.guava:guava:31.1-jre") // Latest stable version

    // Exo Player
    implementation ("androidx.media3:media3-exoplayer:1.3.1")
    implementation ("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation ("androidx.media3:media3-ui:1.3.1")

    implementation ("com.arthenica:ffmpeg-kit-full:6.0-2")

    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")

    implementation ("io.socket:socket.io-client:2.0.1")
    implementation ("org.json:json:20211205")

    // Java language implementation
    implementation ("androidx.fragment:fragment:$1.8.5")
    // Kotlin
    implementation ("androidx.fragment:fragment-ktx:$1.8.5")

    implementation ("com.google.android.material:material:1.9.0")



    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.navigation:navigation-dynamic-features-fragment:2.3.5")

    implementation("com.squareup.okhttp3:okhttp:4.5.0")

}