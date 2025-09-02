package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogConsole
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleDebugTest {

  @Test
  fun test() {
    FLog.setConsoleLogEnabled(false)

    FLog.setLevel(FLogLevel.All)
    assertEquals("vdiwe", logResult())

    FLog.setLevel(FLogLevel.Verbose)
    assertEquals("vdiwe", logResult())

    FLog.setLevel(FLogLevel.Debug)
    assertEquals("diwe", logResult())

    FLog.setLevel(FLogLevel.Info)
    assertEquals("iwe", logResult())

    FLog.setLevel(FLogLevel.Warning)
    assertEquals("we", logResult())

    FLog.setLevel(FLogLevel.Error)
    assertEquals("e", logResult())

    FLog.setLevel(FLogLevel.Off)
    assertEquals("", logResult())
  }
}

private fun logResult(): String {
  var result = ""
  flogConsole(FLogLevel.Verbose) {
    result += "v"
    ""
  }
  flogConsole(FLogLevel.Debug) {
    result += "d"
    ""
  }
  flogConsole(FLogLevel.Info) {
    result += "i"
    ""
  }
  flogConsole(FLogLevel.Warning) {
    result += "w"
    ""
  }
  flogConsole(FLogLevel.Error) {
    result += "e"
    ""
  }
  return result
}