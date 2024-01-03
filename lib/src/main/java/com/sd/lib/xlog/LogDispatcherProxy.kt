package com.sd.lib.xlog

import java.util.concurrent.atomic.AtomicInteger

internal fun FLogDispatcher.toProxy(onIdle: () -> Unit): FLogDispatcher {
    return if (this is LogDispatcherProxy) {
        this
    } else {
        LogDispatcherProxy(this, onIdle)
    }
}

private class LogDispatcherProxy(
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