# minified 构建专用，只给 instrumented 测试用，release 不包含这个文件

# 测试 APK 会直接引用 app 和 lib 的类和方法，不能被裁掉；允许重命名，测试 APK 会套用 mapping
# ObfuscationLoggers 除外，它要被 R8 当作普通类处理（可以移除或内联），LogObfuscationTest 靠它验证混淆后的默认 tag
-keep,allowobfuscation class !com.sd.demo.xlog.log.ObfuscationLoggers,com.sd.demo.xlog.** { *; }
-keep,allowobfuscation class com.sd.lib.xlog.** { *; }

# AGP 会把 app 已有的依赖从测试 APK 里去掉，运行时由 app 提供，所以 app 混淆时不能裁掉它们
-keep class androidx.tracing.** { *; }
-keep class androidx.concurrent.** { *; }
-keep class androidx.annotation.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
-keep class org.jetbrains.annotations.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepattributes *Annotation*
