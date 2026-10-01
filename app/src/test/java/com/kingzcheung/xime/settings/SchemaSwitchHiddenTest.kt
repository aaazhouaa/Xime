package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「功能管理」隐藏方案开关的回归测试。
 *
 * 白霜的中英标点 / 全角字符 / 简繁转换 / 火星文 / 拆字提示 / 固顶（name 型）
 * 不再展示、也不由 app 恢复旧值，仅靠引擎按方案声明的 reset 走默认态。
 */
class SchemaSwitchHiddenTest {

    @Test
    fun `隐藏开关清单包含六个 name 型目标`() {
        assertEquals(
            setOf("ascii_punct", "full_shape", "traditionalization", "mars", "chaifen", "pin_cand"),
            SchemaManager.HIDDEN_SCHEMA_SWITCH_NAMES,
        )
    }

    @Test
    fun `隐藏开关组为空`() {
        assertEquals(
            emptySet<String>(),
            SchemaManager.HIDDEN_SCHEMA_OPTION_GROUPS,
        )
    }

    @Test
    fun `isHiddenSchemaSwitch 隐藏 name 型开关`() {
        SchemaManager.HIDDEN_SCHEMA_SWITCH_NAMES.forEach { name ->
            assertTrue("name 型 $name 应被隐藏", SchemaManager.isHiddenSchemaSwitch(SchemaSwitch(name = name)))
        }
    }

    @Test
    fun `保留项不被隐藏`() {
        // 候选表情等仍应可见
        listOf("emoji", "ascii_mode").forEach { name ->
            assertFalse("$name 不应被隐藏", SchemaManager.isHiddenSchemaSwitch(SchemaSwitch(name = name)))
        }
        // 非隐藏 options 型仍应可见
        assertFalse(
            "任意 options 型不应被隐藏",
            SchemaManager.isHiddenSchemaSwitch(SchemaSwitch(options = listOf("raw_input", "tone_display", "full_pinyin"))),
        )
    }

    @Test
    fun `废弃开关清单已不再被任何内置方案声明`() {
        // 白霜的 mars/chaifen/pin_cand/chinese_english 在雾凇中均无对应 switches 声明
        assertEquals(
            setOf("mars", "chaifen", "pin_cand", "chinese_english"),
            SchemaManager.DEPRECATED_SCHEMA_SWITCH_NAMES,
        )
    }

    @Test
    fun `保留开关不在废弃清单中`() {
        // 雾凇实际保留的开关（含隐藏但仍在声明中的简繁/全角/中英标点）绝不能被清理
        listOf(
            "ascii_mode", "emoji", "traditionalization", "full_shape",
            "ascii_punct", "search_single_char",
        ).forEach { name ->
            assertFalse("$name 不应被清理", name in SchemaManager.DEPRECATED_SCHEMA_SWITCH_NAMES)
        }
    }
}
