package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogD
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoggerConfigTest {
  @Test
  fun test() {
    FLog.setLevel(FLogLevel.Info)

    kotlin.run {
      var count = 0
      flogD<TestLogger> { count++.toString() }
      assertEquals(0, count)
    }

    kotlin.run {
      FLog.config<TestLogger> { it.copy(level = FLogLevel.Debug) }
      var count = 0
      flogD<TestLogger> { count++.toString() }
      assertEquals(1, count)
    }

    kotlin.run {
      FLog.config<TestLogger> { it.copy(level = null) }
      var count = 0
      flogD<TestLogger> { count++.toString() }
      assertEquals(0, count)
    }
  }
}