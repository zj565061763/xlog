# xlog

Android 日志库，发布到 Maven Central（`io.github.zj565061763.android:xlog`）。核心能力：把日志同时输出到 Logcat 控制台和**按天分文件的磁盘仓库**，支持日志等级/模式过滤、单日文件大小限制、按天清理、导出 zip。当前版本见 `lib/gradle.properties` 的 `VERSION_NAME`（当前 1.9.2）。

## 项目结构

- `lib/` — 发布的库模块，namespace `com.sd.lib.xlog`，`minSdk 21`，纯 Kotlin **无第三方依赖**。所有源码在 `lib/src/main/java/com/sd/lib/xlog/`。
- `app/` — 演示 App（`com.sd.demo.xlog`）。**唯一的测试在这里**：`app/src/androidTest/`（instrumented 测试，非单元测试），需要设备/模拟器运行。`app/src/main/java/.../App.kt` 是初始化示例。
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
- **Publisher**（`LogPublisher.kt`）：`LogPublisherImpl` 按日期切换 `DateLogHandler`。文件路径：`<dir>/<yyyyMMdd>/<process>/<yyyyMMdd>.log`（多进程时按进程名分子目录，`:` 替换为 `_`）。`setMaxBytePerDay` 超过一半阈值时把当前文件重命名为 `.log.1`（滚动，最多保留一个 part）。
- **Store**（`LogStore.kt`）：`FileLogStore` 用 `CounterOutputStream` 追加写并自行累计字节数（避免每次 `file.length()`）。
- **安全包装**：`SafeLogPublisher`（`LogSafe.kt`）和 `SafeLogStore`（`LogPublisher.kt` 内）用 `runCatching` 包裹 I/O，异常时自动 close，保证日志失败不crash业务。
- **Formatter**（`LogFormatter.kt`）：格式 `HH:mm:ss.SSS[tag|Level|threadID] msg\n`。连续相同 tag 会省略；主线程省略 threadID。
- 文件名/日期逻辑集中在 `LogFilename.kt` + `LogTime.kt`，日期字符串为 `yyyyMMdd`。**跨月天数差**用 `Calendar` 转毫秒再相减（见 `diffDays`），历史上曾因直接减日期出过 bug（commit 5d5f093）。

## 约定与注意事项

- 改动公共 API 时保持 `F` 前缀约定；内部类用 `internal`。
- `deleteLog(saveDays)`：保留最近 N 天，`saveDays=1` = 仅当天，`<=0` = 删全部。日期比较依赖 `LogFilename.diffDays`，改这里务必考虑跨月/跨年，并补 `app/src/androidTest/.../file/` 下的测试。
- 测试是 **androidTest（instrumented）**，用 `./gradlew :app:connectedAndroidTest` 运行，需要连接的设备/模拟器。`TestLogDispatcher` 提供同步调度以便测试。
- 库无外部依赖，新增依赖需谨慎（会传递给使用方）。
- 发布用 `com.vanniktech.maven.publish`，版本号在 `lib/gradle.properties`。