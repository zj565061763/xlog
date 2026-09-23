package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogV
import com.sd.lib.xlog.flogW
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogTest {

  @Test
  fun testReInit() {
    val init = FLog.init(testContext)
    assertEquals(false, init)
  }

  @Test
  fun testLevel() {
    FLog.setLevel(FLogLevel.All)
    assertEquals("vdiwe", logResult<TestLogger>())

    FLog.setLevel(FLogLevel.Verbose)
    assertEquals("vdiwe", logResult<TestLogger>())

    FLog.setLevel(FLogLevel.Debug)
    assertEquals("diwe", logResult<TestLogger>())

    FLog.setLevel(FLogLevel.Info)
    assertEquals("iwe", logResult<TestLogger>())

    FLog.setLevel(FLogLevel.Warning)
    assertEquals("we", logResult<TestLogger>())

    FLog.setLevel(FLogLevel.Error)
    assertEquals("e", logResult<TestLogger>())

    FLog.setLevel(FLogLevel.Off)
    assertEquals("", logResult<TestLogger>())
  }

  /** 配置的等级覆盖全局等级，但全局等级为Off时一律不打印 */
  @Test
  fun testConfigLevel() {
    // App里给AppLogger配置了等级All
    FLog.setLevel(FLogLevel.Error)
    assertEquals("vdiwe", logResult<AppLogger>())
    assertEquals("e", logResult<TestLogger>())

    FLog.setLevel(FLogLevel.Off)
    assertEquals("", logResult<AppLogger>())
  }
}

/** 依次打印V/D/I/W/E日志，返回执行了消息block的等级，block返回空串，不会真正写日志 */
private inline fun <reified T : FLogger> logResult(): String {
  var result = ""
  flogV<T> {
    result += "v"
    ""
  }
  flogD<T> {
    result += "d"
    ""
  }
  flogI<T> {
    result += "i"
    ""
  }
  flogW<T> {
    result += "w"
    ""
  }
  flogE<T> {
    result += "e"
    ""
  }
  return result
}