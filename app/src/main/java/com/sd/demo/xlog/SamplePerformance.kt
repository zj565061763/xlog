package com.sd.demo.xlog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.SamplePerformanceBinding
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.fDebug
import com.sd.lib.xlog.flogI
import kotlin.time.measureTime

class SamplePerformance : AppCompatActivity() {
    private val _binding by lazy { SamplePerformanceBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_binding.root)
        _binding.btnLog.setOnClickListener {
            log()
        }
    }

    /**
     * 日志性能测试，需要关闭控制台日志后测试
     */
    private fun log(repeat: Int = 1_0000) {
        // 关闭控制台日志
        FLog.setConsoleLogEnabled(false)

        val log = "1".repeat(500)
        measureTime {
            repeat(repeat) {
                flogI<AppLogger> { log }
            }
        }.let {
            fDebug { "time:${it.inWholeMilliseconds}" }
        }
    }
}