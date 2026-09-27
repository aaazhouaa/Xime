package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「输入方案」列表过滤规则的回归测试。
 *
 * 白霜有若干内部方案（英文次翻译器、部件拆字、反查辅码、注音、仓颉、笔画等）
 * 仅作为主方案的依赖组件被引用，不应出现在用户可选的方案列表中；
 * 保留的用户可选方案与第三方方案则必须可见。
 */
class SchemaSelectableTest {

    @Test
    fun `内部方案不可选`() {
        val internal = listOf(
            "melt_eng_t9", "radical_pinyin", "rime_frost_aux",
        )
        internal.forEach { id ->
            assertFalse("内部方案 $id 不应出现在方案列表", SchemaManager.isUserSelectableSchema(id))
        }
    }

    @Test
    fun `内置白霜方案可选`() {
        SchemaManager.BUILTIN_SCHEMAS.forEach { id ->
            assertTrue("内置方案 $id 应可选", SchemaManager.isUserSelectableSchema(id))
        }
    }

    @Test
    fun `内置白霜方案清单符合预期`() {
        assertEquals(
            listOf(
                "rime_frost",
                "rime_frost_double_pinyin_flypy",
                "rime_frost_t9",
                "melt_eng",
            ),
            SchemaManager.BUILTIN_SCHEMAS,
        )
    }

    @Test
    fun `第三方与旧方案不被误伤`() {
        // 黑名单式过滤：市场方案与历史方案都应保持可选
        listOf("pinyin_simp", "t9_pinyin", "double_pinyin_flypy", "wanxiang", "wubi86").forEach { id ->
            assertTrue("$id 不应被过滤", SchemaManager.isUserSelectableSchema(id))
        }
    }
}
