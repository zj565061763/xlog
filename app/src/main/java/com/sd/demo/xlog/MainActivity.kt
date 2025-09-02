package com.sd.demo.xlog

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sd.demo.xlog.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
  private val _binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(_binding.root)
    _binding.btnSampleLog.setOnClickListener {
      startActivity(Intent(this, SampleLog::class.java))
    }
    _binding.btnSampleLoggerApi.setOnClickListener {
      startActivity(Intent(this, SampleLoggerApi::class.java))
    }
    _binding.btnSamplePerformance.setOnClickListener {
      startActivity(Intent(this, SamplePerformance::class.java))
    }
  }
}