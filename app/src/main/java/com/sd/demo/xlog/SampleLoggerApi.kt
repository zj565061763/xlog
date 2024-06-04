package com.sd.demo.xlog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.SampleLogBinding
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.ld
import com.sd.lib.xlog.le
import com.sd.lib.xlog.li
import com.sd.lib.xlog.lv
import com.sd.lib.xlog.lw

class SampleLoggerApi : AppCompatActivity(), FLogger {
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

        lv { "Verbose" }
        ld { "Debug" }
        li { "Info" }
        lw { "Warning" }
        le { "Error" }
    }
}