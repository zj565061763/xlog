package com.sd.demo.xlog

import androidx.test.platform.app.InstrumentationRegistry
import com.sd.lib.xlog.FLogger
import java.io.File

interface TestLogger : FLogger

private val _context get() = InstrumentationRegistry.getInstrumentation().targetContext
val testLogDir get() = _context.filesDir.resolve("app_log")

fun File?.fCreateFile(): Boolean {
    if (this == null) return false
    if (this.isFile) return true
    if (this.isDirectory) this.deleteRecursively()
    this.parentFile?.mkdirs()
    return this.createNewFile()
}