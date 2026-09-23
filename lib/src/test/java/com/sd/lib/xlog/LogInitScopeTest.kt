package com.sd.lib.xlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** [FLogInitScope.configLogger] */
class LogInitScopeTest {
  /** 多次配置同一个logger时，在上一次的配置上修改 */
  @Test
  fun testMerge() {
    val scope = LogInitScopeImpl()
    scope.configLogger(ScopeLogger::class.java) { it.copy(tag = "tag") }
    scope.configLogger(ScopeLogger::class.java) { it.copy(level = FLogLevel.Debug) }
    assertEquals(FLoggerConfig(tag = "tag", level = FLogLevel.Debug), scope.configHolder[ScopeLogger::class.java])
  }

  /** 返回空配置时移除 */
  @Test
  fun testRemove() {
    val scope = LogInitScopeImpl()
    scope.configLogger(ScopeLogger::class.java) { it.copy(tag = "tag") }
    scope.configLogger(ScopeLogger::class.java) { FLoggerConfig() }
    assertFalse(scope.configHolder.containsKey(ScopeLogger::class.java))
  }

  /** tag为空串的配置也算空配置，不会保留 */
  @Test
  fun testEmptyTag() {
    val scope = LogInitScopeImpl()
    scope.configLogger(ScopeLogger::class.java) { it.copy(tag = "") }
    assertFalse(scope.configHolder.containsKey(ScopeLogger::class.java))
  }
}

private interface ScopeLogger : FLogger
