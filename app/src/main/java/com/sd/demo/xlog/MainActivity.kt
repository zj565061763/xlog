package com.sd.demo.xlog

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.ActivityMainBinding
import com.sd.lib.xlog.FLog
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
  private val _binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(_binding.root)
    _binding.btnSampleLog.setOnClickListener {
      startActivity(Intent(this, SampleLog::class.java))
    }
    _binding.btnSampleLogProcess.setOnClickListener {
      startActivity(Intent(this, SampleLogProcess::class.java))
    }
    _binding.btnSamplePerformance.setOnClickListener {
      startActivity(Intent(this, SamplePerformance::class.java))
    }
  }

  override fun onResume() {
    super.onResume()
    FLog.logDirectory {
      // 固定用Locale.US：默认locale下阿拉伯语、波斯语等会输出非ASCII数字，logZipOf识别不了
      val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(System.currentTimeMillis())
      logZipOf(date)
    }
  }
}