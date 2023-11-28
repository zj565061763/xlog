package com.sd.lib.xlog

/**
 * 日志执行器，可以定义执行线程，包括日志的格式化和写入
 */
interface FLogExecutor {
    /**
     * 提交任务，库中可以保证提交方法是按顺序提交的，
     * 开发者执行也应该保证是按顺序执行写入的否则会有先后顺序的问题
     */
    fun submit(task: Runnable)

    /**
     * 关闭
     */
    fun close()
}