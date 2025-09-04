package com.sd.lib.xlog

interface FLogInitScope {
  fun setLogFormatter(formatter: FLogFormatter)
  fun setLogStoreFactory(factory: FLogStore.Factory)
  fun setLogDispatcher(dispatcher: FLogDispatcher)
  fun configLogger(logger: Class<out FLogger>, block: (FLoggerConfig) -> FLoggerConfig)
}

internal class LogInitScopeImpl : FLogInitScope {
  var formatter: FLogFormatter? = null
  var storeFactory: FLogStore.Factory? = null
  var dispatcher: FLogDispatcher? = null
  val configHolder: MutableMap<Class<out FLogger>, FLoggerConfig> = mutableMapOf()

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