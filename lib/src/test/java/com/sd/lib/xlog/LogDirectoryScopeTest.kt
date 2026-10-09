package com.sd.lib.xlog

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipFile
import kotlin.random.Random

/** [LogDirectoryScopeImpl.logZipOf]、[accessDirectory]、[inputStreamOrNull]、[listFilesOrNull]和[copyLimitedTo] */
class LogDirectoryScopeTest {
  @get:Rule
  val folder = TemporaryFolder()

  /** 文件存在时正常打开 */
  @Test
  fun testExists() {
    val file = folder.newFile().apply { writeText("log") }
    assertEquals("log", file.inputStreamOrNull()?.use { it.readBytes().decodeToString() })
  }

  /** 文件在打开前被删除时返回null，打包时跳过 */
  @Test
  fun testDeleted() {
    val file = folder.newFile().apply { delete() }
    assertNull(file.inputStreamOrNull())
  }

  /** 文件存在但打不开时照常抛出，不能当作被删除跳过 */
  @Test(expected = FileNotFoundException::class)
  fun testOpenFailed() {
    folder.newFolder().inputStreamOrNull()
  }

  /** 压缩包里的路径是 <日期>/<进程名>/<日志文件>，日期和进程目录也有条目，打包后不留下临时文件 */
  @Test
  fun testLogZip() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p1")
    dir.createLog(DATE, "p2")

