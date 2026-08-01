# xlog

Android 日志库，发布到 Maven Central（`io.github.zj565061763.android:xlog`）。核心能力：把日志同时输出到 Logcat 控制台和**按天分文件的磁盘仓库**，支持日志等级/模式过滤、单日文件大小限制、按天清理、导出 zip。当前版本见 `lib/gradle.properties` 的 `VERSION_NAME`。版本变更历史维护在 `CHANGELOG.md`（长期维护，语言简洁，只写用户可感知的内容。标题格式：`## <版本号>`，小节固定为 `### ⚠️ Breaking Changes` / `### ✨ Improvements` / `### 🐛 Bug Fixes` / `### Migration`，没有内容的小节省略）。

## 项目结构

- `lib/` — 发布的库模块，namespace `com.sd.lib.xlog`，`minSdk 21`，纯 Kotlin **无第三方依赖**（运行时依赖为零，`testImplementation junit` 只用于单元测试）。源码在 `lib/src/main/java/com/sd/lib/xlog/`，JVM 单元测试在 `lib/src/test/`。
- `app/` — 演示 App（`com.sd.demo.xlog`）。instrumented 测试在 `app/src/androidTest/`，需要设备/模拟器运行。`app/src/main/java/.../App.kt` 是初始化示例。
- Gradle 版本目录：`gradle/libs.versions.toml`（AGP 8.7.3，Kotlin 1.9.25，compileSdk 35）。

## 公共 API（对外，F 前缀）

面向用户的入口都以 `F` 开头，内部实现类无前缀：

- `FLog`（`Log.kt`）— 单例总控。`init(context)` / `setLevel` / `setMode` / `setMaxMBPerDay` / `deleteLog(saveDays)` / `logDirectory {}`。必须先 `init` 再用，否则 `checkInit()` 抛异常。
- `FLogger`（`Logger.kt`）— **空标记接口**。用户定义子接口作为“日志标识”，默认 tag = 该接口短类名。`FLoggerConfig` 可覆盖单个 logger 的 tag/level/mode。
- 打印日志两种写法：
  - `flogV/D/I/W/E<T : FLogger> { "msg" }`（`LoggerApi.kt`，reified 泛型，**推荐**）
  - `FLogger.lv/ld/li/lw/le { "msg" }`（`LogApi.kt`，扩展函数）
  - 消息是 `block: () -> String` 惰性求值；不满足等级时 `isLoggable` 提前返回，不会执行 block。
- 枚举（`Log.kt`）：`FLogLevel`（All/Verbose/Debug/Info/Warning/Error/Off，按 enum ordinal 比较大小），`FLogMode`（Default=控制台+仓库 / Console / Store）。
- 可扩展点（`FLogInitScope` 通过 `init {}` 注入）：`FLogFormatter`、`FLogStore.Factory`、`FLogDispatcher`、`setLogDirectory`。

## 关键架构

日志写入链路：`flogX` → `FLog.log()` → `_dispatcher.dispatch { _publisher.publish(record) }`。

