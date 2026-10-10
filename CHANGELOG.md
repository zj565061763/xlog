# Changelog

## 2.2.0

### ⚠️ Breaking Changes

- `FLogInitScope.setLogDirectory` 的参数由 `File` 改为获取目录的方法 `() -> File?`，在调度线程上调用
- 取不到日志目录（返回 null）时，这次写日志、`deleteLog`、`logDirectory` 都不执行；取到之后本进程一直使用该目录
- 移除 `fLogDir`
- 默认日志目录不再回退到内部存储，外部存储不可用时不写日志文件，Logcat 照常输出
- `FLogFormatter` 新增 `reset()` 用来重置状态，实现 `AutoCloseable` 的格式化器不再调用 `close()`

### 🐛 Bug Fixes

- 日志目录读取失败时，`logZipOf` 返回缺少日志的压缩包，并替换上次的；现在返回 null，保留上次的压缩包
- 自定义 `FLogFormatter` 的 `format()` 抛异常后，下一条相同 tag 的日志会省略 tag，看起来像属于上一个 tag
- 设置了单日大小上限时，自定义 `FLogStore` 的 `size()` 抛异常会关闭日志文件，但不重置格式化器的状态
- 开启 R8 混淆时，嵌套类、局部类 logger 的默认 tag 可能带上外部类名（例如 `Feature$Logger`），和未混淆时不一致
- 日志切换时旧文件已被其他进程删除，Logcat 会误报删除失败
- 私有进程（如 `com.example:worker`）和名为 `com.example_worker` 的全局进程会写进同一个日志文件，`init` 时还会清空对方的压缩包
- Android 7.0–8.1 获取进程名出错时，日志不写入文件，`logZipOf` 抛出异常；现在改为读取 `/proc/self/cmdline` 获取进程名
- 其他进程持续写日志时，`logZipOf` 会一直读取新写入的内容，打包变慢甚至不结束，期间本进程的日志排队等待写入；现在每个文件只打包开始读取时已有的内容
- 日志目录里的日期或进程目录被同名文件占用时，日志一直写不进文件；现在删掉该文件后创建目录
- Java API 消息为 null 或空串时不检查是否已初始化，和 Kotlin API 不一致；现在同样抛出异常提示先初始化
- `logZipOf` 写入压缩包失败时（比如磁盘写满），临时文件的句柄没有关闭，占用的空间不能及时释放
- 其他进程在打包开始时删除了日期目录，`logZipOf` 可能返回没有任何条目的压缩包；现在返回 null
- `init` 清空压缩包失败、`deleteLog` 读取日志目录失败时没有任何提示；现在输出错误到 Logcat

### Migration

- `setLogDirectory(dir)` 改为 `setLogDirectory { dir }`
- `fLogDir()` 改为 `getExternalFilesDir(null)?.resolve("sd.lib.xlog")`，外部存储不可用时为 null
- `fLogDir(preferExternal = false)` 改为 `filesDir.resolve("sd.lib.xlog")`
- 需要写到内部存储或 Direct Boot 期间可用的设备加密存储的，通过 `setLogDirectory` 返回对应目录
- 之前版本外部存储不可用时写到 `filesDir/sd.lib.xlog` 的日志和压缩包不再被清理，需要时手动删除
- 私有进程的日志和压缩包目录名由 `com.example_worker` 改为 `com.example-worker`，按目录名识别进程的需要同步修改
- 之前版本私有进程导出的压缩包不会再被 `init` 清空，需要时手动删除 `.zip/com.example_worker`
- 格式化器在 `AutoCloseable.close()` 里重置状态的，改为重写 `reset()`
- 用 Java 实现 `FLogFormatter` 的，需要实现 `reset()`，没有状态时留空

## 2.1.0

### ✨ Improvements

- 混淆规则改为只保留 `FLogger` 实现类的类名，未使用的实现类可以被 R8 移除
- 每条日志少一次配置查询和等级判断
- `init` 不再在调用线程上获取默认日志目录和进程名，避免主线程磁盘 I/O
- 库内部日志改为只在出错时以 Error 等级输出到 Logcat，tag 为 `XLogLibLogger`，日志等级为 Off 时不输出

### 🐛 Bug Fixes

- 日志写入失败后，下一条相同 tag 的日志会省略 tag，看起来像属于上一个 tag
- 在匿名对象上调用 `FLogger.lx` 时 tag 为空，现在使用去掉包名的类名
- 系统接口取不到进程名时，`init` 会清空其他进程导出的压缩包，多个进程也会写同一个日志文件；现在改为读取 `/proc/self/cmdline` 兜底，仍取不到时 `init` 不再清空压缩包
- 同一日期再次调用 `logZipOf` 时，上次的压缩包在打包期间不完整，打包失败时还会被删除；现在打包成功后才替换
- 多进程时，打包期间其他进程删除了日志文件（日志滚动或清理日志）会导致 `logZipOf` 返回 null；现在跳过已被删除的文件
- 自定义 `FLogFormatter` 的 `close()` 抛异常时，日志文件不再切换，`setMaxMBPerDay` 失效；现在忽略该异常

### Migration

- 混淆规则不再保留 `FLogger` 实现类的成员，依赖它保留成员的（例如通过反射、序列化或 `@JavascriptInterface` 访问），需要自行添加 keep 规则

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
