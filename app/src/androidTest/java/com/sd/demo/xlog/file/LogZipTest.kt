package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.dateOfDaysAgo
import com.sd.demo.xlog.fCreateFile
import com.sd.demo.xlog.resetLogDir
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * 日志压缩包不参与日志保留策略
 */
@RunWith(AndroidJUnit4::class)
class LogZipTest {

  @Test
  fun test() {
    FLog.setLevel(FLogLevel.All)

    val dir = resetLogDir()
    flogI<TestLogger> { "info" }
    awaitLogIdle()

    val today = dateOfDaysAgo(0)

    var zip: File? = null
    FLog.logDirectory { zip = logZipOf(today) }
    awaitLogIdle()
    assertEquals(true, zip?.exists())
    assertEquals(true, (zip?.length() ?: 0) > 0)

    // 压缩包在.开头的内部目录里，不在日志根目录下
    assertEquals(true, zip!!.startsWith(dir))
    assertEquals(
      true,
      generateSequence(zip!!.parentFile) { it.parentFile }
        .any { it.name.startsWith(".") },
    )

    // 只保留当天的日志，压缩包不受影响
    kotlin.run {
      FLog.deleteLog(1)
      awaitLogIdle()
      assertEquals(true, dir.resolve(today).exists())
      assertEquals(true, zip!!.exists())
    }

    /**
     * 删除全部日志，压缩包依然保留。
     * 使用方常见的用法就是导出压缩包之后立即清空日志，再去上传压缩包
     */
    kotlin.run {
      FLog.deleteLog(0)
      awaitLogIdle()
      assertEquals(false, dir.resolve(today).exists())
      assertEquals(true, zip!!.exists())
      assertEquals(true, dir.exists())
    }
  }

  /** 同一日期再次打包时替换上次的压缩包，不留下临时文件 */
  @Test
  fun testRepeat() {
    resetLogDir()
    flogI<TestLogger> { "info" }
    awaitLogIdle()

    val today = dateOfDaysAgo(0)
    var zip1: File? = null
    var zip2: File? = null
    FLog.logDirectory {
      zip1 = logZipOf(today)
      zip2 = logZipOf(today)
    }
    awaitLogIdle()

    assertEquals(zip1, zip2)
    assertEquals(true, ZipFile(zip2!!).use { zip -> zip.entries().asSequence().any { !it.isDirectory } })
    assertEquals(listOf(zip2!!.name), zip2!!.parentFile?.list()?.toList())
  }

  /** 日期目录里没有日志文件时返回不含日志的压缩包，日期对应的不是目录时返回null */
  @Test
  fun testNoLog() {
    val dir = resetLogDir()
    val emptyDate = dateOfDaysAgo(1)
    val fileDate = dateOfDaysAgo(2)
    assertEquals(true, dir.resolve(emptyDate).resolve("process").mkdirs())
    assertEquals(true, dir.resolve(fileDate).fCreateFile())

    // block里的断言失败会被捕获，所以把结果带出来再断言
    var emptyZip: File? = null
    var fileZip: File? = null
    FLog.logDirectory {
      emptyZip = logZipOf(emptyDate)
      fileZip = logZipOf(fileDate)
    }
    awaitLogIdle()
    assertEquals(true, emptyZip?.exists())
    assertEquals(false, ZipFile(emptyZip!!).use { zip -> zip.entries().asSequence().any { !it.isDirectory } })
    assertEquals(null, fileZip)
  }
}
