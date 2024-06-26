package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogV
import com.sd.lib.xlog.flogW
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogLevelTest {

    @Test
    fun test() {
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
    flogV<TestLogger> {
        result += "v"
        Unit
    }
    flogD<TestLogger> {
        result += "d"
        Unit
    }
    flogI<TestLogger> {
        result += "i"
        Unit
    }
    flogW<TestLogger> {
        result += "w"
        Unit
    }
    flogE<TestLogger> {
        result += "e"
        Unit
    }
    return result
}