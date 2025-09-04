package com.sd.demo.xlog

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.ActivityMainBinding
import com.sd.lib.xlog.FLog

class MainActivity : AppCompatActivity() {
  private val _binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(_binding.root)
    _binding.btnSampleLog.setOnClickListener {
      startActivity(Intent(this, SampleLog::class.java))
    }
    _binding.btnSamplePerformance.setOnClickListener {
      startActivity(Intent(this, SamplePerformance::class.java))
    }
  }

  override fun onResume() {
    super.onResume()
    // 删除所有日志
    FLog.deleteLog(saveDays = 0)
  }
}