package com.sd.lib.xlog

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

/** 默认的日志目录，在外部存储的应用专属目录下，外部存储不可用时返回null */
fun Context.fLogDir(
  /** 日志目录名称 */
  dirName: String = "sd.lib.xlog",
): File? {
  require(dirName.isNotEmpty()) { "dirName is empty" }
  return getExternalFilesDir(null)?.resolve(dirName)
}

/** 当前进程名，系统接口取不到时读取/proc/self/cmdline */
internal fun Context.currentProcess(): String? {
  val process = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Application.getProcessName()
  } else {
    val pid = Process.myPid()
    (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
      ?.runningAppProcesses
      ?.firstOrNull { it.pid == pid }
      ?.processName
  }
  return process?.ifEmpty { null }
    ?: libRunCatching { processOfCmdline(File("/proc/self/cmdline").readText()) }.getOrNull()
}

/** 从cmdline的内容中解析出进程名，各参数以'\u0000'分隔，第一个就是进程名 */
internal fun processOfCmdline(cmdline: String): String? {
  return cmdline.substringBefore('\u0000').trim().ifEmpty { null }
}