- **调度器**（`LogDispatcher.kt`）：默认单线程 executor，保证**按提交顺序串行执行**。`LogDispatcherWrapper` 用 `AtomicInteger` 计数，归零时触发 `onIdle`（空闲时若等级为 Off 则关闭 publisher，否则检查文件是否被外部删除并重建）。所有磁盘 I/O 都在调度线程上，不阻塞调用方。
- **Publisher**（`LogPublisher.kt`）：`LogPublisherImpl` 按日期切换 `DateLogHandler`。文件路径：`<dir>/<yyyyMMdd>/<process>/<yyyyMMdd>.<seq>.log`（多进程时按进程名分子目录，`:` 替换为 `_`）。导出的压缩包路径：`<dir>/.zip/<process>/<yyyyMMdd>.zip`。
- **日志文件的滚动**：`setMaxBytePerDay` 超过一半阈值时，关掉当前文件、序号加一、开新文件继续写，同时删掉序号小于「当前 - 2」的旧文件，所以磁盘上始终最多两个文件、总量 ≈ `maxBytePerDay`。序号在 `<date>/<process>/` 目录内递增，序号大的是新文件，`DateLogHandler` 创建时扫一遍目录取最大序号接着写（进程重启后能续上）。序号**不补零**，所以文件名的字典序在跨越 9→10 时和时间序不一致，这是刻意接受的（同一时刻只有两个文件）。
- **Store**（`LogStore.kt`）：`FileLogStore` 用 `CounterOutputStream` 追加写并自行累计字节数（避免每次 `file.length()`）。
- **安全包装**：`SafeLogPublisher`（`LogSafe.kt`）和 `SafeLogStore`（`LogPublisher.kt` 内）用 `runCatching` 包裹 I/O，异常时自动 close，保证日志失败不crash业务。
- **Formatter**（`LogFormatter.kt`）：格式 `HH:mm:ss.SSS[tag|Level|threadID] msg\n`。连续相同 tag 会省略；主线程省略 threadID。
- 文件名/日期逻辑集中在 `LogFilename.kt` + `LogTime.kt`（`logNameOf`/`seqOf` 互为逆运算，别在别处拼日志文件名），日期字符串为 `yyyyMMdd`。**天数差**（`diffDays`）把 `yyyyMMdd` 转成"距 1970-01-01 的天数"再相减，纯整数运算不碰时区。这里踩过两次坑：直接减日期字符串会导致跨月时日志被全删（commit 5d5f093）；改成 `Calendar` 转毫秒相减后，夏令时切换那天只有 23 小时，除以 86400000 会少算一天。**不要再改回基于时间戳的算法。**

## 约定与注意事项

