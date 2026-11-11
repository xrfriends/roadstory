plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.roadstory.pleos"
    compileSdk = 34
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.roadstory.pleos"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val apiBaseUrl = providers.gradleProperty("ROAD_STORY_API_BASE_URL").orElse("http://10.0.2.2:8080")
        // Read build properties at runtime so Kotlin incremental builds cannot inline a previous value.
        buildConfigField("String", "API_BASE_URL", "String.valueOf(\"${apiBaseUrl.get()}\")")
        buildConfigField("boolean", "DEV_LIBRARY_SYNC", "Boolean.parseBoolean(\"false\")")
    }

    flavorDimensions += "platform"
    productFlavors {
        create("fake") {
            dimension = "platform"
            applicationIdSuffix = ".fake"
            buildConfigField("boolean", "PLEOS_SDK_ENABLED", "false")
        }
        create("pleos") {
            dimension = "platform"
            buildConfigField("boolean", "PLEOS_SDK_ENABLED", "true")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = false }
    buildTypes {
        getByName("debug") {
            val enabled = providers.gradleProperty("ROAD_STORY_DEV_SYNC").orNull == "true"
            buildConfigField("boolean", "DEV_LIBRARY_SYNC", "Boolean.parseBoolean(\"$enabled\")")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    "pleosImplementation"("ai.pleos.playground:Vehicle:2.0.3")
    "pleosImplementation"("ai.pleos.playground:NaviHelper:2.0.3")
    "pleosImplementation"("ai.pleos.playground:TextToSpeech:2.1.5.1")
    "pleosImplementation"("ai.pleos.playground:FusedLocation:1.1.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}