    val zip = checkNotNull(LogDirectoryScopeImpl(newPublisher(dir, process = "p1")).logZipOf(DATE))
    assertEquals(
      listOf("${DATE}/", "${DATE}/p1/", "${DATE}/p1/${DATE}.0.log", "${DATE}/p2/", "${DATE}/p2/${DATE}.0.log"),
      zip.zipEntryNames(),
    )
    assertEquals("log\n", zip.zipEntryText("${DATE}/p2/${DATE}.0.log"))
    assertEquals(listOf(zip.name), zip.parentFile?.list()?.toList())
  }

  /** 压缩包目录被同名文件占用时打包失败，返回null，不抛异常 */
  @Test
  fun testZipDirectoryOccupied() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p")
    val publisher = newPublisher(dir, process = "p")
    val zipDir = checkNotNull(publisher.zipFileOf(DATE)?.parentFile).apply {
      parentFile?.mkdirs()
      writeText("occupied")
    }

    assertNull(LogDirectoryScopeImpl(publisher).logZipOf(DATE))
    assertEquals("occupied", zipDir.readText())
  }

  /** 压缩包路径被同名目录占用时，删掉该目录后照常打包 */
  @Test
  fun testZipFileOccupiedByDirectory() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p")
    val publisher = newPublisher(dir, process = "p")
    val zipFile = checkNotNull(publisher.zipFileOf(DATE)).apply {
      mkdirs()
      resolve("child").writeText("child")
    }

    val zip = checkNotNull(LogDirectoryScopeImpl(publisher).logZipOf(DATE))
    assertEquals(zipFile, zip)
    assertEquals(true, zip.isFile)
    assertEquals(listOf("${DATE}/p/${DATE}.0.log"), zip.zipFileNames())
    assertEquals(listOf(zip.name), zip.parentFile?.list()?.toList())
  }

  /** 取不到日志目录时不执行block；取到时执行，block里能打包，离开后scope销毁 */
  @Test
  fun testAccessDirectory() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p")
    var directory: File? = null
    val publisher = defaultLogPublisher(
      processProvider = { "p" },
      directoryProvider = { directory },
      filename = defaultLogFilename(),
      formatter = defaultLogFormatter(),
      storeFactory = { defaultLogStore(it) },
    )

    var called = 0
    publisher.accessDirectory { called++ }
    assertEquals(0, called)

    directory = dir
    var received: File? = null
    var scope: FLogDirectoryScope? = null
    var zip: File? = null
    publisher.accessDirectory {
      received = it
      scope = this
      zip = logZipOf(DATE)
    }
    assertEquals(dir, received)
    assertEquals(true, zip?.isFile)
    assertNull(checkNotNull(scope).logZipOf(DATE))
  }

  /** block抛异常不往外抛，scope照样销毁，之后还能继续访问 */
  @Test
  fun testAccessDirectoryError() {
    val publisher = newPublisher(folder.newFolder(), process = "p")
    var scope: FLogDirectoryScope? = null
    publisher.accessDirectory {
      scope = this
      error("block error")
    }
    assertNull(checkNotNull(scope).logZipOf(DATE))

    var called = false
    publisher.accessDirectory { called = true }
    assertEquals(true, called)
  }

  /** 执行block前先关闭日志文件，所以之后的日志不省略tag */
  @Test
  fun testAccessDirectoryClose() {
    val dir = folder.newFolder()
    newPublisher(dir).use { publisher ->
      publisher.publish(testLogRecord(tag = "A"))
      publisher.accessDirectory { }
      publisher.publish(testLogRecord(tag = "A"))
    }

    val lines = dir.walkTopDown().first { it.isFile }.readLines()
    assertEquals(2, lines.size)
    assertEquals(true, lines[1].contains("[A|"))
  }

  /** 最多复制limit字节，提前读到末尾时停止 */
  @Test
  fun testCopyLimited() {
    val bytes = ByteArray(20_000) { it.toByte() }
    fun copy(limit: Long) = ByteArrayOutputStream().also { bytes.inputStream().copyLimitedTo(it, limit) }.toByteArray().toList()

    assertEquals(emptyList<Byte>(), copy(0))
    assertEquals(bytes.take(10_000), copy(10_000))
    assertEquals(bytes.toList(), copy(30_000))
  }

  /** 打包期间其他进程追加的内容不打包，否则写入不比压缩慢时一直读不完 */
  @Test
  fun testZipAppended() {
    val dir = folder.newFolder()
    val log = dir.createLog(DATE, "p")
    val scope = LogDirectoryScopeImpl(newPublisher(dir, process = "p"), openFile = { AppendOnReadInputStream(it) })

    val zip = checkNotNull(scope.logZipOf(DATE))
    // 确认读取时确实追加了，否则这个测试什么也没验证
    assertEquals("log\nnew\n", log.readText())
    assertEquals("log\n", zip.zipEntryText("${DATE}/p/${DATE}.0.log"))
  }

  /**
   * 打开之后被其他进程删除的文件，照样打包打开时的内容。
   * 长度要从句柄取，用file.length()会得到0。
   */
  @Test
  fun testZipDeletedAfterOpen() {
    val dir = folder.newFolder()
    val log = dir.createLog(DATE, "p")
    val scope = LogDirectoryScopeImpl(
      newPublisher(dir, process = "p"),
      openFile = { file -> file.inputStreamOrNull()?.also { file.delete() } },
    )

    val zip = checkNotNull(scope.logZipOf(DATE))
    // 确认打开之后确实删除了，否则这个测试什么也没验证
    assertEquals(false, log.exists())
    assertEquals("log\n", zip.zipEntryText("${DATE}/p/${DATE}.0.log"))
  }

  /** 列出之后、打开之前被其他进程删除的文件跳过，其他文件照常打包 */
  @Test
  fun testZipSkipDeleted() {
    val dir = folder.newFolder()
    val deleted = dir.createLog(DATE, "p1")
    dir.createLog(DATE, "p2")
    val scope = LogDirectoryScopeImpl(
      newPublisher(dir, process = "p1"),
      openFile = { file ->
        if (file == deleted) file.delete()
        file.inputStreamOrNull()
      },
    )

    val zip = checkNotNull(scope.logZipOf(DATE))
    // 确认打开前确实删除了，否则这个测试什么也没验证
    assertEquals(false, deleted.exists())
    assertEquals(listOf("${DATE}/p2/${DATE}.0.log"), zip.zipFileNames())
  }

  /** 日期不合法时返回null，即使有同名的目录；没有该日期的日志目录时也返回null */
  @Test
  fun testInvalidDate() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p")
    val scope = LogDirectoryScopeImpl(newPublisher(dir, process = "p"))

    // 给不合法的日期也建好目录，确认是被校验拦下的，而不是因为目录不存在
    for (date in listOf("2023112", "202311250", "2023112a", "abcdefgh", "２０２３１１２５")) {
      dir.createLog(date, "p")
      assertNull(date, scope.logZipOf(date))
    }
    assertNull(scope.logZipOf(""))
    assertNull(scope.logZipOf("20231126"))
  }

  /** 日期目录在检查之后被其他进程删除，返回null，不生成没有任何条目的压缩包 */
  @Test
  fun testDirectoryDeletedBeforeZip() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p")
    val real = newPublisher(dir, process = "p")
    val publisher = object : DirectoryLogPublisher by real {
      // 取压缩包路径在目录检查之后、打包之前，在这里删除目录
      override fun zipFileOf(date: String): File? {
        dir.resolve(date).deleteRecursively()
        return real.zipFileOf(date)
      }
    }

    assertNull(LogDirectoryScopeImpl(publisher).logZipOf(DATE))
    // 确认目录确实删除了，否则这个测试什么也没验证
    assertEquals(false, dir.resolve(DATE).exists())
    assertEquals(false, real.zipFileOf(DATE)?.parentFile?.exists())
  }

  /** 离开[FLog.logDirectory]之后返回null */
  @Test
  fun testDestroyed() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p")
    val scope = LogDirectoryScopeImpl(newPublisher(dir, process = "p"))
    scope.destroy()
    assertNull(scope.logZipOf(DATE))
  }

  /** 打包失败时返回null，保留上次的压缩包，不留下临时文件 */
  @Test
  fun testZipErrorKeepPrevious() {
    val dir = folder.newFolder()
    val log = dir.createLog(DATE, "p")
    val scope = LogDirectoryScopeImpl(newPublisher(dir, process = "p"))
    val zip = checkNotNull(scope.logZipOf(DATE))
    val bytes = zip.readBytes()

    // 日志文件存在但读不了，打包失败；以root运行时权限不生效，跳过
    assumeTrue(log.setReadable(false) && !log.canRead())
    try {
      assertNull(scope.logZipOf(DATE))
    } finally {
      log.setReadable(true)
    }

    assertEquals(bytes.toList(), zip.readBytes().toList())
    assertEquals(listOf(zip.name), zip.parentFile?.list()?.toList())
  }

  /** 已读取部分日志后打包失败，保留上次压缩包并删除本次临时文件 */
  @Test
  fun testReadErrorKeepPrevious() {
    val dir = folder.newFolder()
    val log = dir.createLog(DATE, "p")
    log.writeText("x".repeat(DEFAULT_BUFFER_SIZE * 3))
    val publisher = newPublisher(dir, process = "p")
    val zip = checkNotNull(LogDirectoryScopeImpl(publisher).logZipOf(DATE))
    val bytes = zip.readBytes()
    var readCount = 0
    var copiedBytes = 0
    val scope = LogDirectoryScopeImpl(publisher, openFile = { file ->
      object : FileInputStream(file) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
          if (++readCount == 2) throw IOException("read error")
          return super.read(b, off, len).also { copiedBytes += it }
        }
      }
    })

    assertNull(scope.logZipOf(DATE))
    assertEquals(2, readCount)
    assertEquals(DEFAULT_BUFFER_SIZE, copiedBytes)
    assertArrayEquals(bytes, zip.readBytes())
    assertEquals(listOf(zip.name), zip.parentFile?.list()?.toList())
  }

  /** 写入压缩包失败时关闭文件流，返回null，保留上次的压缩包，不留下临时文件 */
  @Test
  fun testWriteErrorCloseOutput() {
    val dir = folder.newFolder()
    // 随机内容压缩不了，写入量超过缓冲区，写到一半就失败
    dir.createLog(DATE, "p").writeBytes(Random(0).nextBytes(100_000))
    val publisher = newPublisher(dir, process = "p")
    val zip = checkNotNull(LogDirectoryScopeImpl(publisher).logZipOf(DATE))
    val bytes = zip.readBytes()

    var output: FailingOutputStream? = null
    val scope = LogDirectoryScopeImpl(publisher, openOutput = {
      FailingOutputStream(failAfter = 20_000).also { output = it }
    })

    assertNull(scope.logZipOf(DATE))
    assertEquals(true, output?.closed)
    assertArrayEquals(bytes, zip.readBytes())
    assertEquals(listOf(zip.name), zip.parentFile?.list()?.toList())
  }

  /** 最后重命名失败时返回null，保留上次的压缩包，不留下临时文件 */
  @Test
  fun testRenameErrorKeepPrevious() {
    val dir = folder.newFolder()
    val log = dir.createLog(DATE, "p")
    val publisher = newPublisher(dir, process = "p")
    val zip = checkNotNull(LogDirectoryScopeImpl(publisher).logZipOf(DATE))
    val bytes = zip.readBytes()

    // 日志有变化，这次打包出来的内容和上次不同
    log.appendText("new\n")
    var tempText: String? = null
    var renameTarget: File? = null
    val scope = LogDirectoryScopeImpl(publisher, rename = { source, target ->
      tempText = source.zipEntryText("${DATE}/p/${DATE}.0.log")
      renameTarget = target
      false
    })

    assertNull(scope.logZipOf(DATE))
    // 确认临时文件已经打包完整，是在重命名这一步失败的，否则这个测试什么也没验证
    assertEquals("log\nnew\n", tempText)
    assertEquals(zip, renameTarget)
    assertArrayEquals(bytes, zip.readBytes())
    assertEquals(listOf(zip.name), zip.parentFile?.list()?.toList())
  }

  /** 取不到进程名时多个进程共用压缩包目录，打包不能删除或替换其他进程的临时文件 */
  @Test
  fun testKeepOtherTempFile() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p")
    val publisher = newPublisher(dir, process = null)
    val zipFile = checkNotNull(publisher.zipFileOf(DATE))
    val otherTemp = zipFile.resolveSibling("${zipFile.name}.tmp").apply {
      parentFile?.mkdirs()
      writeText("other")
    }

    val zip = checkNotNull(LogDirectoryScopeImpl(publisher).logZipOf(DATE))
    assertEquals(listOf("${DATE}/p/${DATE}.0.log"), zip.zipFileNames())
    assertEquals("other", otherTemp.readText())
    assertEquals(setOf(zip.name, otherTemp.name), zip.parentFile?.list()?.toSet())
  }

  /** 所在目录没有执行权限时读不到文件属性，这些文件按已删除跳过，打包不失败 */
  @Test
  fun testZipSkipUnreadableAttributes() {
    val dir = folder.newFolder()
    val log = dir.createLog(DATE, "p")
    val processDir = checkNotNull(log.parentFile)
    val scope = LogDirectoryScopeImpl(newPublisher(dir, process = "p"))

    // 以root运行时权限不生效，跳过
    assumeTrue(processDir.setExecutable(false) && !log.isFile)
    try {
      val zip = checkNotNull(scope.logZipOf(DATE))
      assertEquals(listOf("${DATE}/", "${DATE}/p/"), zip.zipEntryNames())
    } finally {
      processDir.setExecutable(true)
    }
  }

  /** 目录存在时正常列出 */
  @Test
  fun testListExists() {
    val dir = folder.newFolder().apply { resolve("log").writeText("log") }
    assertEquals(listOf("log"), dir.listFilesOrNull()?.map { it.name })
  }

  /** 目录在列出前被删除时返回null，打包时跳过 */
  @Test
  fun testListDeleted() {
    val dir = folder.newFolder().apply { delete() }
    assertNull(dir.listFilesOrNull())
  }

  /** 目录存在但读不了时打包失败，返回null，保留上次的压缩包，不留下临时文件 */
  @Test
  fun testListErrorKeepPrevious() {
    val dir = folder.newFolder()
    val processDir = checkNotNull(dir.createLog(DATE, "p").parentFile)
    val scope = LogDirectoryScopeImpl(newPublisher(dir, process = "p"))
    val zip = checkNotNull(scope.logZipOf(DATE))
    val bytes = zip.readBytes()

    // 以root运行时权限不生效，跳过
    assumeTrue(processDir.setReadable(false) && processDir.listFiles() == null)
    try {
      assertNull(scope.logZipOf(DATE))
    } finally {
      processDir.setReadable(true)
    }

    assertEquals(bytes.toList(), zip.readBytes().toList())
    assertEquals(listOf(zip.name), zip.parentFile?.list()?.toList())
  }
}

