package com.sd.demo.xlog.file

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.TestLogger
import com.sd.demo.xlog.awaitLogIdle
import com.sd.demo.xlog.dateOfDaysAgo
import com.sd.demo.xlog.resetLogDir
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogDirectoryScope
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FLog.logDirectory]的block是外部传入的，抛异常不能影响日志库
 */
@RunWith(AndroidJUnit4::class)
class LogDirectoryErrorTest {

  @Test
  fun test() {
    FLog.setLevel(FLogLevel.All)

    val dir = resetLogDir()
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(true, dir.exists())

    val today = dateOfDaysAgo(0)

    // block正常执行的时候可以拿到压缩包
    kotlin.run {
      var zip: Any? = null
      FLog.logDirectory { zip = logZipOf(today) }
      awaitLogIdle()
      assertNotEquals(null, zip)
    }

    // block抛异常
    var scope: FLogDirectoryScope? = null
    FLog.logDirectory {
      scope = this
      throw RuntimeException("block error")
    }
    awaitLogIdle()

    // 抛异常之后scope依然被销毁，此时今天的日志目录是存在的，
    // 返回null只可能是因为scope已经销毁
    assertEquals(true, dir.resolve(today).exists())
    assertEquals(null, scope!!.logZipOf(today))

    // 抛异常之后调度器还能继续工作，这里如果调度线程死了会等待超时
    resetLogDir()
    flogI<TestLogger> { "info" }
    flogI<TestLogger> { "info" }
    awaitLogIdle()
    assertEquals(true, dir.exists())
    assertEquals(false, dir.listFiles()?.isEmpty())
  }
}
