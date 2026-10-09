# 测试 APK 的混淆规则，instrumented 测试跑在 minified 构建上时 R8 也会处理测试 APK

# 测试代码原样保留，测试依赖的断言靠类名，引用的 app 类通过 mapping 对应
-dontshrink
-dontobfuscate
-dontoptimize

# 测试依赖引用了编译期注解，运行时不存在
-dontwarn javax.lang.model.element.Modifier
