package com.sd.demo.xlog

import android.app.Application
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 打开日志，默认只打开文件日志
        FLog.open(
            // 日志等级 Debug, Info, Warning, Error
            level = FLogLevel.Debug,

            // 日志文件目录，日志文件名称为当天的日期，例如：20231125.log
            directory = filesDir.resolve("app_log"),

            // 限制每天日志文件大小(单位MB)，小于等于0表示不限制大小
            limitMBPerDay = 100,
        )

        // 修改某个日志标识的配置信息
        FLog.config<AppLogger> {
            this.level = FLogLevel.Debug
            this.tag = "AppLoggerAppLogger"
        }

        /**
         * 删除日志，参数saveDays表示要保留的日志天数，小于等于0表示删除全部日志，
         * 此处saveDays=1表示保留1天的日志，即保留当天的日志
         */
        FLog.deleteLog(1)
    }
}