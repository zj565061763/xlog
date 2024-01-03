package com.sd.lib.xlog

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicInteger

internal fun defaultLogDispatcher(onIdle: () -> Unit): LogDispatcher {
    return LogDispatcherHandlerThread(onIdle)
}

internal interface LogDispatcher {
    fun dispatch(block: Runnable)
}

private abstract class BaseLogDispatcher(
    private val onIdle: () -> Unit,
) : LogDispatcher {
    private val _counter = AtomicInteger(0)

    final override fun dispatch(block: Runnable) {
        _counter.incrementAndGet()
        dispatchImpl { executeBlock(block) }
    }

    private fun executeBlock(block: Runnable) {
        try {
            block.run()
        } finally {
            val count = _counter.decrementAndGet().also {
                check(it >= 0) { "block executed more than once." }
            }
            if (count == 0) {
                onIdle()
            }
        }
    }

    protected abstract fun dispatchImpl(block: Runnable)
}

private class LogDispatcherHandlerThread(
    onIdle: () -> Unit,
) : BaseLogDispatcher(onIdle) {

    private val _handler = kotlin.run {
        val thread = HandlerThread("FLog").also { it.start() }
        val looper = checkNotNull(thread.looper)
        Handler(looper)
    }

    override fun dispatchImpl(block: Runnable) {
        _handler.post(block)
    }
}