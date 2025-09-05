package com.sd.lib.xlog

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

/**
 * 日志目录
 */
fun Context.fLogDir(
  /** 是否优先使用外部存储 */
  preferExternal: Boolean = true,
  /** 日志目录名称 */
  dirName: String = "sd.lib.xlog",
): File {
  require(dirName.isNotEmpty()) { "dirName is empty" }
  val rootDir = if (preferExternal) (getExternalFilesDir(null) ?: filesDir) else filesDir
  return rootDir.resolve(dirName)
}

/**
 * 当前进程
 */
internal fun Context.currentProcess(): String? {
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