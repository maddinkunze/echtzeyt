plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from="map.gradle.kts")
/*build.doFirst {
    createMapTask
}*/

// TODO: cursed; in theory i should only need to add the custom maven repo to this library
//       but this works currently and gradle is too complex for me
rootProject.allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

android {
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        multiDexEnabled = true
        vectorDrawables {
            useSupportLibrary = true
        }
        resValue("string", "_libraryEchtzeytVersion", "${defaultConfig.versionName}")
        buildConfigField("String", "LIBRARY_VERSION_NAME", "\"${defaultConfig.versionName}\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    namespace = "com.maddin.echtzeyt"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra.get("kotlinVersion")}")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.startup:startup-runtime:1.2.0")

    implementation("com.github.mapsforge.vtm:vtm:0.22.0")
    implementation("com.github.mapsforge.vtm:vtm-android:0.22.0")
    runtimeOnly("com.github.mapsforge.vtm:vtm-android:0.22.0:natives-armeabi-v7a")
    runtimeOnly("com.github.mapsforge.vtm:vtm-android:0.22.0:natives-arm64-v8a")
    runtimeOnly("com.github.mapsforge.vtm:vtm-android:0.22.0:natives-x86")
    runtimeOnly("com.github.mapsforge.vtm:vtm-android:0.22.0:natives-x86_64")

    implementation(project(path=":transportapi"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}