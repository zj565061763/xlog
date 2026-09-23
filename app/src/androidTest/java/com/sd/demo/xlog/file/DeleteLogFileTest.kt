package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.dateOfDaysAgo
import com.sd.demo.xlog.fCreateFile
import com.sd.demo.xlog.resetLogDir
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 删除日志文件
 *
 * [FLog.deleteLog]是和当前时间比较的，所以这里的日期只能相对当前时间推算，
 * 跨月/跨年/闰年/夏令时这些日期计算的场景在lib模块的LogFilenameTest里覆盖。
 */
@RunWith(AndroidJUnit4::class)
class DeleteLogFileTest {

  @Test
  fun test() {
    val dir = resetLogDir()
    flogI<TestLogger> { "info" }
    awaitLogIdle()

    val today = dir.resolve(dateOfDaysAgo(0))
    // 前1到5天的日志目录
    val days = (1..5).map { dir.resolve(dateOfDaysAgo(it)).createLogDir() }
    // 设备时间被调快又恢复后留下的明天的日志目录
    val future = dir.resolve(dateOfDaysAgo(-1)).createLogDir()
    // 不是日志的条目
    val other = dir.resolve("other").apply { fCreateFile() }

    assertEquals(true, today.isDirectory)
    assertEquals(listOf(true, true, true, true, true), days.map { it.exists() })

    kotlin.run {
      FLog.deleteLog(5)
      awaitLogIdle()
      assertEquals(true, today.exists())
      assertEquals(listOf(true, true, true, true, false), days.map { it.exists() })
      assertEquals(true, future.exists())
      assertEquals(false, other.exists())
    }

    kotlin.run {
      FLog.deleteLog(3)
      awaitLogIdle()
      assertEquals(true, today.exists())
      assertEquals(listOf(true, true, false, false, false), days.map { it.exists() })
      assertEquals(true, future.exists())
    }

    kotlin.run {
      FLog.deleteLog(1)
      awaitLogIdle()
      assertEquals(true, today.exists())
      assertEquals(listOf(false, false, false, false, false), days.map { it.exists() })
      assertEquals(true, future.exists())
    }

    kotlin.run {
      FLog.deleteLog(0)
      awaitLogIdle()
      // 日志全部删除，包括以后的日期，但是日志目录本身保留，避免下次重建的消耗
      assertEquals(true, dir.exists())
      assertEquals(true, dir.listFiles()?.isEmpty())
    }
  }
}

/** 按真实的目录结构创建日志：<日期>/<进程名>/<日期>.0.log */
private fun File.createLogDir(): File {
  return apply { resolve("process").resolve("${name}.0.log").fCreateFile() }
}
