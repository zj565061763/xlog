package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile

/** [LogDirectoryScopeImpl.logZipOf]、[inputStreamOrNull]、[listFilesOrNull]和[copyLimitedTo] */
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

  /** 压缩包里的路径是 <日期>/<进程名>/<日志文件>，打包后不留下临时文件 */
  @Test
  fun testLogZip() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p1")
    dir.createLog(DATE, "p2")

    val zip = checkNotNull(LogDirectoryScopeImpl(newPublisher(dir, process = "p1")).logZipOf(DATE))
    assertEquals(listOf("${DATE}/p1/${DATE}.0.log", "${DATE}/p2/${DATE}.0.log"), zip.zipFileNames())
    assertEquals("log\n", zip.zipEntryText("${DATE}/p2/${DATE}.0.log"))
    assertEquals(listOf(zip.name), zip.parentFile?.list()?.toList())
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

  /** 其他进程在打开之后追加的内容不复制，否则写入不比压缩慢时一直读不完 */
  @Test
  fun testCopyAppended() {
    val file = folder.newFile().apply { writeText("log\n") }
    val out = ByteArrayOutputStream()
    checkNotNull(file.inputStreamOrNull()).use { input ->
      val size = input.channel.size()
      file.appendText("new\n")
      input.copyLimitedTo(out, size)
    }
    assertEquals("log\n", out.toString())
  }

  /** 日期不合法、没有该日期的日志目录时返回null */
  @Test
  fun testInvalidDate() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p")
    val scope = LogDirectoryScopeImpl(newPublisher(dir, process = "p"))

    for (date in listOf("", "2023112", "202311250", "2023112a", "abcdefgh")) {
      assertNull(date, scope.logZipOf(date))
    }
    assertNull(scope.logZipOf("20231126"))
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

  /** 取不到进程名时多个进程共用压缩包目录，打包不能删除或替换其他进程的临时文件 */
  @Test
  fun testKeepOtherTempFile() {
    val dir = folder.newFolder()
    dir.createLog(DATE, "p")
    val publisher = newPublisher(dir, process = null)
    val zipFile = publisher.zipFileOf(DATE)
    val otherTemp = zipFile.resolveSibling("${zipFile.name}.tmp").apply {
      parentFile?.mkdirs()
      writeText("other")
    }

    val zip = checkNotNull(LogDirectoryScopeImpl(publisher).logZipOf(DATE))
    assertEquals(listOf("${DATE}/p/${DATE}.0.log"), zip.zipFileNames())
    assertEquals("other", otherTemp.readText())
    assertEquals(setOf(zip.name, otherTemp.name), zip.parentFile?.list()?.toSet())
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

/** 压缩包里[name]条目的内容 */
private fun File.zipEntryText(name: String): String {
  return ZipFile(this).use { zip -> zip.getInputStream(zip.getEntry(name)).use { it.readBytes().decodeToString() } }
}
