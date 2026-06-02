plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.aiads"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aiads"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/\"")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // ========== AndroidX 核心库 ==========
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)

    // ========== Material Design ==========
    implementation(libs.material)

    // ========== 生命周期 ==========
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)

    // ========== 导航 ==========
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // ========== 协程 ==========
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // ========== 网络 ==========
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // ========== 图片加载 ==========
    implementation(libs.glide)

    // ========== 视频播放 ==========
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // ========== 本地数据库：使用原生 SQLiteOpenHelper，无需注解处理器 ==========

    // ========== 测试 ==========
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
