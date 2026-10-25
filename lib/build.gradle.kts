import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.mavenPublish)
}

android {
  namespace = "com.sd.lib.xlog"
  compileSdk = libs.versions.androidCompileSdk.get().toInt()
  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
    // 接口的默认方法编译成Java默认方法，同时保留DefaultImpls，旧版本编译的实现类和调用方都能用
    freeCompilerArgs += "-Xjvm-default=all-compatibility"
  }

  testOptions {
    // JVM单元测试里android.util.Log等方法返回默认值，不抛异常，库内部日志libLog会用到
    unitTests.isReturnDefaultValues = true
  }
}

tasks.withType<Test>().configureEach {
  // LogInitTest 等测试会初始化 FLog 单例且无法重置，LogTest 依赖未初始化状态，所以每个测试类单独一个 JVM
  forkEvery = 1
  maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

dependencies {
  testImplementation(libs.junit)
}

mavenPublishing {
  configure(
    AndroidSingleVariantLibrary(
      variant = "release",
      sourcesJar = true,
      publishJavadocJar = true,
    )
  )
}