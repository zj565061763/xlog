package com.sd.demo.xlog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.SampleLoggerApiBinding
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.debug
import com.sd.lib.xlog.ld
import com.sd.lib.xlog.le
import com.sd.lib.xlog.li
import com.sd.lib.xlog.lv
import com.sd.lib.xlog.lw

class SampleLoggerApi : AppCompatActivity(), FLogger {
  private val _binding by lazy { SampleLoggerApiBinding.inflate(layoutInflater) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(_binding.root)
    _binding.btnLog.setOnClickListener {
      printLog()
    }
    _binding.btnDebug.setOnClickListener {
      printDebug()
    }

    // 打开控制台日志
    FLog.setConsoleLogEnabled(true)
  }

  private fun printLog() {
    lv { "Verbose" }
    ld { "Debug" }
    li { "Info" }
    lw { "Warning" }
    le { "Error" }
  }

  private fun printDebug() {
    debug(FLogLevel.Verbose) { "Verbose" }
    debug(FLogLevel.Debug) { "Debug" }
    debug(FLogLevel.Info) { "Info" }
    debug(FLogLevel.Warning) { "Warning" }
    debug(FLogLevel.Error) { "Error" }
  }
}