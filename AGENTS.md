# xlog

Android 日志库，发布到 Maven Central（`io.github.zj565061763.android:xlog`）。

- 核心能力：日志同时输出到 Logcat 和按天分文件的磁盘仓库，支持等级/模式过滤、单日大小限制、按天清理、导出 zip。
- 版本号在 `lib/gradle.properties` 的 `VERSION_NAME`，发布用 `com.vanniktech.maven.publish`。
- 变更历史维护在 `CHANGELOG.md`，语言简洁，只写用户可感知的内容。
  - 标题格式：`## <版本号>`
  - 小节固定为 `### ⚠️ Breaking Changes` / `### ✨ Improvements` / `### 🐛 Bug Fixes` / `### Migration`，没有内容的小节省略

## 项目结构

| 路径 | 说明 |
|---|---|
| `lib/` | 发布的库，namespace `com.sd.lib.xlog`，`minSdk 21`，纯 Kotlin，运行时零依赖 |
| `lib/src/test/` | JVM 单元测试 |
| `app/` | 演示 App，`App.kt` 是初始化示例 |
| `app/src/androidTest/` | instrumented 测试，公共工具在 `TestUtils.kt` |
| `gradle/libs.versions.toml` | 依赖和 SDK 版本 |

## 公共 API

- 对外类型以 `F` 开头；内部实现用 `internal`，不加前缀。
- `@PublishedApi` 的函数会内联进使用方的代码，删除或改签名会破坏二进制兼容，要在 CHANGELOG 的 Breaking Changes 里写明。
- `FLog`（`Log.kt`）：单例总控，必须先 `init`，否则抛异常。
- `FLogger`（`Logger.kt`）：空标记接口，使用方定义子接口作为日志标识，默认 tag 是短类名。
- `FLoggerConfig`：通过 `configLogger` 覆盖单个 logger 的 tag/level/mode。
- 打印日志：
  - `flogV/D/I/W/E<T : FLogger> { "msg" }`（`LogApi.kt`，推荐）
  - `FLogger.lv/ld/li/lw/le { "msg" }`（`LoggerApi.kt`）
  - 消息 block 惰性求值，等级不满足时不执行
- 扩展点在 `FLogInitScope`（`FLog.init {}` 里设置）：日志目录、`FLogFormatter`、`FLogStore.Factory`、`FLogDispatcher`。

## 关键架构

- 写入链路：`flogX` → `FLog.publishLog()`，Logcat 在调用线程直接输出，仓库写入经 `_dispatcher.dispatch { _publisher.publish(record) }` 在调度线程执行。
- 磁盘 I/O 都在调度线程上，不阻塞调用方。
  - 包括获取进程名和默认日志目录，`init` 里只传入获取方法，不要直接调用 `currentProcess()`、`fLogDir()`
- 调度器（`LogDispatcher.kt`）：默认单线程 executor，实现契约见 `FLogDispatcher` 的注释。
  - 保持 `Executors.newSingleThreadExecutor()` 的默认线程工厂，不要为了命名或调优先级自定义：线程优先级 nice ≥ 10 时，Android 12 及以下会把线程移到后台调度组，队列积压，崩溃时丢失排队中的日志
- `LogDispatcherWrapper` 计数，队列排空时触发 `onIdle`：等级为 Off 则关闭 publisher，否则检查日志文件，被外部删除就关闭，下次写入时重建。
- 文件路径（`<process>` 是进程名，`:` 替换为 `_`；系统接口和 `/proc/self/cmdline` 都取不到进程名时省略这一层）：
  - 日志：`<dir>/<yyyyMMdd>/<process>/<yyyyMMdd>.<seq>.log`
  - 压缩包：`<dir>/.zip/<process>/<yyyyMMdd>.zip`
- 日志滚动（`DateLogHandler`）：
  - 当前文件达到 `maxBytePerDay` 的一半时关闭，序号加一继续写，并删除当前和上一个序号之外的旧文件；所以最多两个文件，总量约等于上限
  - 创建时扫描目录取最大序号接着写，进程重启后能续上
  - 序号不补零，跨越 9→10 时文件名字典序和时间序不一致，这是刻意接受的
  - 新文件惰性创建，切换后要等下一条日志写入才出现，测试断言文件列表时注意这个时序
- `FileLogStore`（`LogStore.kt`）：`CounterOutputStream` 自行累计字节数，避免每次 `file.length()`。
- 异常隔离：`SafeLogPublisher`（`LogSafe.kt`）捕获并打印异常，保证日志失败不影响业务；`SafeLogStore`（`LogPublisher.kt`）出错时关闭再重抛。
- 库内部日志 `libLog` 直接用 `Log.e` 输出到 Logcat，tag 是 `XLogLibLogger`，只在全局等级为 Off 时不输出。
  - 不要改回走 `flogX`：使用方调高等级后，写盘、打包失败会完全看不到
- 格式（`LogFormatter.kt`）：`HH:mm:ss.SSS[tag|L|threadID] msg\n`，`L` 是 V/D/I/W/E；连续相同 tag 省略 tag，主线程省略 threadID。
- 文件名和日期逻辑集中在 `LogFilename.kt`、`LogTime.kt`，日期格式 `yyyyMMdd`；`logNameOf`/`seqOf` 互为逆运算，不要在别处拼日志文件名。

