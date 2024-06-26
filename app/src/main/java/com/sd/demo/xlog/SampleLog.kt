package com.sd.demo.xlog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.SampleLogBinding
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogV
import com.sd.lib.xlog.flogW
import kotlin.concurrent.thread

class SampleLog : AppCompatActivity() {
    private val _binding by lazy { SampleLogBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(_binding.root)
        _binding.btnLog.setOnClickListener {
            log()
        }
    }

    private fun log() {
        // 打开控制台日志
        FLog.setConsoleLogEnabled(true)

        flogV<AppLogger> { "Verbose" }
        flogD<AppLogger> { "Debug" }
        flogI<AppLogger> { "Info" }
        flogW<AppLogger> { "Warning" }
        flogE<AppLogger> { "Error" }
        thread {
            flogW<AppLogger> { "in thread" }
        }
    }
}