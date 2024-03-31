# Gradle

[![](https://jitpack.io/v/zj565061763/xlog.svg)](https://jitpack.io/#zj565061763/xlog)

# About

关于本库，请看[这里](https://juejin.cn/post/7306423214493270050)

# Sample

#### 初始化

```kotlin
// 初始化
FLog.init(
    // 日志文件目录(必传参数)
    directory = filesDir.resolve("app_log"),

    // 日志个时候(可选参数)
    formatter = AppLogFormatter(),

    // 日志仓库工厂(可选参数)
    storeFactory = AppLogStoreFactory(),

    // 日志调度器(可选参数)
    dispatcher = AppLogDispatcher(),
)
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

```kotlin
/**
 * 打印控制台日志，不会写入到文件中，tag：DebugLogger，
 * 注意：此方法不受[FLog.setConsoleLogEnabled]开关限制，只受日志等级限制
 */
private fun log() {
    fDebug(FLogLevel.Verbose) { "Verbose" }
    fDebug { "Debug" }
    fDebug(FLogLevel.Info) { "Info" }
    fDebug(FLogLevel.Warning) { "Warning" }
    fDebug(FLogLevel.Error) { "Error" }
}
```

#### 常用配置

```kotlin
// 设置日志等级 All, Verbose, Debug, Info, Warning, Error, Off  默认日志等级：All
FLog.setLevel(FLogLevel.All)

// 限制每天日志文件大小(单位MB)，小于等于0表示不限制，默认不限制
FLog.setLimitMBPerDay(100)

// 设置是否打打印控制台日志，默认打开
FLog.setConsoleLogEnabled(true)

// 修改某个日志标识的配置信息
FLog.config<AppLogger> {
    this.level = FLogLevel.All
    this.tag = "AppLoggerAppLogger"
}

/**
 * 删除日志，参数saveDays表示要保留的日志天数，小于等于0表示删除全部日志，
 * 此处saveDays=1表示保留1天的日志，即保留当天的日志
 */
FLog.deleteLog(1)
```