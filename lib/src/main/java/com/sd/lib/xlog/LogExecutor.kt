package com.sd.lib.xlog

/**
 * 日志执行器，可以定义日志写入的线程
 */
interface FLogExecutor {
    /**
     * 提交任务
     */
    fun submit(task: Runnable)

    /**
     * 关闭
     */
    fun close()
}