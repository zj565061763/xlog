package com.sd.lib.xlog

import java.util.concurrent.Executors

/**
 * 日志调度器，实现类可以在任何线程上执行任务，但必须保证按顺序执行
 */
interface FLogDispatcher {
    fun dispatch(block: Runnable)
}

private val SingleThreadExecutor by lazy { Executors.newSingleThreadExecutor() }

internal class LogDispatcherDefault : FLogDispatcher {
    override fun dispatch(block: Runnable) {
        SingleThreadExecutor.submit(block)
    }
}