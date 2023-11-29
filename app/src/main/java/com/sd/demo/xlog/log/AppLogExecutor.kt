package com.sd.demo.xlog.log

import com.sd.lib.xlog.FLogExecutor
import java.util.concurrent.Executors

private val logExecutor = Executors.newSingleThreadExecutor()

class AppLogExecutor(private val debug: Boolean) : FLogExecutor {

    override fun submit(task: Runnable) {
        if (debug) {
            // debug模式下直接执行
            task.run()
        } else {
            // release模式下异步执行
            logExecutor.submit(task)
        }
    }

    override fun close(closeable: AutoCloseable) {
    }
}