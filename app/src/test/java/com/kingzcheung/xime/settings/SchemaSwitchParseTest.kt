package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 方案 switches 解析与「功能管理」展示映射的回归测试。
 *
 * 覆盖万象方案实际使用的三种开关形态：
 * - name 型（两态布尔开关，如 super_tips、charset_filter）
 * - options 型（多选一，如简繁转换 s2s/s2t/s2hk/s2tw、编码显示 raw_input/...）
 * - 带 reset 默认值的开关与开关组
 */
class SchemaSwitchParseTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun writeSchema(content: String): File {
        val f = tempDir.newFile("test.schema.yaml")
        f.writeText(content)
        return f
    }

    @Test
    fun `name type switch with two states`() {
        val f = writeSchema(
            """
            schema:
              schema_id: wanxiang
              name: 万象拼音
            switches:
              - name: super_tips
                states: [提示关, 提示开]
            """.trimIndent()
        )
        val switches = SchemaManager.parseSchemaSwitches(f)
        assertEquals(1, switches.size)
        val sw = switches.first()
        assertEquals("super_tips", sw.name)
        assertEquals(listOf("提示关", "提示开"), sw.states)
        assertTrue(sw.options.isEmpty())
        assertEquals(-1, sw.reset)
    }

    @Test
    fun `options type switch group keeps options and reset`() {
        val f = writeSchema(
            """
            schema:
              schema_id: wanxiang
              name: 万象拼音
            switches:
              - options: [s2s, s2t, s2hk, s2tw]
                states: [简体, 通繁, 港繁, 臺繁]
              - options: [raw_input, tone_display, full_pinyin]
                states: [原编码, 有声调, 无声调]
                reset: 2
            """.trimIndent()
        )
        val switches = SchemaManager.parseSchemaSwitches(f)
        assertEquals(2, switches.size)

        val chinese = switches[0]
        assertEquals("", chinese.name)
        assertEquals(listOf("s2s", "s2t", "s2hk", "s2tw"), chinese.options)
        assertEquals(listOf("简体", "通繁", "港繁", "臺繁"), chinese.states)

        val display = switches[1]
        assertEquals(listOf("raw_input", "tone_display", "full_pinyin"), display.options)
        assertEquals(2, display.reset)
    }

    @Test
    fun `switch without states is skipped`() {
        val f = writeSchema(
            """
            schema:
              schema_id: wanxiang
              name: 万象拼音
            switches:
              - name: english
                reset: 1
              - name: super_tips
                states: [提示关, 提示开]
            """.trimIndent()
        )
        // 无 states 的 english 被跳过（无法展示），仅保留 super_tips
        val switches = SchemaManager.parseSchemaSwitches(f)
        assertEquals(1, switches.size)
        assertEquals("super_tips", switches.first().name)
    }

    @Test
    fun `switch reset parsed for name type`() {
        val f = writeSchema(
            """
            schema:
              schema_id: wanxiang
              name: 万象拼音
            switches:
              - name: abbrev
                states: [简码关, 简码开]
                reset: 1
            """.trimIndent()
        )
        val sw = SchemaManager.parseSchemaSwitches(f).single()
        assertEquals(1, sw.reset)
    }

    @Test
    fun `abbrev may be scalar or list`() {
        val f = writeSchema(
            """
            schema:
              schema_id: wanxiang
              name: 万象拼音
            switches:
              - name: a
                states: [关, 开]
                abbrev: 关开
              - name: b
                states: [关, 开]
                abbrev: [关, 开]
            """.trimIndent()
        )
        val switches = SchemaManager.parseSchemaSwitches(f)
        assertEquals(listOf("关开"), switches[0].abbrev)
        assertEquals(listOf("关", "开"), switches[1].abbrev)
        assertNotNull(switches)
    }

    @Test
    fun `no switches block returns empty`() {
        val f = writeSchema(
            """
            schema:
              schema_id: luna_pinyin
              name: 朙月拼音
            """.trimIndent()
        )
        assertTrue(SchemaManager.parseSchemaSwitches(f).isEmpty())
    }
}
