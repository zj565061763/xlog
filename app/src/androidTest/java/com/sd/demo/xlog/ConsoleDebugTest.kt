package com.sd.demo.xlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.fDebug
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
    fDebug(FLogLevel.Verbose) {
        result += "v"
        Unit
    }
    fDebug(FLogLevel.Debug) {
        result += "d"
        Unit
    }
    fDebug(FLogLevel.Info) {
        result += "i"
        Unit
    }
    fDebug(FLogLevel.Warning) {
        result += "w"
        Unit
    }
    fDebug(FLogLevel.Error) {
        result += "e"
        Unit
    }
    return result
}