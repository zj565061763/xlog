package com.sd.lib.xlog

import java.util.concurrent.Executors

/**
 * 日志调度器，实现类可以在任何线程上执行任务，但必须保证按提交顺序执行，一个执行完成再执行下一个
 */
interface FLogDispatcher {
    fun dispatch(block: Runnable)
}

private val SingleThreadExecutor by lazy { Executors.newSingleThreadExecutor() }

/**
 * 默认日志调度器
 */
internal class LogDispatcherDefault : FLogDispatcher {
    override fun dispatch(block: Runnable) {
        SingleThreadExecutor.submit(block)
    }
}