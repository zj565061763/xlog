package com.sd.demo.xlog

import android.app.Application
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化
        FLog.init(
            //（必传参数）日志文件目录
            directory = filesDir.resolve("app_log"),

            //（可选参数）是否异步发布日志，默认值false
            async = false,
        )

        // 设置日志等级 All, Verbose, Debug, Info, Warning, Error, Off  默认日志等级：All
        FLog.setLevel(FLogLevel.All)

        // 限制每天日志文件大小(单位MB)，小于等于0表示不限制，默认不限制
        FLog.setLimitMBPerDay(100)

        // 设置是否打打印控制台日志，默认打开
        FLog.setConsoleLogEnabled(true)

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