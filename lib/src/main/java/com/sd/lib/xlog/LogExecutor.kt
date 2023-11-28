package com.sd.lib.xlog

/**
 * 日志写入执行器
 */
interface FLogExecutor {
    /**
     * 提交任务
     */
    fun submit(runnable: Runnable)

    /**
     * 关闭
     */
    fun close()
}