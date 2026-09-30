package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LogPublisherTest {
  @get:Rule
  val folder = TemporaryFolder()

  /** 正常轮换：写满就切下一个序号，始终只保留当前和上一个 */
  @Test
  fun testRotate() {
    val dir = folder.newFolder()
    val publisher = newPublisher(dir) { defaultLogStore(it) }

    // 上限400字节，写满200字节就轮换，每条日志约61字节，所以4条填满一个分片
    val maxByte = 400L
    publisher.setMaxBytePerDay(maxByte)

    /**
     * 每4条填满一个分片，42条一路轮换到序号10，最后2条写在序号10里。
     * 故意让保留的两个序号跨过9和10的位数边界，
     * 覆盖序号进入两位数之后的轮换和删除逻辑。
     */
    repeat(42) { publisher.publish(testLogRecord()) }

    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    assertEquals(
      listOf(filename.logNameOf(date, 9), filename.logNameOf(date, 10)),
      dir.logNames(),
    )

    val totalSize = dir.totalSize()
    assertTrue("日志总大小${totalSize}字节，超过了上限${maxByte}字节", totalSize <= maxByte)
  }

  /**
   * 轮换的时候创建日志仓库失败，不能退回去继续写旧文件。
   * 否则每条日志都会触发一次轮换、每次都失败，
   * 旧文件无限增长，[FLog.setMaxMBPerDay]的限制形同虚设。
   */
  @Test
  fun testStoreCreateErrorOnRotate() {
    val dir = folder.newFolder()
    var createCount = 0

    val publisher = newPublisher(dir) { file ->
      createCount++
      // 第一个文件创建成功，之后轮换时全部失败
      if (createCount >= 2) error("create error")
      defaultLogStore(file)
    }

    val maxByte = 100L
    publisher.setMaxBytePerDay(maxByte)

    var failures = 0
    repeat(20) {
      runCatching { publisher.publish(testLogRecord()) }.onFailure { failures++ }
    }

    // 创建失败的时候日志写不进去，但是已有文件不能因此无限增长
    val totalSize = dir.totalSize()
    assertTrue("日志总大小${totalSize}字节，超过了上限${maxByte}字节", totalSize <= maxByte)
    assertTrue("应该有写入失败", failures > 0)
  }

  /** 轮换的时候格式化器重置失败，不能中断轮换，否则会一直写回旧文件 */
  @Test
  fun testFormatterResetErrorOnRotate() {
    val dir = folder.newFolder()
    val publisher = defaultLogPublisher(
      processProvider = { null },
      directoryProvider = { dir },
      filename = defaultLogFilename(),
      formatter = ResetErrorFormatter(),
      storeFactory = { defaultLogStore(it) },
    )

    val maxByte = 400L
    publisher.setMaxBytePerDay(maxByte)
    repeat(42) { publisher.publish(testLogRecord()) }
    publisher.close()

    assertEquals(2, dir.logNames().size)
    val totalSize = dir.totalSize()
    assertTrue("日志总大小${totalSize}字节，超过了上限${maxByte}字节", totalSize <= maxByte)
  }

  /** 写入失败之后，下一条日志不能省略tag，否则会被当成上一个tag的日志 */
  @Test
  fun testAppendErrorKeepTag() {
    val dir = folder.newFolder()
    var appendCount = 0

    val publisher = newPublisher(dir) { file ->
      val store = defaultLogStore(file)
      object : FLogStore by store {
        override fun append(log: String) {
          appendCount++
          // 第2条日志写入失败
          if (appendCount == 2) error("append error")
          store.append(log)
        }
      }
    }

    publisher.publish(testLogRecord(tag = "A"))
    runCatching { publisher.publish(testLogRecord(tag = "B")) }
    publisher.publish(testLogRecord(tag = "B"))

    val lines = dir.walkTopDown().first { it.isFile }.readLines()
    assertEquals(2, lines.size)
    assertTrue(lines[1], lines[1].contains("[B|"))
  }

  /** 格式化失败之后，下一条日志不能省略tag，否则会被当成上一个tag的日志 */
  @Test
  fun testFormatErrorKeepTag() {
    val dir = folder.newFolder()
    val publisher = defaultLogPublisher(
      processProvider = { null },
      directoryProvider = { dir },
      filename = defaultLogFilename(),
      // 第2条日志格式化失败
      formatter = FormatErrorFormatter(errorAt = 2),
      storeFactory = { defaultLogStore(it) },
    )

    publisher.publish(testLogRecord(tag = "A"))
    runCatching { publisher.publish(testLogRecord(tag = "B")) }
    publisher.publish(testLogRecord(tag = "B"))

    val lines = dir.walkTopDown().first { it.isFile }.readLines()
    assertEquals(2, lines.size)
    assertTrue(lines[1], lines[1].contains("[B|"))
  }

  /** 获取大小失败时仓库已经关闭，要和关闭日志文件一样重置格式化器 */
  @Test
  fun testSizeErrorResetFormatter() {
    val dir = folder.newFolder()
    val formatter = ResetCountFormatter()
    var sizeCount = 0
    var storeCloseCount = 0

    val publisher = defaultLogPublisher(
      processProvider = { null },
      directoryProvider = { dir },
      filename = defaultLogFilename(),
      formatter = formatter,
      storeFactory = { file ->
        val store = defaultLogStore(file)
        object : FLogStore by store {
          override fun size(): Long {
            // 第1次获取大小失败
            if (++sizeCount == 1) error("size error")
            return store.size()
          }

          override fun close() {
            storeCloseCount++
            store.close()
          }
        }
      },
    )

    // 设置上限之后每条日志都会获取大小
    publisher.setMaxBytePerDay(1024 * 1024)

    assertTrue(runCatching { publisher.publish(testLogRecord()) }.isFailure)
    assertEquals(1, storeCloseCount)
    assertEquals(1, formatter.resetCount)
  }

  /** 获取进程名和目录可能有IPC或磁盘I/O，创建时不能获取，要等到调度线程上第一次用到 */
  @Test
  fun testLazyProcessAndDirectory() {
    val dir = folder.newFolder()
    var processCount = 0
    var directoryCount = 0

    val publisher = defaultLogPublisher(
      processProvider = { processCount++; null },
      directoryProvider = { directoryCount++; dir },
      filename = defaultLogFilename(),
      formatter = defaultLogFormatter(),
      storeFactory = { defaultLogStore(it) },
    )
    assertEquals(0, processCount)
    assertEquals(0, directoryCount)

    // 第一次用到时获取，之后不再重复获取
    publisher.publish(testLogRecord())
    publisher.publish(testLogRecord())
    assertEquals(1, processCount)
    assertEquals(1, directoryCount)
  }

  /** 取不到日志目录时丢弃日志、不删压缩包，每次用到都重新获取；取到之后固定不变 */
  @Test
  fun testNullDirectory() {
    val dir = folder.newFolder()
    val date = defaultLogFilename().dateOf(RECORD_MILLIS)
    val zip = newPublisher(dir, process = "com.sd.demo").zipFileOf(date).createZip()
    var directory: File? = null
    var directoryCount = 0

    val publisher = defaultLogPublisher(
      processProvider = { "com.sd.demo" },
      directoryProvider = { directoryCount++; directory },
      filename = defaultLogFilename(),
      formatter = defaultLogFormatter(),
      storeFactory = { defaultLogStore(it) },
    )

    publisher.publish(testLogRecord())
    publisher.deleteZipDirectory()
    assertNull(publisher.logDirOf(date))
    assertNull(publisher.zipFileOf(date))
    assertEquals(4, directoryCount)
    assertEquals(false, dir.resolve(date).exists())
    assertEquals(true, zip.exists())

    directory = dir
    publisher.publish(testLogRecord())
    publisher.publish(testLogRecord())
    assertEquals(2, dir.resolve(date).walkTopDown().first { it.isFile }.readLines().size)

    directory = null
    assertEquals(dir.resolve(date), publisher.logDirOf(date))
    assertEquals(5, directoryCount)
  }

  /** 只删除本进程的压缩包目录，取不到进程名时不删，避免误删其他进程的压缩包 */
  @Test
  fun testDeleteZipDirectory() {
    val dir = folder.newFolder()
    val main = newPublisher(dir, process = "com.sd.demo")
    val remote = newPublisher(dir, process = "com.sd.demo:remote")
    val unknown = newPublisher(dir, process = null)

    val mainZip = main.zipFileOf("20231125").createZip()
    val remoteZip = remote.zipFileOf("20231125").createZip()
    val unknownZip = unknown.zipFileOf("20231125").createZip()

    unknown.deleteZipDirectory()
    assertEquals(true, mainZip.exists())
    assertEquals(true, remoteZip.exists())
    assertEquals(true, unknownZip.exists())

    main.deleteZipDirectory()
    assertEquals(false, mainZip.exists())
    assertEquals(true, remoteZip.exists())
    assertEquals(true, unknownZip.exists())
  }

  /** 删除旧日志时，文件已经被其他进程删除不算失败，否则会误报错误日志 */
  @Test
  fun testDeleteOrAbsent() {
    val file = folder.newFile()
    assertTrue(file.deleteOrAbsent())
    assertFalse(file.exists())

    // 已经不存在
    assertTrue(file.deleteOrAbsent())

    // 存在但删除失败
    val dir = folder.newFolder().apply { resolve("log").writeText("log") }
    assertFalse(dir.deleteOrAbsent())
  }

  /** 进程重启之后从已有文件的最大序号接着写，不动其他文件 */
  @Test
  fun testContinueSeq() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val logDir = dir.resolve(date).apply { mkdirs() }
    listOf(1, 3).forEach { logDir.resolve(filename.logNameOf(date, it)).writeText("old\n") }

    newPublisher(dir).publish(testLogRecord())

    assertEquals(listOf(filename.logNameOf(date, 1), filename.logNameOf(date, 3)), dir.logNames())
    val lines = logDir.resolve(filename.logNameOf(date, 3)).readLines()
    assertEquals(2, lines.size)
    assertEquals("old", lines[0])
  }

  /** 接着写的文件已经写满时，切到下一个序号，并删除当前和上一个之外的旧文件 */
  @Test
  fun testContinueSeqRotate() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val logDir = dir.resolve(date).apply { mkdirs() }
    listOf(1, 2).forEach { logDir.resolve(filename.logNameOf(date, it)).writeText("old\n") }
    // 上一次运行已经写到200字节，再写一条就超过一半上限
    logDir.resolve(filename.logNameOf(date, 3)).writeText("0".repeat(199) + "\n")

    val publisher = newPublisher(dir)
    publisher.setMaxBytePerDay(400)

    // 写进序号3之后切换，删除序号1和2，新文件惰性创建，还没有出现
    publisher.publish(testLogRecord())
    assertEquals(listOf(filename.logNameOf(date, 3)), dir.logNames())

    publisher.publish(testLogRecord())
    assertEquals(listOf(filename.logNameOf(date, 3), filename.logNameOf(date, 4)), dir.logNames())
  }

  /** 跨天时写到新日期的目录，新文件的第一条日志不能省略tag */
  @Test
  fun testDateChange() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val nextDayMillis = RECORD_MILLIS + 24 * 60 * 60 * 1000L
    val date = filename.dateOf(RECORD_MILLIS)
    val nextDate = filename.dateOf(nextDayMillis)

    val publisher = newPublisher(dir)
    publisher.publish(testLogRecord())
    publisher.publish(testLogRecord(millis = nextDayMillis))

    assertEquals(1, dir.resolve(date).resolve(filename.logNameOf(date, 0)).readLines().size)
    val nextLines = dir.resolve(nextDate).resolve(filename.logNameOf(nextDate, 0)).readLines()
    assertEquals(1, nextLines.size)
    assertTrue(nextLines[0], nextLines[0].contains("[T|"))
  }

  /** 日志按进程名分子目录，进程名里的:替换为- */
  @Test
  fun testProcessDir() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)

    newPublisher(dir, process = "com.sd.demo:remote").publish(testLogRecord())

    assertEquals(true, dir.resolve(date).resolve("com.sd.demo-remote").resolve(filename.logNameOf(date, 0)).isFile)
  }

  /** 私有进程和名字相近的全局进程不能共用目录，否则日志混写，init时还会清空对方的压缩包 */
  @Test
  fun testProcessDirConflict() {
    val dir = folder.newFolder()
    val date = defaultLogFilename().dateOf(RECORD_MILLIS)
    val privateProcess = newPublisher(dir, process = "com.sd.demo:remote")
    val globalProcess = newPublisher(dir, process = "com.sd.demo_remote")

    privateProcess.publish(testLogRecord())
    globalProcess.publish(testLogRecord())
    assertEquals(listOf("com.sd.demo-remote", "com.sd.demo_remote"), dir.resolve(date).list()?.sorted())

    val globalZip = globalProcess.zipFileOf(date).createZip()
    privateProcess.deleteZipDirectory()
    assertEquals(true, globalZip.exists())
  }

  /** 进程名含路径分隔符或者是.和..时会跳出所在目录，按取不到进程名处理 */
  @Test
  fun testInvalidProcessName() {
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val outside = folder.newFolder()

    for (process in listOf(".", "..", "a/../..", outside.absolutePath)) {
      val dir = folder.newFolder()
      val otherZip = newPublisher(dir, process = "other").zipFileOf(date).createZip()
      val publisher = newPublisher(dir, process = process)

      // 日志直接写在日期目录下，不分进程子目录
      publisher.publish(testLogRecord())
      publisher.close()
      assertEquals(process, true, dir.resolve(date).resolve(filename.logNameOf(date, 0)).isFile)

      // 不删除压缩包目录，其他进程的压缩包还在
      publisher.deleteZipDirectory()
      assertEquals(process, true, otherZip.exists())
    }

    assertEquals(emptyList<String>(), outside.list()?.toList())
  }
}

