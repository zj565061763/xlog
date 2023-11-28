# 前言

目前大多数库api设计都是`Log.d("tag", "msg")`这种风格，而且支持自定义日志存储的比较少，
所以笔者想自己造一个轮子。

这种api风格有什么不好呢？

首先，它的`tag`是一个字符串，需要开发人员严格管理`tag`，要不然可能各种硬编码的`tag`满天飞。

另外，它也可能导致性能陷阱，假设有这么一段代码：

```kotlin
// 打印一个List
Log.d("tag", list.joinToString())
```

此处使用`Debug`打印日志，生产模式下调高日志等级，不打印这一行日志，但是`list.joinToString()`这一行代码仍然会被执行，有可能导致性能问题。

下文会分析笔者期望的api是什么样的，本文演示代码都是用`kotlin`，库中好用的api也是基于`kotlin`特性来实现的。

笔者写库有个习惯，对外开放的类或者全局方法都会加一个前缀`f`，一个是为了避免命名冲突，另一个是为了方便代码检索，以下文章中会出现，这里做一下解释。

# 期望

什么样的api才能解决上面的问题呢？我们看一下方法的签名和打印方式
```kotlin
inline fun <reified T : FLogger> flogD(block: () -> Any)
```
```kotlin
interface AppLogger : FLogger
```
```kotlin
flogD<AppLogger> {
    list.joinToString { it }
}
```

`flogD`方法打印`Debug`日志，传一个`Flogger`的子类`AppLogger`作为日志标识，同时传一个`block`来返回要打印的日志内容。

日志标识是一个类或者接口，所以管理方式比较简单不会造成`tag`混乱的问题，默认`tag`是日志标识类的短类名。生产模式下调高日志等级后，`block`就不会被执行了，避免了可能的性能问题。

