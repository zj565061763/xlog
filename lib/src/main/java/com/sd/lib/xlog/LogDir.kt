package com.sd.lib.xlog

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

fun Context.fLogDir(): File {
  @SuppressLint("SdCardPath")
  val dir = (filesDir ?: File("/data/data/${packageName}/files"))
    .resolve("sd.lib.xlog")

  val process = currentProcess()
  return if (!process.isNullOrBlank() && process != packageName) {
    dir.resolve(process)
  } else {
    dir
  }
}

private fun Context.currentProcess(): String? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Application.getProcessName()
  } else {
    runCatching {
      val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val processes = manager.runningAppProcesses
      if (processes.isNullOrEmpty()) {
        null
      } else {
        val pid = Process.myPid()
        processes.find { it.pid == pid }?.processName
      }
    }.getOrNull()
  }
}