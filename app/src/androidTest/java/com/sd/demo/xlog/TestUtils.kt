package com.sd.demo.xlog

import androidx.test.platform.app.InstrumentationRegistry
import com.sd.lib.xlog.FLogger

interface TestLogger : FLogger

private val _context get() = InstrumentationRegistry.getInstrumentation().targetContext
val testLogDir get() = _context.filesDir.resolve("app_log")