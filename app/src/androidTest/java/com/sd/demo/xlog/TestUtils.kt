package com.sd.demo.xlog

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.fLogDir
import java.io.File

interface TestLogger : FLogger

val testContext: Context
  get() = InstrumentationRegistry.getInstrumentation().targetContext

val testLogDir: File
  get() = testContext.fLogDir()

fun File.fCreateFile(): Boolean {
  if (isFile) return true
  if (isDirectory) deleteRecursively()
  parentFile?.mkdirs()
  return createNewFile()
}