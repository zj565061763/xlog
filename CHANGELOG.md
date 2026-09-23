# Changelog

## 2.1.0

### ⚠️ Breaking Changes

- 不兼容旧版本的二进制：`flogX`、`FLogger.lx` 是内联函数，旧版本编译出的代码在新版本上运行会抛 `NoSuchMethodError`

### ✨ Improvements

- 混淆规则改为只保留 `FLogger` 实现类的类名，未使用的实现类可以被 R8 移除
- 每条日志少一次配置查询和等级判断
- `init` 不再在调用线程上获取默认日志目录和进程名，避免主线程磁盘 I/O
- 库内部日志改为以 Error 等级输出到 Logcat，tag 为 `XLogLibLogger`，只在日志等级为 Off 时不输出

### 🐛 Bug Fixes

- 日志写入失败后，下一条相同 tag 的日志会省略 tag，看起来像属于上一个 tag
- 在匿名对象上调用 `FLogger.lx` 时 tag 为空，现在使用去掉包名的类名
- 系统接口取不到进程名时，`init` 会清空其他进程导出的压缩包，多个进程也会写同一个日志文件；现在改为读取 `/proc/self/cmdline` 兜底，仍取不到时 `init` 不再清空压缩包
- 同一日期再次调用 `logZipOf` 时，上次的压缩包在打包期间不完整，打包失败时还会被删除；现在打包成功后才替换
- 多进程时，打包期间其他进程删除了日志文件（日志滚动或清理日志）会导致 `logZipOf` 返回 null；现在跳过已被删除的文件

### Migration

- 直接依赖本库的工程重新编译即可，不需要改代码
- 依赖了其他基于本库编译的库的，需要这些库基于新版本重新编译发布

## 2.0.0

### ⚠️ Breaking Changes

- 移除 `FLogDirectoryScope.logZipOf(year, month, dayOfMonth)`，请使用 `logZipOf(date: String)`
- 日志文件命名由 `<date>.log`、`<date>.log.1` 改为 `<date>.<seq>.log`：写满后切换到下一个序号继续写，只保留最新两个文件，不再重命名（重命名失败会导致大小限制失效）

### ✨ Improvements

- 日志压缩包移到 `<日志目录>/.zip/<进程名>/` 下，属于临时产物，下次 `init` 时清空；需要长期保存请获取后自行移走
- `deleteLog` 不再删除压缩包（包括 `deleteLog(0)`），日志根目录本身也始终保留
- `FLogDispatcher` 契约明确为三条：每个任务有且只执行一次、按提交顺序串行执行、保证任务之间的内存可见性
- 新增 lib 模块 JVM 单元测试：日期计算、调度器契约、文件轮换

### 🐛 Bug Fixes

- 夏令时切换日 `deleteLog` 天数计算少一天（改为纯数值日期计算，非法日期严格校验）
- 未初始化时打日志抛 `UninitializedPropertyAccessException`，现在正确提示需要先初始化
- `FLog.logDirectory` 的 block 抛异常会导致 App 崩溃，现在会捕获
- `setMaxMBPerDay` 参数过大时 Int 溢出导致限制失效
- `FLogStore.size()` 存在副作用，可能凭空创建出空文件
- 日志文件切换时仓库创建失败会写回旧文件，导致旧文件无限增长

### Migration

- 调用了 `logZipOf(year, month, dayOfMonth)` 的，改为 `logZipOf("yyyyMMdd")` 格式的日期字符串
- 依赖压缩包长期存在的，导出后把返回的 `File` 移到自己管理的目录
- 自定义了 `FLogDispatcher` 的，对照接口注释里的三条契约检查实现
- 旧格式的日志文件升级后不再写入，会随日期过期被 `deleteLog` 清理，无需手动处理
