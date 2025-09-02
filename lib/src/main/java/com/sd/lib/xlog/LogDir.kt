package com.sd.lib.xlog

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

fun Context.fLogDir(): File {
  val dir = applicationContext.filesDir.resolve("sd.lib.xlog")
  val process = currentProcess()
  return if (!process.isNullOrBlank() && process != packageName) {
    dir.resolve(process.replace(":", "_"))
  } else {
    dir
  }
}

private fun Context.currentProcess(): String? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Application.getProcessName()
  } else {
    val pid = Process.myPid()
    (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
      ?.runningAppProcesses
      ?.firstOrNull { it.pid == pid }
      ?.processName
  }
}