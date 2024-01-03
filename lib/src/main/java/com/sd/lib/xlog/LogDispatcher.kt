package com.sd.lib.xlog

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger

internal interface LogDispatcher {
    fun dispatch(block: Runnable)

    companion object {
        fun create(async: Boolean, onIdle: () -> Unit): LogDispatcher {
            return if (async) {
                LogDispatcherIO(onIdle)
            } else {
                LogDispatcherMain(onIdle)
            }
        }
    }
}

private abstract class BaseLogDispatcher(
    looper: Looper,
    private val onIdle: () -> Unit,
) : LogDispatcher {
    private val _handler = Handler(looper)
    private val _counter = AtomicInteger(0)

    final override fun dispatch(block: Runnable) {
        _counter.incrementAndGet()
        _handler.post { executeBlock(block) }
    }

    private fun executeBlock(block: Runnable) {
        try {
            block.run()
        } finally {
            val count = _counter.decrementAndGet().also {
                check(it >= 0) { "Runnable executed more than once." }
            }
            if (count == 0) {
                onIdle()
            }
        }
    }
}

private class LogDispatcherMain(onIdle: () -> Unit) : BaseLogDispatcher(
    looper = Looper.getMainLooper(),
    onIdle = onIdle,
)

private class LogDispatcherIO(onIdle: () -> Unit) : BaseLogDispatcher(
    looper = HandlerThread("FLog").let {
        it.start()
        it.looper
    },
    onIdle = onIdle,
)