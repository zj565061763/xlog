package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile

/** [LogDirectoryScopeImpl.logZipOf]和[inputStreamOrNull] */
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
    assertEquals(listOf(zip.name), zip.parentFile?.list()?.toList())
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
