package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.fDebug
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleDebugTest {
    @Test
    fun test() {
        kotlin.run {
            FLog.setLevel(FLogLevel.All)
            var count = 0
            fDebug { count++ }
            Assert.assertEquals(1, count)
        }

        kotlin.run {
            FLog.setLevel(FLogLevel.Warning)
            var count = 0
            fDebug { count++ }
            Assert.assertEquals(0, count)
        }
    }
}