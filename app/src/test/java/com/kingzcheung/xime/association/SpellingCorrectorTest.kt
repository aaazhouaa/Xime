package com.kingzcheung.xime.association

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SpellingCorrector 纯函数测试。
 *
 * 只覆盖 rank / distance（不依赖 TrieAssociationEngine 的 Android 资源加载，
 * 故可在 JVM 单测中直接运行）。
 */
class SpellingCorrectorTest {

    // ---------- distance：受限编辑距离 ----------

    @Test
    fun `identical words have zero distance`() {
        assertEquals(0, SpellingCorrector.distance("hello", "hello", 1, 0))
    }

    @Test
    fun `single replacement costs one operation`() {
        // helo → help：末位 o→p
        assertEquals(3, SpellingCorrector.distance("helo", "help", 1, 0))
    }

    @Test
    fun `single insertion costs one operation`() {
        // helo → hello：插入一个 l
        assertEquals(3, SpellingCorrector.distance("helo", "hello", 1, 0))
    }

    @Test
    fun `single removal costs one operation`() {
        // helloo → hello：删除一个 o
        assertEquals(3, SpellingCorrector.distance("helloo", "hello", 2, 1))
    }

    @Test
    fun `length gap beyond budget is rejected without computing`() {
        // |3 - 8| = 5 > maxDiff 1
        assertEquals(-1, SpellingCorrector.distance("cat", "elephant", 1, 0))
    }

    @Test
    fun `too many removals is rejected`() {
        // helloo → hell：删 2 字。maxDiff 允许（2 ≤ 2），但 maxRemove = 1 不允许
        assertEquals(-1, SpellingCorrector.distance("helloo", "hell", 2, 1))
    }

    @Test
    fun `two replacements within budget`() {
        // abcdef → abXYef：位置 2、3 各一次替换
        assertEquals(6, SpellingCorrector.distance("abcdef", "abXYef", 2, 1))
    }

    @Test
    fun `transposition counts as two replacements`() {
        // recieve → receive：ie/ei 换位，按替换计为 2 次操作
        assertEquals(6, SpellingCorrector.distance("recieve", "receive", 2, 1))
    }

    @Test
    fun `repeated characters do not inflate distance`() {
        // 插入预算内：helloo → hellooo
        assertEquals(3, SpellingCorrector.distance("helloo", "hellooo", 2, 1))
    }

    // ---------- rank：候选排序与过滤 ----------

    @Test
    fun `input shorter than three is not corrected`() {
        assertEquals(
            emptyList<String>(),
            SpellingCorrector.rank("ab", listOf("abc" to 1), 5),
        )
    }

    @Test
    fun `empty vocabulary yields nothing`() {
        assertEquals(emptyList<String>(), SpellingCorrector.rank("helo", emptyList(), 5))
    }

    @Test
    fun `exact match is not suggested`() {
        val result = SpellingCorrector.rank("abc", listOf("abc" to 1), 5)
        assertTrue("完全相同词不应作为纠错建议", result.isEmpty())
    }

    @Test
    fun `word prefixed candidates belong to completion not correction`() {
        // abcd 以 abc 为前缀 → 属前缀补全场景，纠错应跳过
        val result = SpellingCorrector.rank("abc", listOf("abcd" to 1), 5)
        assertTrue("以输入为前缀的词应由 Trie 补全处理，不在此纠错", result.isEmpty())
    }

    @Test
    fun `out of budget candidates are filtered out`() {
        // xyz 与 abc 相差 3 次替换，超 maxDiff 1
        val result = SpellingCorrector.rank("abc", listOf("xyz" to 1), 5)
        assertTrue("超编辑预算的候选应被过滤", result.isEmpty())
    }

    @Test
    fun `same distance ordered by lower frequency index first`() {
        val result = SpellingCorrector.rank(
            "abc",
            listOf(
                "abd" to 10,   // 1 替换
                "aXc" to 5,    // 1 替换
            ),
            5,
        )
        assertEquals(listOf("aXc", "abd"), result)
    }

    @Test
    fun `smaller distance wins over better frequency`() {
        val result = SpellingCorrector.rank(
            "abcdef",
            listOf(
                "abXYef" to 1,   // 2 替换 → 距离 6
                "abcXef" to 20,  // 1 替换 → 距离 3
            ),
            5,
        )
        assertEquals("距离近者优先，即便词频更低", listOf("abcXef", "abXYef"), result)
    }

    @Test
    fun `limit truncates suggestions`() {
        val result = SpellingCorrector.rank(
            "abc",
            listOf(
                "abd" to 1,
                "aXc" to 2,
                "abY" to 3,
            ),
            2,
        )
        assertEquals(2, result.size)
        assertEquals(listOf("abd", "aXc"), result)
    }

    @Test
    fun `mixed vocabulary keeps only in-budget corrections`() {
        val result = SpellingCorrector.rank(
            "helo",
            listOf(
                "hello" to 5,      // 1 插入 → 3
                "help" to 1,       // 1 替换 → 3
                "hero" to 2,       // 1 替换 → 3
                "helo" to 0,       // 完全相同 → 跳过
                "heloWorld" to 9,  // 以输入为前缀 → 跳过
                "elephant" to 3,   // 长度差超预算 → 跳过
            ),
            5,
        )
        assertEquals(listOf("help", "hero", "hello"), result)
    }
}
