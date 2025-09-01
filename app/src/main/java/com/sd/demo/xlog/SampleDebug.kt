package com.sd.demo.xlog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.SampleDebugBinding
import com.sd.lib.xlog.FLog
import com.sd.lib.xlog.FLogLevel
import com.sd.lib.xlog.fDebug

class SampleDebug : AppCompatActivity() {
  private val _binding by lazy { SampleDebugBinding.inflate(layoutInflater) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(_binding.root)
    _binding.btnLog.setOnClickListener {
      log()
    }
  }

  /**
   * 打印控制台日志，不会写入到文件中，默认tag为：DebugLogger，
   * 注意：此方法不受[FLog.setConsoleLogEnabled]开关限制，只受日志等级限制
   */
  private fun log() {
    fDebug(FLogLevel.Verbose) { "Verbose" }
    fDebug(FLogLevel.Debug) { "Debug" }
    fDebug(FLogLevel.Info) { "Info" }
    fDebug(FLogLevel.Warning) { "Warning" }
    fDebug(FLogLevel.Error) { "Error" }
  }
}