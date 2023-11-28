package com.sd.demo.xlog

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.ActivityMainBinding
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.fDebug
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogW
import kotlin.time.measureTime

class MainActivity : AppCompatActivity() {
    private val _binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_binding.root)
        _binding.btnLog.setOnClickListener {
            log()
        }

        _binding.btnPerformance.setOnClickListener {
            testPerformance()
        }
    }
}

private fun log() {
    // 打开控制台日志
    FLog.enableConsoleLog(true)

    flogD<AppLogger> { "debug" }
    flogI<AppLogger> { "info" }
    flogW<AppLogger> { "warning" }
    flogE<AppLogger> { "error" }

    // 打印控制台日志，不会写入到文件中，tag：DebugLogger
    fDebug { "console debug log" }
}

/**
 * 日志性能测试，需要关闭控制台日志后测试
 */
private fun testPerformance(logLength: Int = 500, repeat: Int = 1_0000) {
    // 关闭控制台日志
    FLog.enableConsoleLog(false)
    val log = "1".repeat(logLength)
    measureTime {
        repeat(repeat) {
            flogI<AppLogger> { log }
        }
    }.let {
        Log.i(MainActivity::class.java.simpleName, "time:${it.inWholeMilliseconds}")
    }
}