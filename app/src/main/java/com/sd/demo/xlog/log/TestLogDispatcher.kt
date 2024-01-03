package com.sd.demo.xlog.log

import com.sd.lib.xlog.FLogDispatcher

/**
 * 单元测试用的调度器
 */
class TestLogDispatcher : FLogDispatcher {
    override fun dispatch(block: Runnable) {
        block.run()
    }
}