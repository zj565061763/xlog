package com.sd.demo.xlog

import android.app.Application
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.FLogMode

class App : Application() {
  override fun onCreate() {
    super.onCreate()

    // 初始化
    FLog.init(context = this) {
      // 单元测试使用的调度器
//      setLogDispatcher(TestLogDispatcher())

      configLogger(AppLogger::class.java) {
        it.copy(
          level = FLogLevel.All,
          tag = "AppLoggerAppLogger",
        )
      }
    }

    // 设置日志等级 All, Verbose, Debug, Info, Warning, Error, Off  默认日志等级：All
    FLog.setLevel(FLogLevel.All)

    // 设置日志模式 Default，Console，Store 默认日志模式：Default，发布到控制台和仓库
    FLog.setMode(FLogMode.Default)

    // 限制每天日志文件大小(单位MB)，小于等于0表示不限制，默认不限制
    FLog.setMaxMBPerDay(100)

    /**
     * 删除日志，参数saveDays表示要保留的日志天数，小于等于0表示删除全部日志，
     * 此处saveDays=1表示保留1天的日志，即保留当天的日志
     */
    FLog.deleteLog(saveDays = 1)
  }
}