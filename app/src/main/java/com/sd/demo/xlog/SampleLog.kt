package com.sd.demo.xlog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.SampleLogBinding
import com.sd.demo.xlog.log.AppLogger
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogMode
import com.sd.lib.xlog.FLogger
import com.sd.lib.xlog.flogD
import com.sd.lib.xlog.flogE
import com.sd.lib.xlog.flogI
import com.sd.lib.xlog.flogV
import com.sd.lib.xlog.flogW
import com.sd.lib.xlog.ld
import com.sd.lib.xlog.le
import com.sd.lib.xlog.li
import com.sd.lib.xlog.lv
import com.sd.lib.xlog.lw
import kotlin.concurrent.thread

open class SampleLog : AppCompatActivity(), FLogger {
  private val _binding by lazy { SampleLogBinding.inflate(layoutInflater) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(_binding.root)
    _binding.btnLog.setOnClickListener {
      log()
    }
    _binding.btnLogConsole.setOnClickListener {
      logConsole()
    }
    _binding.btnLoggerApi.setOnClickListener {
      loggerApi()
    }

    FLog.setMode(FLogMode.Default)

    // 测试用：带EXTRA_LOG启动时直接打印日志并退出，不用点击按钮
    if (intent.getBooleanExtra(EXTRA_LOG, false)) {
      log()
      finish()
    }
  }

  private fun log() {
    flogV<AppLogger> { "Verbose" }
    flogD<AppLogger> { "Debug" }
    flogI<AppLogger> { "Info" }
    flogW<AppLogger> { "Warning" }
    flogE<AppLogger> { "Error" }
    thread { flogE<AppLogger> { "in thread" } }
  }

  private fun logConsole() {
    flogV<AppLogger>(mode = FLogMode.Console) { "Verbose" }
    flogD<AppLogger>(mode = FLogMode.Console) { "Debug" }
    flogI<AppLogger>(mode = FLogMode.Console) { "Info" }
    flogW<AppLogger>(mode = FLogMode.Console) { "Warning" }
    flogE<AppLogger>(mode = FLogMode.Console) { "Error" }
  }

  private fun loggerApi() {
    lv { "Verbose" }
    ld { "Debug" }
    li { "Info" }
    lw { "Warning" }
    thread { le { "in thread" } }
  }

  companion object {
    /** 启动时带上true，页面创建后直接打印一组日志并退出，供多进程测试使用 */
    const val EXTRA_LOG = "log"
  }
}