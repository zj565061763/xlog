package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

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

    try {
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
    } finally {
      publisher.close()
    }
  }

  /** 调小上限后下一条日志就切换，超限的旧文件保留到下次切换才删除；上限设为0或负数后不再切换 */
  @Test
  fun testMaxByteChange() {
    val dir = folder.newFolder()
    val publisher = newPublisher(dir)
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val logDir = dir.resolve(date)

    try {
      // 不限制时10条都写在序号0里，首条61字节，之后省略tag每条59字节，共592字节
      repeat(10) { publisher.publish(testLogRecord()) }
      assertEquals(listOf(filename.logNameOf(date, 0)), dir.logNames())

      // 调小到400，下一条写入后超过一半就切换；新文件惰性创建，再写一条才出现
      publisher.setMaxBytePerDay(400)
      publisher.publish(testLogRecord())
      assertEquals(listOf(filename.logNameOf(date, 0)), dir.logNames())
      publisher.publish(testLogRecord())
      assertEquals(listOf(filename.logNameOf(date, 0), filename.logNameOf(date, 1)), dir.logNames())
      // 超限的序号0不额外删除，留到下次切换
      assertTrue(logDir.resolve(filename.logNameOf(date, 0)).length() > 400)

      // 序号1写满4条切到序号2，这时才删除序号0
      repeat(3) { publisher.publish(testLogRecord()) }
      assertEquals(listOf(filename.logNameOf(date, 1)), dir.logNames())
      publisher.publish(testLogRecord())
      assertEquals(listOf(filename.logNameOf(date, 1), filename.logNameOf(date, 2)), dir.logNames())

      // 取消限制后一直写序号2，不再切换
      publisher.setMaxBytePerDay(0)
      repeat(10) { publisher.publish(testLogRecord()) }
      assertEquals(listOf(filename.logNameOf(date, 1), filename.logNameOf(date, 2)), dir.logNames())
      assertEquals(11, logDir.resolve(filename.logNameOf(date, 2)).readLines().size)

      // 负数同样表示不限制
      publisher.setMaxBytePerDay(-1)
      repeat(10) { publisher.publish(testLogRecord()) }
      assertEquals(listOf(filename.logNameOf(date, 1), filename.logNameOf(date, 2)), dir.logNames())
      assertEquals(21, logDir.resolve(filename.logNameOf(date, 2)).readLines().size)
    } finally {
      publisher.close()
    }
  }

  /** 当前文件大小恰好等于上限的一半时就切换，小于时不切换 */
  @Test
  fun testRotateBoundary() {
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)

    /** 上限为[maxByte]时写两条日志，返回剩下的日志文件 */
    fun logNamesAfterTwoLogs(maxByte: Long): List<String> {
      val dir = folder.newFolder()
      newPublisher(dir).use { publisher ->
        publisher.setMaxBytePerDay(maxByte)
        publisher.publish(testLogRecord())
        publisher.publish(testLogRecord())
      }
      return dir.logNames()
    }

    // 首条日志61字节，上限122的一半恰好是61：第1条写完就切换，第2条写进序号1，写完再切换并删除序号0
    assertEquals(listOf(filename.logNameOf(date, 1)), logNamesAfterTwoLogs(122))
    // 上限124的一半是62：第1条不切换，第2条仍写进序号0
    assertEquals(listOf(filename.logNameOf(date, 0)), logNamesAfterTwoLogs(124))
  }

  /** 轮换按字节数判断，不是字符数 */
  @Test
  fun testRotateMultiByte() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)

    newPublisher(dir).use { publisher ->
      // 50个汉字150字节，加上21字节的前缀和换行共171字节，超过上限300的一半；按字符数算只有71，不会切换
      publisher.setMaxBytePerDay(300)
      publisher.publish(testLogRecord(msg = "中".repeat(50)))
      publisher.publish(testLogRecord(msg = "tail"))
    }

    assertEquals(listOf(filename.logNameOf(date, 0), filename.logNameOf(date, 1)), dir.logNames())
    assertEquals(171L, dir.resolve(date).resolve(filename.logNameOf(date, 0)).length())
  }

  /** 空闲时文件还在就不关闭，格式化器状态保留，下一条相同tag的日志省略tag */
  @Test
  fun testIdleFileExists() {
    val dir = folder.newFolder()
    val publisher = newPublisher(dir)
    try {
      publisher.publish(testLogRecord(tag = "A"))
      publisher.onIdle()
      publisher.publish(testLogRecord(tag = "A"))

      val lines = dir.walkTopDown().first { it.isFile }.readLines()
      assertEquals(2, lines.size)
      assertTrue(lines[1], lines[1].startsWith("${LogTime.timeOf(RECORD_MILLIS)}[I|"))
    } finally {
      publisher.close()
    }
  }

  /** 日志文件被外部删除后，空闲时关闭，下一条日志重建文件、首条带tag，大小从0重新计数 */
  @Test
  fun testIdleFileDeleted() {
    val dir = folder.newFolder()
    val publisher = newPublisher(dir)
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val logFile = dir.resolve(date).resolve(filename.logNameOf(date, 0))
    // 上限580，写满290字节切换；首条61字节，之后省略tag每条59字节，5条切换
    publisher.setMaxBytePerDay(580)

    try {
      repeat(3) { publisher.publish(testLogRecord()) }
      assertTrue(logFile.delete())

      // 写到已删除的句柄上，文件不会重建，等空闲时才关闭
      publisher.publish(testLogRecord(msg = "lost"))
      assertEquals(false, logFile.exists())
      publisher.onIdle()

      // 重建后从0计数：如果沿用删除前的计数，第2条就会切换，第3条会出现序号1
      repeat(3) { publisher.publish(testLogRecord()) }
      assertEquals(listOf(filename.logNameOf(date, 0)), dir.logNames())
      val lines = logFile.readLines()
      assertEquals(3, lines.size)
      assertTrue(lines[0], lines[0].contains("[T|"))

      // 重建后的第5条写满，第6条出现序号1，说明计数是从重建开始的
      repeat(2) { publisher.publish(testLogRecord()) }
      assertEquals(listOf(filename.logNameOf(date, 0)), dir.logNames())
      publisher.publish(testLogRecord())
      assertEquals(listOf(filename.logNameOf(date, 0), filename.logNameOf(date, 1)), dir.logNames())
    } finally {
      publisher.close()
    }
  }

  /** 切换后新文件还没创建时空闲，不影响后续：旧文件不删，下一条写到新序号且带tag */
  @Test
  fun testIdleAfterRotate() {
    val dir = folder.newFolder()
    val publisher = newPublisher(dir)
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    publisher.setMaxBytePerDay(400)

    try {
      repeat(4) { publisher.publish(testLogRecord()) }
      assertEquals(listOf(filename.logNameOf(date, 0)), dir.logNames())

      publisher.onIdle()
      assertEquals(listOf(filename.logNameOf(date, 0)), dir.logNames())

      publisher.publish(testLogRecord(msg = "next"))
      assertEquals(listOf(filename.logNameOf(date, 0), filename.logNameOf(date, 1)), dir.logNames())
      assertEquals(4, dir.resolve(date).resolve(filename.logNameOf(date, 0)).readLines().size)
      val text = dir.resolve(date).resolve(filename.logNameOf(date, 1)).readText()
      assertTrue(text, text.contains("[T|"))
      assertTrue(text, text.endsWith("] next\n"))
    } finally {
      publisher.close()
    }
  }

  /** 关闭后继续写，接着当前序号写入，首条带tag */
  @Test
  fun testPublishAfterClose() {
    val dir = folder.newFolder()
    val publisher = newPublisher(dir)
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    publisher.setMaxBytePerDay(400)

    try {
      // 4条写满序号0，第5条写进序号1
      repeat(5) { publisher.publish(testLogRecord()) }
      publisher.close()
      publisher.publish(testLogRecord(msg = "after close"))

      assertEquals(listOf(filename.logNameOf(date, 0), filename.logNameOf(date, 1)), dir.logNames())
      val lines = dir.resolve(date).resolve(filename.logNameOf(date, 1)).readLines()
      assertEquals(2, lines.size)
      assertTrue(lines[1], lines[1].contains("[T|"))
      assertTrue(lines[1], lines[1].endsWith("] after close"))
    } finally {
      publisher.close()
    }
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

    try {
      var failures = 0
      repeat(20) {
        runCatching { publisher.publish(testLogRecord()) }.onFailure { failures++ }
      }

      // 创建失败的时候日志写不进去，但是已有文件不能因此无限增长
      val totalSize = dir.totalSize()
      assertTrue("日志总大小${totalSize}字节，超过了上限${maxByte}字节", totalSize <= maxByte)
      assertTrue("应该有写入失败", failures > 0)
    } finally {
      publisher.close()
    }
  }

  /** 轮换时创建仓库暂时失败，恢复后继续写新文件，不回写旧文件 */
  @Test
  fun testStoreCreateRecovery() {
    val dir = folder.newFolder()
    var createCount = 0
    val publisher = newPublisher(dir) { file ->
      if (++createCount == 2) error("create error")
      defaultLogStore(file)
    }
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val logDir = dir.resolve(date)
    publisher.setMaxBytePerDay(100)

    try {
      publisher.publish(testLogRecord(msg = "x".repeat(80)))
      val oldFile = logDir.resolve(filename.logNameOf(date, 0))
      val oldText = oldFile.readText()
      assertThrows(IllegalStateException::class.java) { publisher.publish(testLogRecord(msg = "lost")) }
      publisher.publish(testLogRecord(msg = "recovered"))

      assertEquals(3, createCount)
      assertEquals(oldText, oldFile.readText())
      val text = logDir.resolve(filename.logNameOf(date, 1)).readText()
      assertTrue(text, text.contains("[T|"))
      assertTrue(text, text.endsWith("] recovered\n"))

      publisher.publish(testLogRecord(msg = "x".repeat(80)))
      publisher.publish(testLogRecord(msg = "tail"))
      assertEquals(listOf(filename.logNameOf(date, 1), filename.logNameOf(date, 2)), dir.logNames())
    } finally {
      publisher.close()
    }
  }

  /** 仓库关闭时抛异常不阻断轮换，新文件首条日志仍包含tag */
  @Test
  fun testStoreCloseErrorOnRotate() {
    val dir = folder.newFolder()
    var closeCount = 0
    val publisher = newPublisher(dir) { file ->
      val store = defaultLogStore(file)
      object : FLogStore by store {
        override fun close() {
          store.close()
          closeCount++
          error("close error")
        }
      }
    }
    publisher.setMaxBytePerDay(100)

    try {
      repeat(3) { publisher.publish(testLogRecord(msg = "x".repeat(80))) }
      publisher.publish(testLogRecord(msg = "tail"))

      val filename = defaultLogFilename()
      val date = filename.dateOf(RECORD_MILLIS)
      assertEquals(3, closeCount)
      assertEquals(listOf(filename.logNameOf(date, 2), filename.logNameOf(date, 3)), dir.logNames())
      val text = dir.resolve(date).resolve(filename.logNameOf(date, 3)).readText()
      assertTrue(text, text.contains("[T|"))
      assertTrue(text, text.endsWith("] tail\n"))
    } finally {
      publisher.close()
    }
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

    try {
      publisher.publish(testLogRecord(tag = "A"))
      runCatching { publisher.publish(testLogRecord(tag = "B")) }
      publisher.publish(testLogRecord(tag = "B"))

      val lines = dir.walkTopDown().first { it.isFile }.readLines()
      assertEquals(2, lines.size)
      assertTrue(lines[1], lines[1].contains("[B|"))
    } finally {
      publisher.close()
    }
  }

  /** 写入失败之后关闭仓库，下一条日志重新打开再写入 */
  @Test
  fun testAppendErrorCloseStore() {
    val dir = folder.newFolder()
    var appendCount = 0
    var closeCount = 0

    val publisher = newPublisher(dir) { file ->
      val store = defaultLogStore(file)
      object : FLogStore by store {
        override fun append(log: String) {
          // 第2条日志写入失败
          if (++appendCount == 2) error("append error")
          store.append(log)
        }

        override fun close() {
          closeCount++
          store.close()
        }
      }
    }

    try {
      publisher.publish(testLogRecord(msg = "one"))
      assertEquals(0, closeCount)
      assertThrows(IllegalStateException::class.java) { publisher.publish(testLogRecord(msg = "lost")) }
      assertEquals(1, closeCount)

      publisher.publish(testLogRecord(msg = "two"))
      val lines = dir.walkTopDown().first { it.isFile }.readLines()
      assertEquals(listOf("one", "two"), lines.map { it.substringAfter("] ") })
    } finally {
      publisher.close()
    }
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

    try {
      publisher.publish(testLogRecord(tag = "A"))
      runCatching { publisher.publish(testLogRecord(tag = "B")) }
      publisher.publish(testLogRecord(tag = "B"))

      val lines = dir.walkTopDown().first { it.isFile }.readLines()
      assertEquals(2, lines.size)
      assertTrue(lines[1], lines[1].contains("[B|"))
    } finally {
      publisher.close()
    }
  }

  /** 获取大小失败后恢复写入和轮换，下一条日志不省略tag */
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

    publisher.setMaxBytePerDay(400)
    try {
      assertThrows(IllegalStateException::class.java) { publisher.publish(testLogRecord(msg = "first")) }
      assertEquals(1, storeCloseCount)
      assertEquals(1, formatter.resetCount)

      publisher.publish(testLogRecord(msg = "recovered"))
      val lines = dir.walkTopDown().first { it.isFile }.readLines()
      assertEquals(listOf("first", "recovered"), lines.map { it.substringAfter("] ") })
      assertTrue(lines[1], lines[1].contains("[T|"))

      repeat(3) { publisher.publish(testLogRecord(msg = "x".repeat(200))) }
      publisher.publish(testLogRecord(msg = "tail"))
      val filename = defaultLogFilename()
      val date = filename.dateOf(RECORD_MILLIS)
      assertEquals(listOf(filename.logNameOf(date, 2), filename.logNameOf(date, 3)), dir.logNames())
      val text = dir.resolve(date).resolve(filename.logNameOf(date, 3)).readText()
      assertTrue(text, text.contains("[T|"))
      assertTrue(text, text.endsWith("] tail\n"))
    } finally {
      publisher.close()
    }
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

    try {
      // 第一次用到时获取，之后不再重复获取
      publisher.publish(testLogRecord())
      publisher.publish(testLogRecord())
      assertEquals(1, processCount)
      assertEquals(1, directoryCount)
    } finally {
      publisher.close()
    }
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

    try {
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
    } finally {
      publisher.close()
    }
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

  /** 日期目录、进程目录被同名文件占用时，删掉文件再创建目录，日志照常写入 */
  @Test
  fun testReplaceOccupiedDir() {
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val logName = filename.logNameOf(date, 0)

    // 日期目录被占用
    val dateOccupied = folder.newFolder().apply { resolve(date).writeText("occupied") }
    newPublisher(dateOccupied, process = "p").use { it.publish(testLogRecord()) }
    assertEquals(1, dateOccupied.resolve(date).resolve("p").resolve(logName).readLines().size)

    // 进程目录被占用
    val processOccupied = folder.newFolder().apply {
      resolve(date).mkdirs()
      resolve(date).resolve("p").writeText("occupied")
    }
    newPublisher(processOccupied, process = "p").use { it.publish(testLogRecord()) }
    assertEquals(1, processOccupied.resolve(date).resolve("p").resolve(logName).readLines().size)

    // 取不到进程名时日志直接写在日期目录下
    val noProcess = folder.newFolder().apply { resolve(date).writeText("occupied") }
    newPublisher(noProcess).use { it.publish(testLogRecord()) }
    assertEquals(1, noProcess.resolve(date).resolve(logName).readLines().size)
  }

  /** 运行中日期目录被同名文件占用，空闲时发现日志文件不存在就删掉它，下一条日志重建 */
  @Test
  fun testIdleOccupiedDir() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val publisher = newPublisher(dir, process = "p")

    try {
      publisher.publish(testLogRecord())
      assertTrue(dir.resolve(date).deleteRecursively())
      dir.resolve(date).writeText("occupied")

      publisher.onIdle()
      publisher.publish(testLogRecord(msg = "rebuilt"))

      val lines = dir.resolve(date).resolve("p").resolve(filename.logNameOf(date, 0)).readLines()
      assertEquals(listOf("rebuilt"), lines.map { it.substringAfter("] ") })
    } finally {
      publisher.close()
    }
  }

  /** 日志目录本身或它上层的路径被文件占用时不删除，日志写不进去；这些路径不属于日志库 */
  @Test
  fun testKeepFileOutsideDirectory() {
    val occupied = folder.newFolder().resolve("data").apply { writeText("data") }

    for (dir in listOf(occupied, occupied.resolve("nested").resolve("logs"))) {
      val publisher = newPublisher(dir, process = "p")
      assertThrows(IOException::class.java) { publisher.publish(testLogRecord()) }
      publisher.onIdle()
      assertThrows(IOException::class.java) { publisher.publish(testLogRecord()) }
      publisher.close()
      assertEquals("data", occupied.readText())
    }
  }

  /** 轮换时删除旧文件失败只打印不重试，不抛异常，也不影响继续写入 */
  @Test
  fun testDeleteOldLogFailed() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val logDir = dir.resolve(date).apply { mkdirs() }
    // 序号0被非空目录占用，删不掉
    val occupied = logDir.resolve(filename.logNameOf(date, 0)).apply {
      mkdirs()
      resolve("child").writeText("child")
    }
    // 上一次运行已经写到200字节，再写一条就超过一半上限
    logDir.resolve(filename.logNameOf(date, 2)).writeText("0".repeat(199) + "\n")

    val publisher = newPublisher(dir)
    publisher.setMaxBytePerDay(400)

    try {
      // 写进序号2之后切换，删除序号0失败，继续写序号3
      publisher.publish(testLogRecord())
      publisher.publish(testLogRecord())
      assertEquals(listOf(0, 2, 3).map { filename.logNameOf(date, it) }, logDir.list()?.sortedBy { filename.seqOf(it) })
      assertEquals("child", occupied.resolve("child").readText())
    } finally {
      publisher.close()
    }
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

    newPublisher(dir).use { it.publish(testLogRecord()) }

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

    try {
      // 写进序号3之后切换，删除序号1和2，新文件惰性创建，还没有出现
      publisher.publish(testLogRecord())
      assertEquals(listOf(filename.logNameOf(date, 3)), dir.logNames())

      publisher.publish(testLogRecord())
      assertEquals(listOf(filename.logNameOf(date, 3), filename.logNameOf(date, 4)), dir.logNames())
    } finally {
      publisher.close()
    }
  }

  /** 进程目录里不是日志的文件不参与序号扫描，轮换时也不删除 */
  @Test
  fun testIgnoreNonLogFile() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val logDir = dir.resolve(date).apply { mkdirs() }
    // 旧版本的分片文件和其他文件，名字里的数字比当前序号大，不能被当作序号
    val others = listOf("${date}.log.9", "${date}.9.zip", "notes.txt").map { logDir.resolve(it).apply { writeText("other\n") } }
    // 上一次运行已经写到200字节，再写一条就超过一半上限
    logDir.resolve(filename.logNameOf(date, 0)).writeText("0".repeat(199) + "\n")

    val publisher = newPublisher(dir)
    publisher.setMaxBytePerDay(400)

    try {
      // 第1条写进序号0后切换，第2到5条写满序号1后切换并删除序号0，第6条写进序号2
      repeat(6) { publisher.publish(testLogRecord()) }
      assertEquals(listOf(1, 2).map { filename.logNameOf(date, it) }, logDir.logNamesOf(filename))
      assertEquals(listOf("other\n", "other\n", "other\n"), others.map { it.readText() })
    } finally {
      publisher.close()
    }
  }

  /** 刚切换、新文件还没创建时关闭再打开，扫描到的最大序号仍是写满的文件，往里多写一条再切换 */
  @Test
  fun testCloseAfterRotate() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val logDir = dir.resolve(date)
    val publisher = newPublisher(dir)
    publisher.setMaxBytePerDay(400)

    try {
      // 4条写满序号0并切换，序号1还没创建
      repeat(4) { publisher.publish(testLogRecord()) }
      publisher.close()
      assertEquals(listOf(filename.logNameOf(date, 0)), dir.logNames())

      // 重新打开后接着序号0写，首条带tag，写完再次切换
      publisher.publish(testLogRecord(msg = "extra"))
      assertEquals(listOf(filename.logNameOf(date, 0)), dir.logNames())
      val lines = logDir.resolve(filename.logNameOf(date, 0)).readLines()
      assertEquals(5, lines.size)
      assertTrue(lines[4], lines[4].contains("[T|"))
      assertTrue(lines[4], lines[4].endsWith("] extra"))

      publisher.publish(testLogRecord(msg = "next"))
      assertEquals(listOf(filename.logNameOf(date, 0), filename.logNameOf(date, 1)), dir.logNames())
      val text = logDir.resolve(filename.logNameOf(date, 1)).readText()
      assertTrue(text, text.endsWith("] next\n"))
    } finally {
      publisher.close()
    }
  }

  /** 跨天时写到新日期的目录，新文件的第一条日志不能省略tag */
  @Test
  fun testDateChange() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val nextDayMillis = RECORD_MILLIS + 24 * 60 * 60 * 1000L
    val date = filename.dateOf(RECORD_MILLIS)
    val nextDate = filename.dateOf(nextDayMillis)

    newPublisher(dir).use { publisher ->
      publisher.publish(testLogRecord())
      publisher.publish(testLogRecord(millis = nextDayMillis))
    }

    assertEquals(1, dir.resolve(date).resolve(filename.logNameOf(date, 0)).readLines().size)
    val nextLines = dir.resolve(nextDate).resolve(filename.logNameOf(nextDate, 0)).readLines()
    assertEquals(1, nextLines.size)
    assertTrue(nextLines[0], nextLines[0].contains("[T|"))
  }

  /** 时间回拨到前一天时写回旧日期的目录，接着它的序号写，再回到当天也正常，切换目录后首条都带tag */
  @Test
  fun testDateBackward() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val prevDayMillis = RECORD_MILLIS - 24 * 60 * 60 * 1000L
    val date = filename.dateOf(RECORD_MILLIS)
    val prevDate = filename.dateOf(prevDayMillis)
    val prevFile = dir.resolve(prevDate).resolve(filename.logNameOf(prevDate, 2)).apply {
      parentFile?.mkdirs()
      writeText("old\n")
    }

    newPublisher(dir).use { publisher ->
      publisher.publish(testLogRecord(msg = "today"))
      publisher.publish(testLogRecord(millis = prevDayMillis, msg = "backward"))
      publisher.publish(testLogRecord(msg = "forward"))
    }

    val prevLines = prevFile.readLines()
    assertEquals(listOf("old", "backward"), prevLines.map { it.substringAfter("] ") })
    assertTrue(prevLines[1], prevLines[1].contains("[T|"))
    assertEquals(listOf(filename.logNameOf(prevDate, 2)), dir.resolve(prevDate).list()?.toList())

    val lines = dir.resolve(date).resolve(filename.logNameOf(date, 0)).readLines()
    assertEquals(listOf("today", "forward"), lines.map { it.substringAfter("] ") })
    assertTrue(lines[1], lines[1].contains("[T|"))
  }

  /** 日志按进程名分子目录，进程名里的:替换为- */
  @Test
  fun testProcessDir() {
    val dir = folder.newFolder()
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)

    newPublisher(dir, process = "com.sd.demo:remote").use { it.publish(testLogRecord()) }

    assertEquals(true, dir.resolve(date).resolve("com.sd.demo-remote").resolve(filename.logNameOf(date, 0)).isFile)
  }

  /** 私有进程和名字相近的全局进程不能共用目录，否则日志混写，init时还会清空对方的压缩包 */
  @Test
  fun testProcessDirConflict() {
    val dir = folder.newFolder()
    val date = defaultLogFilename().dateOf(RECORD_MILLIS)
    val privateProcess = newPublisher(dir, process = "com.sd.demo:remote")
    val globalProcess = newPublisher(dir, process = "com.sd.demo_remote")

    try {
      privateProcess.publish(testLogRecord())
      globalProcess.publish(testLogRecord())
      assertEquals(listOf("com.sd.demo-remote", "com.sd.demo_remote"), dir.resolve(date).list()?.sorted())

      val globalZip = globalProcess.zipFileOf(date).createZip()
      privateProcess.deleteZipDirectory()
      assertEquals(true, globalZip.exists())
    } finally {
      privateProcess.close()
      globalProcess.close()
    }
  }

  /** 进程名为空串时按取不到处理；含路径分隔符或者是.和..时会跳出所在目录，也按取不到处理 */
  @Test
  fun testInvalidProcessName() {
    val filename = defaultLogFilename()
    val date = filename.dateOf(RECORD_MILLIS)
    val outside = folder.newFolder()

    for (process in listOf("", ".", "..", "a/../..", outside.absolutePath)) {
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

/** 目录下直接存放的日志文件名，按序号排序，不包括其他文件 */
private fun File.logNamesOf(filename: LogFilename): List<String> {
  return list()?.filter { filename.seqOf(it) != null }?.sortedBy { filename.seqOf(it) } ?: emptyList()
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
