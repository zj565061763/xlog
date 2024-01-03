package com.sd.lib.xlog

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicInteger

internal interface LogDispatcher {
    fun dispatch(block: Runnable)

    companion object {
        fun create(onIdle: () -> Unit): LogDispatcher {
            return LogDispatcherIO(onIdle)
        }
    }
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

private class LogDispatcherIO(onIdle: () -> Unit) : BaseLogDispatcher(onIdle) {
    private val _handler: Handler

    init {
        val thread = HandlerThread("FLog").also { it.start() }
        _handler = Handler(thread.looper)
    }

    override fun dispatchImpl(block: Runnable) {
        _handler.post(block)
    }
}