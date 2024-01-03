package com.sd.lib.xlog

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal fun defaultLogDispatcher(onIdle: () -> Unit): LogDispatcher {
    return LogDispatcherDefault(onIdle)
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

private val SingleThreadExecutor by lazy { Executors.newSingleThreadExecutor() }

private class LogDispatcherDefault(onIdle: () -> Unit) : BaseLogDispatcher(onIdle) {
    override fun dispatchImpl(block: Runnable) {
        SingleThreadExecutor.submit(block)
    }
}