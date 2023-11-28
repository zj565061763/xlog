package com.sd.demo.xlog.log

import com.sd.lib.xlog.FLogExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AppLogExecutor : FLogExecutor {
    private var _executor: ExecutorService? = Executors.newSingleThreadExecutor()

    override fun submit(task: Runnable) {
        val executor = _executor ?: Executors.newSingleThreadExecutor().also {
            _executor = it
        }
        executor.submit(task)
    }

    override fun close() {
        _executor?.shutdown()
        _executor = null
    }
}