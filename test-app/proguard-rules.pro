# 保留测试APK调用的入口
-keep class com.sd.test.xlog.MinifiedLoggers { public *; }
-keep,allowoptimization public class com.sd.lib.xlog.F** { public *; }

# 测试APK也调用Kotlin运行时，保留其ABI供跨APK调用
-keep class kotlin.** { *; }