- 改动公共 API 时保持 `F` 前缀约定；内部类用 `internal`。
- **日志文件被外部删除，靠 `onIdle` 兜底，不要改成每次写入检查 `isFile`**（这是有意的取舍，不是 bug）。文件被删后 fd 仍然有效，写入照样成功（写进已 unlink 的 inode）也不会抛异常，没有比 stat 更便宜的信号；而 `onIdle` 已经在做这次 stat。因为 `LogDispatcherWrapper` 的计数器是**队列排空时归零**而不是定时触发：稀疏写入下一条日志就归零一次，检查频率和"每次写入都 stat"完全相同；突发写入下 N 条日志才 stat 一次，压力越大越省。所以现方案的检查频率永远不高于每次写入检查，代价只是丢失窗口等于一个 burst 的长度，而文件被外部删除本身是低概率事件。
- **日志滚动不要改回 `renameTo`**。旧实现是"写满就把 `xxx.log` 重命名为 `xxx.log.1`"，而 `renameTo` 的返回值只被拿去打日志了：一旦失败（权限、只读挂载、存储异常），原文件还在原地、大小不变，下一条日志写完又超阈值，于是**每写一条日志都做一遍 close + open + delete + rename**，文件无限增长，`setMaxMBPerDay` 完全失效（模拟 1000 条日志：磁盘占用超出上限 98 倍、995 次重试）。现在改成开新序号文件，只有 create 和 delete，没有 rename 这个失败点；**删除旧文件失败也绝不重试**，最多多留一个文件，写入路径不受影响。
- 轮换后的新日志文件是**惰性创建**的（`FileLogStore` 在第一次 `append` 时才建文件），所以刚切换完磁盘上还只有旧序号那一个文件，要等下一条日志写入新文件才出现。测试里断言文件列表时要注意这个时序。
- **`LogTime._calendar` 的时区是进程启动时快照的，运行期改时区不生效，这也是有意不处理的**。日志时间会停留在旧时区直到进程重启。不修的理由：低频、能自愈，而且 `deleteLog` 的 `today` 和日志文件日期都走同一个 `LogTime`，用的是同一个时区，保留策略不会因此判断错。**夏令时和这条无关**（`TimeZone` 按时间戳动态算偏移量），别把它和 `diffDays` 踩过的那个 DST 坑混为一谈。
- **每条日志都 `write` + `flush`，不加缓冲，这也是有意的取舍**（同样不是性能 bug）。日志库的价值恰恰在崩溃/被杀现场，缓冲意味着最后那几条——通常也是最关键的几条——丢失。要改缓冲的话必须先回答"进程被 SIGKILL 时怎么保证 flush"，而这个问题在 Android 上无解。
- **`logZipOf` 返回的压缩包是临时产物**：放在 `<dir>/.zip/<process>/`，只保证在本次进程运行期间有效，下次 `init` 会清空（只清本进程那个子目录，不影响其他进程）。使用方需要长期保存的话，应该拿到 `File` 之后自己移到自己管理的目录，库不提供 target 参数，也不提供单独的删除 API。压缩包的生命周期是"导出→上传/分享→丢弃"，和 `saveDays` 那套按天保留是两回事，所以刻意不让它受 `deleteLog` 管辖——`deleteLog` 会跳过所有 `.` 开头的条目（历史上正是因为 zip 落在日志根目录、文件名解析不出日期而被当垃圾删掉）。**`deleteLog(0)`（删除全部日志）同样不删压缩包**，因为"导出 zip → 立即清空日志腾空间 → 再上传 zip"是常见用法；所以它不能用 `dir.deleteRecursively()` 一把梭，得逐个跳过 `.` 条目。另外**日志根目录本身永远保留**（即使空了也不删），省掉下次写日志时重建目录的开销。
- `deleteLog(saveDays)`：保留最近 N 天，`saveDays=1` = 仅当天，`<=0` = 删全部。日期比较依赖 `LogFilename.diffDays`，改这里务必考虑跨月/跨年/闰年/夏令时，并补 `lib/src/test/.../LogFilenameTest.kt` 的用例。
- 两套测试：
  - `./gradlew :lib:test` — JVM 单元测试，无需设备。纯逻辑（日期计算等）放这里，能访问 `internal`，日期场景可以构造成确定的。
  - `./gradlew :app:connectedAndroidTest` — instrumented 测试，需要设备/模拟器，覆盖真实文件读写。`App.kt` 里通过 `setLogDispatcher(TestLogDispatcher)` 注入了测试调度器：它**保持异步**（单线程按序执行，和默认调度器行为一致），额外提供 `await()`。测试里断言文件状态前必须先调 `awaitLogIdle()`。
- instrumented 测试开头一律用 `resetLogDir()`，不要直接 `dir.deleteRecursively()`。因为上一个测试可能留着**打开的文件句柄**：目录被删掉后往这个句柄写日志照样成功（写进已 unlink 的 inode），文件不会重建，要等空闲回调发现文件不存在才 close。不先关句柄的话，测试结果取决于执行顺序。
- `awaitLogIdle()` 为什么不用 `FLog.logDirectory {}` 做屏障：一是 `logDirectory` 第一件事就是 `_publisher.close()`，会改变被测状态；二是 `LogDispatcherWrapper` 的 `onIdle` 在 `task.run()` 之后的 `finally` 里执行，从 block 里发信号等不到它。`TestLogDispatcher.await()` 直接往自己的执行器排空任务，收到的 task 已经是包装好的（含 `onIdle`），既能等全又无副作用。`LogFileDeletedTest` 依赖这个语义。
- instrumented 测试里的日期**只能相对当前时间往前推**（用 `dateOfDaysAgo`），因为 `deleteLog` 读的是 `System.currentTimeMillis()`；写死具体日期的测试只在那一天能通过。
- 库无外部依赖，新增依赖需谨慎（会传递给使用方）。
- 发布用 `com.vanniktech.maven.publish`，版本号在 `lib/gradle.properties`。