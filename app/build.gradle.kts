plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.sd.demo.xlog"
  compileSdk = libs.versions.androidCompileSdk.get().toInt()
  defaultConfig {
    targetSdk = libs.versions.androidCompileSdk.get().toInt()
    minSdk = 21
    applicationId = "com.sd.demo.xlog"
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testProguardFiles("proguard-test-rules.pro")
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  // instrumented 测试跑在 R8 混淆后的 minified 构建上，验证 lib 的混淆规则和默认 tag
  testBuildType = "minified"

  signingConfigs {
    create("release") {
      storeFile = file("template.jks")
      storePassword = "template"
      keyAlias = "template"
      keyPassword = "template"
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs["release"]
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    // 只给 instrumented 测试用：在 debug 基础上开启 R8，额外保留测试 APK 依赖 app 提供的类
    create("minified") {
      initWith(getByName("debug"))
      // debuggable 的构建 AGP 会加 -dontobfuscate，关掉才会真正混淆
      isDebuggable = false
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-minified-rules.pro")
      matchingFallbacks += "debug"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }

  buildFeatures {
    viewBinding = true
  }
}

dependencies {
  implementation(libs.androidx.appcompat)

  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)

  implementation(project(":lib"))
}