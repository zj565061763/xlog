package com.sd.lib.xlog

import java.io.File

/** 初始化配置，在[FLog.init]的block里调用 */
interface FLogInitScope {
  /**
   * 设置日志目录，默认为[fLogDir]。
   * 目录只能存放日志，[FLog.deleteLog]会删除其中不是日志的文件。
   */
  fun setLogDirectory(directory: File)

  /** 设置日志格式化器 */
  fun setLogFormatter(formatter: FLogFormatter)

  /** 设置日志仓库工厂，默认直接写文件 */
  fun setLogStoreFactory(factory: FLogStore.Factory)

  /** 设置日志调度器，默认使用单线程执行器 */
  fun setLogDispatcher(dispatcher: FLogDispatcher)

  /** 配置[logger]，[block]的参数是当前配置，返回新的配置，返回空配置表示移除 */
  fun configLogger(logger: Class<out FLogger>, block: (FLoggerConfig) -> FLoggerConfig)
}

internal class LogInitScopeImpl : FLogInitScope {
  var directory: File? = null
  var formatter: FLogFormatter? = null
  var storeFactory: FLogStore.Factory? = null
  var dispatcher: FLogDispatcher? = null
  val configHolder: MutableMap<Class<out FLogger>, FLoggerConfig> = mutableMapOf()

  override fun setLogDirectory(directory: File) {
    this.directory = directory
  }

  override fun setLogFormatter(formatter: FLogFormatter) {
    this.formatter = formatter
  }

  override fun setLogStoreFactory(factory: FLogStore.Factory) {
    this.storeFactory = factory
  }

  override fun setLogDispatcher(dispatcher: FLogDispatcher) {
    this.dispatcher = dispatcher
  }

  override fun configLogger(logger: Class<out FLogger>, block: (FLoggerConfig) -> FLoggerConfig) {
    val config = block(configHolder.getOrPut(logger) { FLoggerConfig() })
    if (config.isEmpty()) {
      configHolder.remove(logger)
    } else {
      configHolder[logger] = config
    }
  }
}