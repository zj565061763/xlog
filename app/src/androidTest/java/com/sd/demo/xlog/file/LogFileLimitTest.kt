package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.dateOfDaysAgo
import com.sd.demo.xlog.resetLogDir
import com.sd.demo.xlog.testContext
import com.sd.demo.xlog.zipFileNames
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogDirectoryScope
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 限制日志文件大小
 */
@RunWith(AndroidJUnit4::class)
class LogFileLimitTest {

  @Test
  fun test() {
    val dir = resetLogDir()
    FLog.setMaxMBPerDay(1)

    flogI<TestLogger> { "info" }
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(true, dir.exists())
    assertEquals(false, dir.listFiles()?.isEmpty())

    val today = dateOfDaysAgo(0)
    val logDir = dir.resolve(today).resolve(testContext.packageName)

    // 还没写满，只有序号0一个文件
    assertEquals(listOf("${today}.0.log"), logDir.logNames())

    /**
     * 限制1MB，写满一半(512KB)就切到下一个序号。
     * 新文件是惰性创建的，切换之后磁盘上还只有序号0，
     * 要等下一条日志真正写入才会出现序号1的文件
     */
    val log = "1".repeat(800 * 1024)
    flogI<TestLogger> { log }
    awaitLogIdle()
    assertEquals(listOf("${today}.0.log"), logDir.logNames())

    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(listOf("${today}.0.log", "${today}.1.log"), logDir.logNames())

    // 序号1也写满，切到序号2，序号0被删除
    flogI<TestLogger> { log }
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(listOf("${today}.1.log", "${today}.2.log"), logDir.logNames())

    // 再来一次，始终只保留两个
    flogI<TestLogger> { log }
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(listOf("${today}.2.log", "${today}.3.log"), logDir.logNames())

    // block里的断言失败会被捕获，所以把结果带出来再断言
    var scope: FLogDirectoryScope? = null
    var zip: File? = null
    FLog.logDirectory {
      scope = this
      zip = logZipOf(today)
    }
    awaitLogIdle()
    // 压缩包里是保留下来的两个文件
    val processDir = "${today}/${testContext.packageName}"
    assertEquals(listOf("${processDir}/${today}.2.log", "${processDir}/${today}.3.log"), zip!!.zipFileNames())
    // 离开logDirectory之后scope已经销毁
    assertEquals(null, scope!!.logZipOf(today))
  }
}

/** 目录下的日志文件名，按序号排序。不能按文件名排序，序号位数不同的时候字典序和数值序不一致 */
private fun File.logNames(): List<String> {
  return listFiles()?.map { it.name }?.sortedBy { it.logSeq() } ?: emptyList()
}

/** 从 <date>.<seq>.log 里解析出序号 */
private fun String.logSeq(): Int? {
  return removeSuffix(".log").substringAfterLast('.').toIntOrNull()
}