package com.sd.demo.xlog.log

import com.sd.lib.xlog.FLogDispatcher

/**
 * 单元测试使用的调度器
 */
class TestLogDispatcher : FLogDispatcher {
  override fun dispatch(task: Runnable) {
    task.run()
  }
}