# 实现分析
日志库的完整实现已经写好了，放在这里[xlog](https://github.com/zj565061763/xlog)

* 支持限制日志大小，例如限制每天只能写入10MB的日志
* 支持自定义日志格式
* 支持自定义日志存储，即如何持久化日志
* 支持自定义日志执行线程，包括日志格式化和写入

这一节主要分析一下实现过程中遇到的问题。

#### 问题：如果App运行期间日志文件被意外删除了，怎么处理？

在Android中，用`java.io`的api对一个文件进行写入，如果文件被删除，继续写入的话不会抛异常，导致日志丢失，该如何解决？

有同学说，在写入之前先检查文件是否存在，如果存在就继续写入，不存在就创建后写入。

检查一个文件是否存在通常是调用`java.io.File.exist()`方法，但是它比较耗性能，我们来做一个测试：

```kotlin
measureTime {
    repeat(1_0000) {
        file.exists()
    }
}.let {
    Log.i("MainActivity", "time:${it.inWholeMilliseconds}")
}
```

```
14:50:33.536 MainActivity            com.sd.demo.xlog                I  time:39
14:50:35.872 MainActivity            com.sd.demo.xlog                I  time:54
14:50:38.200 MainActivity            com.sd.demo.xlog                I  time:43
14:50:40.028 MainActivity            com.sd.demo.xlog                I  time:53
14:50:41.693 MainActivity            com.sd.demo.xlog                I  time:58
```

可以看到1万次调用的耗时在50毫秒左右。

我们再测试一下对文件写入的耗时：

```kotlin
val output = filesDir.resolve("log.txt").outputStream().buffered()
val log = "1".repeat(50).toByteArray()
measureTime {
    repeat(1_0000) {
        output.write(log)
        output.flush()
    }
}.let {
    Log.i("MainActivity", "time:${it.inWholeMilliseconds}")
}
```

```
14:57:56.092 MainActivity            com.sd.demo.xlog                I  time:38
14:57:56.558 MainActivity            com.sd.demo.xlog                I  time:57
14:57:57.129 MainActivity            com.sd.demo.xlog                I  time:57
14:57:57.559 MainActivity            com.sd.demo.xlog                I  time:46
14:57:58.054 MainActivity            com.sd.demo.xlog                I  time:54
```

可以看到1万次调用，每次写入50个字符的耗时也在50毫秒左右。如果每次写入日志前都判断一下文件是否存在，那么实际上相当于2次写入的性能成本，这显然很不划算。

还有同学说，开一个线程，定时判断文件是否存在，这样子虽然不会损耗单次写入的性能，但是又多占用了一个线程资源，显然也不符合笔者的需求。

其实Android已经给我们提供了这种场景的解决方案，那就是`android.os.MessageQueue.IdleHandler`，关于`IdleHandler`这里就不展开讨论了，简单来说就是当你注册一个`IdleHandler`后，它会在主线程空闲的时候被执行。

我们可以在每次写入日志之后注册`IdleHandler`，等`IdleHandler`被执行的时候检查一下日志文件是否存在，如果不存在就关闭输出流，这样子在下一次写入的时候就会重新创建文件写入了。

这里要注意每次写入日志之后注册`IdleHandler`，并不是每次都创建新对象，要判断一下如果原先的对象还未执行的话就不用注册一个新的`IdleHandler`，库中大概的代码如下：

```kotlin
private class LogFileChecker(private val block: () -> Unit) {
    private var _idleHandler: IdleHandler? = null

    fun register(): Boolean {
        // 如果当前线程没有Looper则不注册，上层逻辑可以直接检查文件是否存在，因为是非主线程
        Looper.myLooper() ?: return false
        
        // 如果已经注册过了，直接返回
        _idleHandler?.let { return true }
        
        val idleHandler = IdleHandler {
            // 执行block检查任务
            libTryRun { block() }
            
            // 重置变量，等待下次注册
            _idleHandler = null
            false
        }
        
        // 保存并注册idleHandler
        _idleHandler = idleHandler
        Looper.myQueue().addIdleHandler(idleHandler)
        return true
    }
}
```

这样子文件被意外删除之后，就可以重新创建写入了，避免丢失大量的日志。

#### 问题：如何检测文件大小是否溢出

库支持对每天的日志大小做限制，例如限制每天最多只能写入10MB，每次写入日志之后都会检查日志大小是否超过限制，通常我们会调用`java.io.File.length()`方法获取文件的大小，但是它也比较耗性能，我们来做一个测试：

```kotlin
val file = filesDir.resolve("log.txt").apply {
    this.writeText("hello")
}
measureTime {
    repeat(1_0000) {
        file.length()
    }
}.let {
    Log.i("MainActivity", "time:${it.inWholeMilliseconds}")
}
```

```
16:56:04.090 MainActivity            com.sd.demo.xlog                I  time:61
16:56:05.329 MainActivity            com.sd.demo.xlog                I  time:80
16:56:06.382 MainActivity            com.sd.demo.xlog                I  time:72
16:56:07.496 MainActivity            com.sd.demo.xlog                I  time:79
16:56:08.591 MainActivity            com.sd.demo.xlog                I  time:78
```

可以看到耗时在60毫秒左右，相当于上面测试中1次文件写入的耗时。

库中支持自定义日志存储，在日志存储接口中定义了`size()`方法，上层通过此方法来判断当前日志的大小。

如果开发者自定义了日志存储，避免在此方法中每次调用`java.io.File.length()`来返回日志大小，应该维护一个表示日志大小的变量，变量初始化的时候获取一下`java.io.File.length()`，后续通过写入的数量来增加这个变量的值，并在`size()`方法中返回。库中默认的日志存储实现类就是这样实现的，有兴趣的可以看[这里](https://github.com/zj565061763/xlog/blob/main/lib/src/main/java/com/sd/lib/xlog/LogStore.kt)

#### 问题：文件大小溢出后怎么处理？

假设限制每天最多只能写入10MB，那超过10MB后如何处理？有同学说直接删掉或者清空文件，重新写入，这也是一种策略，但是会丢失之前的所有日志。

例如白天写了9.9MB，到晚上的时候写满10MB，清空之后，白天的日志都没了，这时候用户反馈白天遇到的一个bug，需要上传日志，那就芭比Q了。

有没有办法少丢失一些呢？可以把日志分多个文件存储，为了便于理解假设分为2个文件存储，一天10MB，那1个文件最多只能写入5MB。具体步骤如下：

1. 写入文件`20231128.log`
2. `20231128.log`写满5MB的时候关闭输出流，并把它重命名为`20231128.log.1`

这时候继续写日志到话，发现`20231128.log`文件不存在就会创建，又跳到了步骤1，就这样一直重复1和2两个步骤，到晚上写满10MB到时候，至少还有5MB到日志内容保存在`20231128.log.1`文件中避免丢失全部到日志。

分的文件数量越多，保留的日志就越多，实际上就是拿出一部分空间当作中转区，满了就向后递增数字重命名备份。目前库中只分为2个文件存储，暂时不开放自定义文件数量。

#### 问题：打印日志的性能

性能，是这个库最关心的问题，通常来说文件写入操作是性能开销的大头，目前是用`java.io`相关的api来实现的，怎样提高写入性能笔者也一直在探索，在demo中提供了一个基于内存映射的日志存储方案，但是稳定性未经测试，后续测试通过后可能会转正。有兴趣的读者可以看看[这里](https://github.com/zj565061763/xlog/blob/main/app/src/main/java/com/sd/demo/xlog/log/MMapLogStore.kt)。

还有一个比较影响性能的就是日志的格式化，通常要把一个时间戳转为某个日期格式，大部分人都会用`java.text.SimpleDateFormat`来格式化，用它来格式`年:月:日`的时候问题不大，但是如果要格式化`时:分:秒.毫秒`那它就比较耗性能，我们来做一个测试：

```kotlin
val format = SimpleDateFormat("HH:mm:ss.SSS")
val millis = System.currentTimeMillis()
measureTime {
    repeat(1_0000) {
        format.format(millis)
    }
}.let {
    Log.i("MainActivity", "time:${it.inWholeMilliseconds}")
}
```

```
16:05:26.920 MainActivity            com.sd.demo.xlog                I  time:245
16:05:27.586 MainActivity            com.sd.demo.xlog                I  time:227
16:05:28.324 MainActivity            com.sd.demo.xlog                I  time:212
16:05:29.370 MainActivity            com.sd.demo.xlog                I  time:217
16:05:30.157 MainActivity            com.sd.demo.xlog                I  time:193
```

可以看到1万次格式化耗时大概在200毫秒左右。

我们再用`java.util.Calendar`测试一下：

```kotlin
val calendar = Calendar.getInstance()
// 时间戳1
val millis1 = System.currentTimeMillis()
// 时间戳2
val millis2 = millis1 + 1000
// 切换时间戳标志
var flag = true
measureTime {
    repeat(1_0000) {
        calendar.timeInMillis = if (flag) millis1 else millis2
        calendar.run {
            "${get(Calendar.HOUR_OF_DAY)}:${get(Calendar.MINUTE)}:${get(Calendar.SECOND)}.${get(Calendar.MILLISECOND)}"
        }
        flag = !flag
    }
}.let {
    Log.i("MainActivity", "time:${it.inWholeMilliseconds}")
}
```
```
16:11:25.342 MainActivity            com.sd.demo.xlog                I  time:35
16:11:26.209 MainActivity            com.sd.demo.xlog                I  time:35
16:11:27.316 MainActivity            com.sd.demo.xlog                I  time:37
16:11:28.057 MainActivity            com.sd.demo.xlog                I  time:25
16:11:28.825 MainActivity            com.sd.demo.xlog                I  time:18

```

这里解释一下为什么要用两个时间戳，因为`Calendar`内部有缓存，如果用同一个时间戳测试的话，没办法评估它真正的性能，所以这里每次格式化之后就切换到另一个时间戳，避免缓存影响测试。

可以看到1万次的格式化耗时在30毫秒左右，差距很大。

如果要自定义日志格式的话，建议用`Calendar`来格式化时间，有更好的方案欢迎和笔者交流。

#### 问题：日志的格式如何显示

手机的存储资源是宝贵的，如何定义日志格式也是一个比较重要的细节。

* 优化时间显示

目前库内部是以天为单位来命名日志文件的，例如：`20231128.log`，所以在格式化时间戳的时候只保留了`时:分:秒.毫秒`，避免冗余显示当天的日期。

* 优化日志等级显示

打印的时候提供了4个日志等级：`Debug, Info, Warning, Error`，一般最常用的记录等级是`Info`，所以在格式化的时候如果等级是`Info`则不显示等级标志，规则如下：
```kotlin
private fun FLogLevel.displayName(): String {
    return when (this) {
        FLogLevel.Verbose -> "V"
        FLogLevel.Debug -> "D"
        FLogLevel.Warning -> "W"
        FLogLevel.Error -> "E"
        else -> ""
    }
}
```

* 优化日志标识显示

如果连续2条或多条日志都是同一个日志标识，那么就只有第1条日志会显示日志`tag`

* 优化线程ID显示

如果是主线程的话，不显示线程ID，只有非主线程才显示线程ID

经过上面的优化之后，日志打印的格式是这样的：

```kotlin
flogI<AppLogger> { "1" }
flogI<AppLogger> { "2" }
flogW<AppLogger> { "3" }
flogI<UserLogger> { "user debug" }
thread {
    flogI<UserLogger> { "thread" }
}
```

```
19:19:43.961[AppLogger] 1
19:19:43.974 2
19:19:43.975[W] 3
19:19:43.976[UserLogger] user debug
19:19:43.977[12578] thread
```

# API

这一节介绍一下库的API



#### 常用方法

```kotlin
// 打开日志，默认只打开文件日志
FLog.open(
    //（必传参数）日志等级 All, Verbose, Debug, Info, Warning, Error
    level = FLogLevel.All,

    //（必传参数）日志文件目录，日志文件名称为当天的日期，例如：20231125.log
    directory = filesDir.resolve("app_log"),

    //（必传参数）限制每天日志文件大小(单位MB)，小于等于0表示不限制大小
    limitMBPerDay = 100,
    
    //（可选参数）自定义日志格式
    formatter = AppLogFormatter(),
    
    //（可选参数）自定义日志存储
    storeFactory = AppLogStoreFactory(),
    
    //（可选参数）自定义执行线程，包括日志的格式化和写入，默认在调用线程执行
    executor = AppLogExecutor(),
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
````
#### 打印日志
```kotlin
interface AppLogger : FLogger
```
```kotlin
flogV<AppLogger> { "Verbose" }
flogD<AppLogger> { "Debug" }
flogI<AppLogger> { "Info" }
flogW<AppLogger> { "Warning" }
flogE<AppLogger> { "Error" }

// 打印控制台日志，不会写入到文件中，不需要指定日志标识，tag：DebugLogger
fDebug { "console debug log" }
```

#### 配置日志标识

可以通过`FLog.config`方法修改某个日志标识的配置信息，例如下面的代码：

```kotlin
FLog.config<AppLogger> {
    // 修改日志等级
    this.level = FLogLevel.Debug

    // 修改tag
    this.tag = "AppLoggerAppLogger"
}
```

#### 自定义日志格式

```kotlin
class AppLogFormatter : FLogFormatter {
    override fun format(record: FLogRecord): String {
        // 自定义日志格式
        return record.msg
    }
}
```

```kotlin
interface FLogRecord {
    /** 日志标识 */
    val logger: Class<out FLogger>

    /** 日志tag */
    val tag: String

    /** 日志内容 */
    val msg: String

    /** 日志等级 */
    val level: FLogLevel

    /** 日志生成的时间戳 */
    val millis: Long

    /** 日志是否在主线程生成 */
    val isMainThread: Boolean

    /** 日志生成的线程ID */
    val threadID: String
}
```

#### 自定义日志存储

日志存储是通过`FLogStore`接口实现的，每一个`FLogStore`对象负责管理一个日志文件。
所以需要提供一个`FLogStore.Factory`工厂为每个日志文件提供`FLogStore`对象。

```kotlin
class AppLogStoreFactory : FLogStore.Factory {
    override fun create(file: File): FLogStore {
        return AppLogStore(file)
    }
}
```

```kotlin
class AppLogStore(file: File) : FLogStore {
    // 添加日志
    override fun append(log: String) {}

    // 返回当前日志的大小
    override fun size(): Long = 0

    // 关闭
    override fun close() {}
}
```

#### 自定义日志线程

打印日志默认在调用线程执行，可以通过`FLogExecutor`接口自定义执行线程

```kotlin
class AppLogExecutor(private val debug: Boolean) : FLogExecutor {
    private var _executor: ExecutorService? = null

    override fun submit(task: Runnable) {
        if (debug) {
            // debug模式下直接执行
            task.run()
        } else {
            // release模式下异步执行
            val executor = _executor ?: Executors.newSingleThreadExecutor().also {
                _executor = it
            }
            executor.submit(task)
        }
    }

    override fun close() {
        _executor?.shutdown()
        _executor = null
    }
}
```

```kotlin
/**
 * 日志执行器，可以定义执行线程，包括日志的格式化和写入
 */
interface FLogExecutor {
    /**
     * 提交任务，库中可以保证按顺序提交任务，
     * 开发者应该保证按顺序执行任务，否则会有先后顺序的问题
     */
    fun submit(task: Runnable)

    /**
     * 关闭
     */
    fun close()
}
```