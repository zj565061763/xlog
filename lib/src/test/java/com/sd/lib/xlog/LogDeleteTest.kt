package com.sd.lib.xlog

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * [FLog.deleteLog]按保留天数删除日志，不是日志的条目一起删除，以后的日期和.开头的条目保留。
 * 日期只能相对当前时间推算，跨月、跨年、闰年和夏令时在[LogFilenameTest]里覆盖。
 *
 * 这个类会初始化[FLog]，初始化之后无法重置，所以单独一个测试类。
 */
class LogDeleteTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun test() {
    val dir = folder.newFolder()
    val dispatcher = AwaitDispatcher()
    assertTrue(FLog.init(ContextWrapper(null)) {
      setLogDirectory { dir }
      setLogDispatcher(dispatcher)
    })

    // 今天的目录由真实的日志产生，确认这里推算的日期和日志库一致
    flogI<DeleteLogger>(FLogMode.Store) { "today" }
    assertTrue(dispatcher.await())
    val today = dir.resolve(dateOfDaysAgo(0))
    assertTrue(today.isDirectory)

    // 前1到3天的日志目录
    val days = (1..3).map { dir.resolve(dateOfDaysAgo(it)).createLogDir() }
    // 设备时间被调快又恢复后留下的明天的日志目录
    val future = dir.resolve(dateOfDaysAgo(-1)).createLogDir()
    // 不是日志的条目
    val other = dir.resolve("other").apply { writeText("other") }
    // .开头的是库的内部目录
    val zipDir = dir.resolve(".zip").apply { mkdirs() }

    FLog.deleteLog(3)
    assertTrue(dispatcher.await())
    assertEquals(listOf(true, true, false), days.map { it.exists() })
    assertEquals(listOf(true, true, false, true), listOf(today, future, other, zipDir).map { it.exists() })

    FLog.deleteLog(1)
    assertTrue(dispatcher.await())
    assertEquals(listOf(false, false, false), days.map { it.exists() })
    assertEquals(listOf(true, true, true), listOf(today, future, zipDir).map { it.exists() })

    // 小于等于0删除全部日志，包括以后的日期，日志目录本身和.开头的条目保留
    FLog.deleteLog(0)
    assertTrue(dispatcher.await())
    assertEquals(listOf(zipDir.name), dir.list()?.toList())
  }

  /** 日志目录存在但读不了时抛异常，由调用方输出到Logcat，不能当作空目录；目录不存在时不抛 */
  @Test
  fun testListError() {
    val dir = folder.newFolder()
    val expired = dir.resolve(dateOfDaysAgo(1)).createLogDir()
    val filename = defaultLogFilename()
    val today = dateOfDaysAgo(0)

    // 以root运行时权限不生效，跳过
    assumeTrue(dir.setReadable(false) && dir.listFiles() == null)
    try {
      assertThrows(IOException::class.java) { deleteLogIn(dir = dir, filename = filename, today = today, saveDays = 0) }
    } finally {
      dir.setReadable(true)
    }
    assertTrue(expired.exists())

    // 目录不存在时不抛
    deleteLogIn(dir = dir.resolve("missing"), filename = filename, today = today, saveDays = 0)
  }

  /** 某个过期目录删不掉时不抛异常，其他过期目录照常删除，恢复之后再删能删掉 */
  @Test
  fun testDeleteFailed() {
    val filename = defaultLogFilename()
    val today = dateOfDaysAgo(0)

    // 目录的列出顺序不确定，轮流让其中一个删不掉，另一个都要照常删除
    for (lockedIndex in 0..1) {
      val dir = folder.newFolder()
      val days = (1..2).map { dir.resolve(dateOfDaysAgo(it)).createLogDir() }
      val locked = days[lockedIndex]
      val other = days[1 - lockedIndex]

      // 目录不可写时删不掉里面的日志文件；以root运行时权限不生效，跳过
      assumeTrue(locked.setWritable(false) && !locked.canWrite())
      try {
        deleteLogIn(dir = dir, filename = filename, today = today, saveDays = 0)
        assertTrue(locked.exists())
        assertFalse(other.exists())
      } finally {
        locked.setWritable(true)
      }

      deleteLogIn(dir = dir, filename = filename, today = today, saveDays = 0)
      assertFalse(locked.exists())
    }
  }
}

private interface DeleteLogger : FLogger

/** 按真实的目录结构创建日志：<日期>/<日期>.0.log，JVM里取不到进程名，没有进程目录 */
private fun File.createLogDir(): File {
  return apply {
    mkdirs()
    resolve("${name}.0.log").writeText("log\n")
  }
}
