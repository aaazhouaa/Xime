package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「功能管理」隐藏方案开关的回归测试。
 *
 * 万象的中英标点 / 全角字符 / 简码前置 / 字符集范围 / 辅码查词排序（name 型），
 * 以及简繁转换（options 型 s2s/s2t/s2hk/s2tw）不再展示、也不由 app 恢复旧值，
 * 仅靠引擎按方案声明的 reset 走默认态。
 */
class SchemaSwitchHiddenTest {

    @Test
    fun `隐藏开关清单包含五个 name 型目标`() {
        assertEquals(
            setOf("ascii_punct", "full_shape", "abbrev", "charset_filter", "char_priority"),
            SchemaManager.HIDDEN_SCHEMA_SWITCH_NAMES,
        )
    }

    @Test
    fun `隐藏开关组包含简繁转换四个选项`() {
        assertEquals(
            setOf("s2s", "s2t", "s2hk", "s2tw"),
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
    fun `isHiddenSchemaSwitch 隐藏简繁转换组`() {
        val sw = SchemaSwitch(options = listOf("s2s", "s2t", "s2hk", "s2tw"))
        assertTrue("简繁转换组应被隐藏", SchemaManager.isHiddenSchemaSwitch(sw))
    }

    @Test
    fun `保留项不被隐藏`() {
        // 候选注释、超级提示、英文候选、候选表情、中英翻译等仍应可见
        listOf("super_tips", "english", "emoji", "chinese_english", "context_reorder").forEach { name ->
            assertFalse("$name 不应被隐藏", SchemaManager.isHiddenSchemaSwitch(SchemaSwitch(name = name)))
        }
        // 编码显示、候选注释等 options 型仍应可见
        assertFalse(
            "编码显示组不应被隐藏",
            SchemaManager.isHiddenSchemaSwitch(SchemaSwitch(options = listOf("raw_input", "tone_display", "full_pinyin"))),
        )
        assertFalse(
            "候选注释组不应被隐藏",
            SchemaManager.isHiddenSchemaSwitch(SchemaSwitch(options = listOf("comment_off", "tone_hint", "toneless_hint"))),
        )
    }
}
