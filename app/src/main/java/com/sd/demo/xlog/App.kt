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
         * 打开日志，文件保存目录：[Context.getFilesDir]/flog，
         * 默认只打开文件日志，可以调用[FLog.enableConsoleLog]方法开关控制台日志，
         */
        FLog.open(
            context = this,

            //（必传参数）日志等级 All, Verbose, Debug, Info, Warning, Error
            level = FLogLevel.All,

            //（可选参数）限制每天日志文件大小(单位MB)，小于等于0表示不限制大小，默认100MB
            limitMBPerDay = 100,
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