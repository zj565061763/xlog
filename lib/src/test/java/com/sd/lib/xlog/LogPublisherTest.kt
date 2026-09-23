package com.sd.lib.xlog

import org.junit.Assert.assertEquals
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
}

private fun newPublisher(
  dir: File,
  process: String? = null,
  storeFactory: FLogStore.Factory = FLogStore.Factory { defaultLogStore(it) },
): DirectoryLogPublisher {
  return defaultLogPublisher(
    processProvider = { process },
    directoryProvider = { dir },
    filename = defaultLogFilename(),
    formatter = defaultLogFormatter(),
    storeFactory = storeFactory,
  )
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

private fun File.createZip(): File {
  parentFile?.mkdirs()
  assertTrue(createNewFile())
  return this
}
