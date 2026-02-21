plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.carstensen.parcelcam"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.carstensen.parcelcam"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // For calling app: pass base name etc via Intent extras
        // See docs in README.md
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    
packaging {
    resources {
        // BouncyCastle jars (via SSHJ) contain duplicate OSGI manifests
        // Use pickFirst for the exact duplicate, and exclude common variants
        pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        excludes += "META-INF/versions/**/OSGI-INF/MANIFEST.MF"
        excludes += "META-INF/*.kotlin_module"
        excludes += "META-INF/DEPENDENCIES"
        excludes += "META-INF/LICENSE*"
        excludes += "META-INF/NOTICE*"
    }
}

}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // EXIF helper
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Networking libs (choose based on method)
    // SMB: jcifs-ng
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    // FTP/FTPS: Apache Commons Net
    implementation("commons-net:commons-net:3.10.0")

    // SFTP: SSHJ
    implementation("com.hierynomus:sshj:0.39.0")
}
