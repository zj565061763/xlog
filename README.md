[![Maven Central](https://img.shields.io/maven-central/v/io.github.zj565061763.android/xlog)](https://central.sonatype.com/search?q=g:io.github.zj565061763.android+xlog)

# Gradle

```kotlin
implementation("io.github.zj565061763.android:xlog:$version")
```

# About

关于本库，请看[这里](https://juejin.cn/post/7306423214493270050)

# Sample

#### 初始化

```kotlin
// 初始化
FLog.init(context)
```

#### 打印日志

```kotlin
/**
 * 定义一个日志标识，默认tag为短类名：AppLogger
 */
interface AppLogger : FLogger
```

```kotlin
flogV<AppLogger> { "Verbose" }
flogD<AppLogger> { "Debug" }
flogI<AppLogger> { "Info" }
flogW<AppLogger> { "Warning" }
flogE<AppLogger> { "Error" }
```

#### 常用配置

```kotlin
// 设置日志等级 All, Verbose, Debug, Info, Warning, Error, Off  默认日志等级：All
FLog.setLevel(FLogLevel.All)

// 设置日志模式 Default，Console，Store 默认日志模式：Default，发布到控制台和仓库
FLog.setMode(FLogMode.Default)

// 限制每天日志文件大小(单位MB)，小于等于0表示不限制，默认不限制
FLog.setMaxMBPerDay(100)

/**
 * 删除日志，参数saveDays表示要保留的日志天数，小于等于0表示删除全部日志，
 * 此处saveDays=1表示保留1天的日志，即保留当天的日志
 */
FLog.deleteLog(1)
```