package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「输入方案」列表过滤规则的回归测试。
 *
 * 万象有若干内部方案（abbrev / phrase 系列）仅作为主方案的依赖组件被引用，
 * 不应出现在用户可选的方案列表中；保留的 6 个方案与第三方方案则必须可见。
 */
class SchemaSelectableTest {

    @Test
    fun `内部方案不可选`() {
        val internal = listOf(
            "wanxiang_abbrev", "wanxiang_abbrev_t9",
            "wanxiang_phrase", "wanxiang_phrase_t9",
        )
        internal.forEach { id ->
            assertFalse("内部方案 $id 不应出现在方案列表", SchemaManager.isUserSelectableSchema(id))
        }
    }

    @Test
    fun `保留的六个万象方案可选`() {
        SchemaManager.BUILTIN_SCHEMAS.forEach { id ->
            assertTrue("内置方案 $id 应可选", SchemaManager.isUserSelectableSchema(id))
        }
    }

    @Test
    fun `保留的六个万象方案清单符合预期`() {
        assertEquals(
            listOf(
                "wanxiang", "wanxiang_flypy", "wanxiang_t9",
                "wanxiang_english", "wanxiang_reverse", "wanxiang_mixedcode",
            ),
            SchemaManager.BUILTIN_SCHEMAS,
        )
    }

    @Test
    fun `第三方与旧方案不被误伤`() {
        // 黑名单式过滤：市场方案与历史方案都应保持可选
        listOf("pinyin_simp", "t9_pinyin", "double_pinyin_flypy", "luna_pinyin", "wubi86").forEach { id ->
            assertTrue("$id 不应被过滤", SchemaManager.isUserSelectableSchema(id))
        }
    }
}
