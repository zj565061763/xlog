# Gradle

[![](https://jitpack.io/v/zj565061763/xlog.svg)](https://jitpack.io/#zj565061763/xlog)

# 常用方法

```kotlin
// 打开日志，默认只打开文件日志
FLog.open(
    // 日志等级 Debug, Info, Warning, Error
    level = FLogLevel.Debug,

    // 日志文件目录，日志文件名称为当天的日期，例如：20231125.log
    directory = filesDir.resolve("app_log"),

    // 限制每天日志文件大小(单位MB)，小于等于0表示不限制大小
    limitMBPerDay = 100,
)


// 是否打打印控制台日志
FLog.enableConsoleLog(false)


/**
 * 删除日志，参数saveDays表示要保留的日志天数，小于等于0表示删除全部日志，
 * 此处saveDays=1表示保留1天的日志，即保留当天的日志
 */
FLog.deleteLog(1)


// 关闭日志
FLog.close()
```

# 打印日志

打印日志需要指定日志标识，例如下面代码中的`AppLogger`标识。

```kotlin
/**
 * 定义一个日志标识，默认tag为短类名：AppLogger
 */
interface AppLogger : FLogger
```

Kotlin:

```kotlin
flogD<AppLogger> { "debug" }
flogI<AppLogger> { "info" }
flogW<AppLogger> { "warning" }
flogE<AppLogger> { "error" }

// 打印控制台日志，不会写入到文件中，不需要指定日志标识，tag：DebugLogger
fDebug { "console debug log" }
```

Java:

```java
FLog.logD(AppLogger.class,"debug");
FLog.logI(AppLogger.class,"Info");
FLog.logW(AppLogger.class,"Warning");
FLog.logE(AppLogger.class,"Error");

// 打印控制台日志，不会写入到文件中，不需要指定日志标识，tag：DebugLogger
FLog.debug("console debug log");
```

# 配置日志标识

可以通过`FLog.config`方法修改某个日志标识的配置信息，例如下面的代码：

```kotlin
FLog.config<AppLogger> {
    // 修改日志等级
    this.level = FLogLevel.Debug

    // 修改tag
    this.tag = "AppLoggerAppLogger"
}
```

# 自定义日志格式

可以在打开日志的时候传入一个`FLogFormatter`接口的对象，该接口负责格式化日志记录：[FLogRecord](https://github.com/zj565061763/xlog/blob/main/lib/src/main/java/com/sd/lib/xlog/LogRecord.kt)

```kotlin
interface FLogFormatter {
    /**
     * 格式化
     */
    fun format(record: FLogRecord): String
}
```

```kotlin
class AppLogFormatter : FLogFormatter {
    override fun format(record: FLogRecord): String {
        // 自定义日志格式
        return record.msg
    }
}
```

```kotlin
FLog.open(
    // 自定义日志格式
    formatter = AppLogFormatter()

    //...
)
```

# 自定义日志存储

日志存储是通过`FLogStore`接口实现的，每一个`FLogStore`对象负责管理一个日志文件。
所以自定义日志存储，需要在打开日志的时候，传入一个`FLogStore.Factory`工厂，负责为每个日志文件提供一个`FLogStore`对象。

```kotlin
 /**
 * 日志仓库
 */
interface FLogStore {
    /**
     * 添加日志
     */
    @Throws(Exception::class)
    fun append(log: String)

    /**
     * 日志大小(单位B)
     */
    fun size(): Long

    /**
     * 关闭
     */
    @Throws(Exception::class)
    fun close()

    /**
     * 日志仓库工厂
     */
    fun interface Factory {
        /**
         * 创建[file]对应的日志仓库
         */
        fun create(file: File): FLogStore
    }
}

```

```kotlin
class AppLogStoreFactory : FLogStore.Factory {
    override fun create(file: File): FLogStore {
        return AppLogStore(file)
    }
}

class AppLogStore(file: File) : FLogStore {
    override fun append(log: String) {}

    override fun size(): Long = 0

    override fun close() {}
}
```

```kotlin
FLog.open(
    // 自定义日志仓库
    storeFactory = AppLogStoreFactory()

    //...
)
```