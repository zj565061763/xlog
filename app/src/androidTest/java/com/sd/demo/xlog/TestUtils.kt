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
    return this.parentFile.fMakeDirs() && this.createNewFile()
}

fun File?.fMakeDirs(): Boolean {
    if (this == null) return false
    if (this.isDirectory) return true
    if (this.isFile) this.delete()
    return this.mkdirs()
}