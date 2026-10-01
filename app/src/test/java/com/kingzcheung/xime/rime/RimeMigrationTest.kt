package com.kingzcheung.xime.rime

import com.kingzcheung.xime.settings.SchemaManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 白霜（rime-frost）→ 雾凇（rime-ice）内置方案迁移的纯逻辑回归测试。
 *
 * 迁移触发条件与结果由 [SchemaManager.LEGACY_BUILTIN_SCHEMAS] 与
 * [SchemaManager.mergeBuiltinSchemas] 决定，此处覆盖三条关键行为：
 *  1. 老用户（schema_list 全是白霜 id）被整体替换为新内置方案；
 *  2. 用户自选的第三方方案与顺序保持不变；
 *  3. 已迁移用户（无旧 id）再跑一次不变（幂等）。
 */
class RimeMigrationTest {

    /** 迁移等价逻辑：剔除旧内置 id → 补齐新内置方案（与 RimeConfigHelper 保持一致）。 */
    private fun migrate(enabled: List<String>): List<String> =
        SchemaManager.mergeBuiltinSchemas(
            enabled.filterNot { it in SchemaManager.LEGACY_BUILTIN_SCHEMAS }
        )

    private fun hasLegacy(enabled: List<String>): Boolean =
        enabled.any { it in SchemaManager.LEGACY_BUILTIN_SCHEMAS }

    @Test
    fun `白霜老用户列表被整体替换`() {
        val old = listOf(
            "rime_frost",
            "rime_frost_double_pinyin_flypy",
            "rime_frost_t9",
            "melt_eng",
        )
        assertTrue("应被识别为需要迁移", hasLegacy(old))
        assertEquals(
            listOf("rime_ice", "t9", "double_pinyin_flypy"),
            migrate(old),
        )
    }

    @Test
    fun `第三方方案与用户顺序保留`() {
        val old = listOf("wanxiang_t9", "rime_frost", "wanxiang")
        assertTrue(hasLegacy(old))
        val migrated = migrate(old)
        assertEquals("wanxiang_t9", migrated[0])
        assertEquals("wanxiang", migrated[1])
        // 新内置方案追加在尾部，且不含任何白霜 id
        assertTrue(migrated.containsAll(SchemaManager.BUILTIN_SCHEMAS))
        assertTrue(migrated.none { it in SchemaManager.LEGACY_BUILTIN_SCHEMAS })
    }

    @Test
    fun `已迁移列表幂等`() {
        val migrated = migrate(listOf("rime_frost", "rime_frost_t9"))
        assertFalse("迁移后不应再有旧 id", hasLegacy(migrated))
        assertEquals("二次迁移不应改变列表", migrated, migrate(migrated))
    }

    @Test
    fun `全新安装列表不触发迁移`() {
        val fresh = SchemaManager.BUILTIN_SCHEMAS
        assertFalse("新内置方案不应触发迁移", hasLegacy(fresh))
        assertEquals(fresh, migrate(fresh))
    }

    @Test
    fun `仅英文内部方案残留时也触发迁移`() {
        // 老用户被精简到只剩 melt_eng（已降级为内部方案）时，仍需替换为新内置方案
        val old = listOf("melt_eng")
        assertTrue(hasLegacy(old))
        assertEquals(
            SchemaManager.BUILTIN_SCHEMAS,
            migrate(old),
        )
    }
}
