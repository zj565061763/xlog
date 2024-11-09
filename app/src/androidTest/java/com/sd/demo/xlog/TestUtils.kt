package com.sd.demo.xlog

import androidx.test.platform.app.InstrumentationRegistry
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.fLogDir
import java.io.File

interface TestLogger : FLogger

val testLogDir get() = InstrumentationRegistry.getInstrumentation().targetContext.fLogDir()

fun File.fCreateFile(): Boolean {
    if (isFile) return true
    if (isDirectory) deleteRecursively()
    parentFile?.mkdirs()
    return createNewFile()
}