package com.sd.demo.xlog.log

import com.sd.lib.xlog.FLogExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AppLogExecutor(
    private val debug: Boolean
) : FLogExecutor {
    private var _executor: ExecutorService? = null

    override fun submit(task: Runnable) {
        if (debug) {
            task.run()
        } else {
            val executor = _executor ?: Executors.newSingleThreadExecutor().also {
                _executor = it
            }
            executor.submit(task)
        }
    }

    override fun close() {
        _executor?.shutdown()
        _executor = null
    }
}