private const val DATE = "20231125"

/** 按真实的目录结构创建日志：<日期>/<进程名>/<日期>.0.log */
private fun File.createLog(date: String, process: String): File {
  return resolve(date).resolve(process).resolve("${date}.0.log").apply {
    parentFile?.mkdirs()
    writeText("log\n")
  }
}

/** 压缩包里的文件条目，不包括目录，按名称排序 */
private fun File.zipFileNames(): List<String> {
  return ZipFile(this).use { zip -> zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.sorted().toList() }
}

/** 压缩包里的全部条目，包括目录，按名称排序 */
private fun File.zipEntryNames(): List<String> {
  return ZipFile(this).use { zip -> zip.entries().asSequence().map { it.name }.sorted().toList() }
}

/** 第一次读取前往文件追加一行，模拟打包期间其他进程还在写 */
private class AppendOnReadInputStream(private val file: File) : FileInputStream(file) {
  private var _appended = false

  override fun read(): Int {
    appendOnce()
    return super.read()
  }

  override fun read(b: ByteArray): Int {
    appendOnce()
    return super.read(b)
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    appendOnce()
    return super.read(b, off, len)
  }

  private fun appendOnce() {
    if (_appended) return
    _appended = true
    file.appendText("new\n")
  }
}

/** 写入超过[failAfter]字节之后一直失败，模拟磁盘写满 */
private class FailingOutputStream(private val failAfter: Int) : OutputStream() {
  private var _count = 0

  var closed = false
    private set

  override fun write(b: Int) {
    if (_count >= failAfter) throw IOException("disk full")
    _count++
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    if (_count + len > failAfter) throw IOException("disk full")
    _count += len
  }

  override fun close() {
    closed = true
  }
}

/** 压缩包里[name]条目的内容 */
private fun File.zipEntryText(name: String): String {
  return ZipFile(this).use { zip -> zip.getInputStream(zip.getEntry(name)).use { it.readBytes().decodeToString() } }
}