## 有意的取舍（不是 bug，不要“修复”）

- `diffDays` 把 `yyyyMMdd` 转成距 1970-01-01 的天数再相减，纯整数运算，不碰时区。
  - 不要直接减日期字符串：跨月时日志会被全删（commit 5d5f093）
  - 不要改回 `Calendar` 转毫秒相减：夏令时切换日只有 23 小时，会少算一天
- 日志文件被外部删除靠 `onIdle` 兜底，不要改成每次写入检查 `isFile`。
  - 文件被删后 fd 仍有效，写入照样成功，没有比 stat 更便宜的信号
  - `onIdle` 在队列排空时触发：稀疏写入时和每次检查等价，突发写入时 N 条才检查一次
  - 代价是最多丢失一个 burst 的日志，而外部删除本身是低概率事件
- 日志滚动不要改回 `renameTo`，删除旧文件失败也绝不重试。
  - `renameTo` 失败时原文件大小不变，之后每条日志都重试 close + open + delete + rename，文件无限增长，`setMaxMBPerDay` 失效
  - 现方案只有 create 和 delete，删除失败最多多留一个文件，不影响写入
- 每条日志都 `write` + `flush`，不加缓冲。
  - 崩溃或被杀前的最后几条日志最关键，缓冲会丢掉它们
  - 要加缓冲必须先回答“进程被 SIGKILL 时怎么 flush”，这在 Android 上无解
- `LogTime._calendar` 的时区在首次使用时确定，之后改时区不生效。
  - 低频，进程重启后自愈
  - `deleteLog` 的 today 和日志文件日期用同一个时区，保留策略不会判断错
  - 夏令时与此无关（`TimeZone` 按时间戳动态计算偏移），不要和 `diffDays` 的夏令时问题混为一谈
- `deleteLog` 保留日期在今天之后的目录，只有 `saveDays<=0` 时才删。
  - 这类目录来自设备时间被调快又恢复
  - 不删是因为当前时间被调慢时，这些目录才是真实的日志
- `lib/consumer-rules.pro` 用 `-keepnames` 保留 `FLogger` 实现类的类名。
  - 默认 tag 是短类名，删掉这条规则的话，混淆后 tag 会变成无意义的短名
  - 不要改回 `-keep`：它会阻止 R8 移除没用到的实现类

## 日志清理与压缩包

- `deleteLog(saveDays)`：保留最近 N 天，`saveDays=1` 表示仅当天，`<=0` 表示删除全部。
- 是否删除的判断集中在 `LogFilename.kt` 的 `shouldDeleteLog`，不要在 `deleteLog` 里另写。
- 改日期比较务必考虑跨月、跨年、闰年、夏令时，并补 `LogFilenameTest.kt` 的用例。
- 日志目录只能存放日志：`deleteLog` 会删除其中不是日志的条目，自定义目录时不能用共用目录。
- `deleteLog` 跳过所有 `.` 开头的条目；历史上 zip 落在日志根目录，文件名解析不出日期而被误删。
- `deleteLog(0)` 同样不删压缩包，常见用法是“导出 zip → 清空日志 → 上传 zip”；所以不能用 `dir.deleteRecursively()`。
- 日志根目录永远保留，即使空了也不删，省掉下次写日志时重建目录。
- `logZipOf` 返回的压缩包是临时产物：
  - 只保证本次进程运行期间有效，下次 `init` 清空本进程的压缩包子目录，不影响其他进程
  - 取不到进程名时 `init` 不清空，此时压缩包目录是所有进程共用的
  - 需要长期保存由使用方自行移走，库不提供 target 参数，也不提供删除 API
  - 生命周期是“导出 → 上传/分享 → 丢弃”，刻意不受 `deleteLog` 管辖
- 打包先写 `<yyyyMMdd>.zip.tmp`，成功后再 `renameTo` 替换，同一日期再次打包时上次的压缩包保持完整，失败时也保留。

## 测试

| 命令 | 说明 |
|---|---|
| `./gradlew :lib:test` | JVM 单元测试，无需设备，能访问 `internal`；纯逻辑（日期计算等）放这里，日期可构造成确定值 |
| `./gradlew :app:connectedAndroidTest` | instrumented 测试，需要设备/模拟器，覆盖真实文件读写 |

instrumented 测试：

- `App.kt` 注入了 `TestLogDispatcher`：保持异步、单线程按序执行，额外提供 `await()`。
- 断言文件状态前必须先调 `awaitLogIdle()`。
- 开头一律用 `resetLogDir()`，不要直接 `dir.deleteRecursively()`；上一个测试可能留着打开的句柄，删目录后写入仍会成功且文件不重建，结果取决于执行顺序。
- `awaitLogIdle()` 不用 `FLog.logDirectory {}` 做屏障，`LogFileDeletedTest` 依赖这个语义：
  - `logDirectory` 第一件事是 `_publisher.close()`，会改变被测状态
  - `onIdle` 在 `task.run()` 之后的 `finally` 里执行，从 block 里发信号等不到它
- 日期只能相对当前时间往前推（用 `dateOfDaysAgo`），因为 `deleteLog` 读的是 `System.currentTimeMillis()`。

## 依赖

- 库无外部依赖，新增依赖需谨慎，会传递给使用方。
