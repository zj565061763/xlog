package com.sd.lib.xlog

/**
 * 日志执行器，可以定义执行线程，包括日志的格式化和写入
 */
interface FLogExecutor {
    /**
     * 提交任务，库中可以保证按顺序提交任务，
     * 开发者应该保证按顺序执行任务，否则会有先后顺序的问题
     */
    fun submit(task: Runnable)

    /**
     * 日志关闭回调，
     * 如果异步任务还未完成的，需要在任务完成后调用[AutoCloseable.close]关闭[closeable]释放资源
     */
    fun close(closeable: AutoCloseable)
}