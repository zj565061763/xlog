package com.sd.demo.xlog

import android.app.Application
import android.content.Context
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        /**
         * 初始化，日志保存目录：[Context.getFilesDir]/flog，
         * 默认不限制日志大小，可以调用[FLog.setLimitMBPerDay]方法修改，
         * 默认日志等级：[FLogLevel.All]，可以调用[FLog.setLevel]方法修改，
         * 默认打开控制台日志，可以调用[FLog.setConsoleLogEnabled]方法修改
         */
        FLog.init(this)

        // 设置日志等级 All, Verbose, Debug, Info, Warning, Error, Off  默认日志等级：All
        FLog.setLevel(FLogLevel.All)

        // 限制每天日志文件大小(单位MB)，小于等于0表示不限制大小，默认不限制大小
        FLog.setLimitMBPerDay(100)

        // 修改某个日志标识的配置信息
        FLog.config<AppLogger> {
            this.level = FLogLevel.All
            this.tag = "AppLoggerAppLogger"
        }

        /**
         * 删除日志，参数saveDays表示要保留的日志天数，小于等于0表示删除全部日志，
         * 此处saveDays=1表示保留1天的日志，即保留当天的日志
         */
        FLog.deleteLog(1)
    }
}