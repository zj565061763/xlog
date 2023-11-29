package com.sd.demo.xlog

import android.app.Application
import com.sd.demo.xlog.log.AppLogExecutor
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel

private const val DEBUG = true

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 打开日志，默认只打开文件日志
        FLog.open(
            //（必传参数）日志等级 All, Verbose, Debug, Info, Warning, Error
            level = if (DEBUG) FLogLevel.All else FLogLevel.Info,

            //（必传参数）日志文件目录，日志文件名称为当天的日期，例如：20231125.log
            directory = filesDir.resolve("app_log"),

            //（必传参数）限制每天日志文件大小(单位MB)，小于等于0表示不限制大小
            limitMBPerDay = 100,

            //（可选参数）自定义执行线程，包括日志的格式化和写入，默认在调用线程执行
            executor = if (DEBUG) null else AppLogExecutor()
        )

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