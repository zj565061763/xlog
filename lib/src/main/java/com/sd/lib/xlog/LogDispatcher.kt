package com.sd.lib.xlog

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 日志调度器，实现类可以在任何线程上执行任务，但必须保证按顺序执行
 */
interface FLogDispatcher {
    fun dispatch(block: Runnable)
}

internal fun defaultLogDispatcher(onIdle: () -> Unit): FLogDispatcher {
    return LogDispatcherProxy(
        dispatcher = LogDispatcherDefault(),
        onIdle = onIdle,
    )
}

private class LogDispatcherProxy(
    private val dispatcher: FLogDispatcher,
    private val onIdle: () -> Unit,
) : FLogDispatcher {
    private val _counter = AtomicInteger(0)

    override fun dispatch(block: Runnable) {
        _counter.incrementAndGet()
        dispatcher.dispatch { execute(block) }
    }

    private fun execute(block: Runnable) {
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
}

private val SingleThreadExecutor by lazy { Executors.newSingleThreadExecutor() }

private class LogDispatcherDefault : FLogDispatcher {
    override fun dispatch(block: Runnable) {
        SingleThreadExecutor.submit(block)
    }
}