/** 目录下的日志文件名，按序号排序。不能按文件名排序，序号位数不同的时候字典序和数值序不一致 */
private fun File.logNames(): List<String> {
  val filename = defaultLogFilename()
  return walkTopDown().filter { it.isFile }.map { it.name }
    .sortedBy { filename.seqOf(it) }
    .toList()
}

private fun File.totalSize(): Long {
  return walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

private fun File?.createZip(): File {
  val file = checkNotNull(this)
  file.parentFile?.mkdirs()
  assertTrue(file.createNewFile())
  return file
}

private class ResetErrorFormatter : FLogFormatter {
  private val _formatter = defaultLogFormatter()
  override fun format(record: FLogRecord): String = _formatter.format(record)
  override fun reset() = error("reset error")
}

/** 第[errorAt]次格式化时，先更新内部状态再抛异常 */
private class FormatErrorFormatter(private val errorAt: Int) : FLogFormatter {
  private val _formatter = defaultLogFormatter()
  private var _count = 0

  override fun format(record: FLogRecord): String {
    val log = _formatter.format(record)
    if (++_count == errorAt) error("format error")
    return log
  }

  override fun reset() {
    _formatter.reset()
  }
}

/** 记录[reset]的调用次数 */
private class ResetCountFormatter : FLogFormatter {
  private val _formatter = defaultLogFormatter()

  var resetCount = 0
    private set

  override fun format(record: FLogRecord): String = _formatter.format(record)

  override fun reset() {
    resetCount++
    _formatter.reset()
  }
}
