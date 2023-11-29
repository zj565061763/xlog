package com.sd.demo.xlog.log

import com.sd.lib.xlog.FLogExecutor
import java.util.concurrent.Executors

private val logExecutor = Executors.newSingleThreadExecutor()

class AppLogExecutor : FLogExecutor {
    override fun submit(task: Runnable) {
        logExecutor.submit(task)
    }
}