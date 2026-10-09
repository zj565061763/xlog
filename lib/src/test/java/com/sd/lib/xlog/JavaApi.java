package com.sd.lib.xlog;

/** 从Java调用日志API，这些签名由@JvmStatic和@JvmOverloads生成，只有Java的调用点能发现它们被去掉 */
class JavaApi {
  /** 用不带模式的重载依次打印V/D/I/W/E日志，消息分别是v、d、i、w、e */
  static void log(Class<? extends FLogger> logger) {
    FLog.logV(logger, "v");
    FLog.logD(logger, "d");
    FLog.logI(logger, "i");
    FLog.logW(logger, "w");
    FLog.logE(logger, "e");
  }

  /** 用带模式的重载依次打印V/D/I/W/E日志，消息分别是v、d、i、w、e */
  static void log(Class<? extends FLogger> logger, FLogMode mode) {
    FLog.logV(logger, mode, "v");
    FLog.logD(logger, mode, "d");
    FLog.logI(logger, mode, "i");
    FLog.logW(logger, mode, "w");
    FLog.logE(logger, mode, "e");
  }
}
