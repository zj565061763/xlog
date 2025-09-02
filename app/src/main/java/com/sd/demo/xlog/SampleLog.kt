package com.sd.demo.xlog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.SampleLogBinding
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogConsole
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogV
import com.sd.lib.xlog.flogW
import com.sd.lib.xlog.ld
import com.sd.lib.xlog.le
import com.sd.lib.xlog.li
import com.sd.lib.xlog.logConsole
import com.sd.lib.xlog.lv
import com.sd.lib.xlog.lw
import kotlin.concurrent.thread

class SampleLog : AppCompatActivity(), FLogger {
  private val _binding by lazy { SampleLogBinding.inflate(layoutInflater) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(_binding.root)
    _binding.btnLog.setOnClickListener {
      log()
    }
    _binding.btnLoggerApi.setOnClickListener {
      loggerApi()
    }

    // 打开控制台日志
    FLog.setConsoleLogEnabled(true)
  }

  private fun log() {
    flogConsole { "log only in console" }
    flogV<AppLogger> { "Verbose" }
    flogD<AppLogger> { "Debug" }
    flogI<AppLogger> { "Info" }
    flogW<AppLogger> { "Warning" }
    flogE<AppLogger> { "Error" }
    thread {
      flogW<AppLogger> { "in thread" }
    }
  }

  private fun loggerApi() {
    logConsole { "log only in console" }
    lv { "Verbose" }
    ld { "Debug" }
    li { "Info" }
    lw { "Warning" }
    le { "Error" }
  }
}