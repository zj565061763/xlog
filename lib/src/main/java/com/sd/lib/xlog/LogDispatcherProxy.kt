package com.sd.lib.xlog

import java.util.concurrent.atomic.AtomicInteger

/**
 * [onIdle]回调在[FLogDispatcher]上面执行
 */
internal fun FLogDispatcher.toProxy(onIdle: () -> Unit): LogDispatcherProxy {
    return if (this is LogDispatcherProxy) {
        this
    } else {
        LogDispatcherProxy(this, onIdle)
    }
}

internal class LogDispatcherProxy(
    private val dispatcher: FLogDispatcher,
    private val onIdle: () -> Unit,
) : FLogDispatcher {
    private val _counter = AtomicInteger(0)

    override fun dispatch(block: Runnable) {
        _counter.incrementAndGet()
        dispatcher.dispatch {
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
}