package com.kingzcheung.xime.association

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgramTrieTest {

    @Test
    fun `getChildEntries returns direct successors of prefix`() {
        val trie = NgramTrie()
        trie.insert(listOf("我", "爱"))
        trie.insert(listOf("我", "爱"))
        trie.insert(listOf("我", "想"))
        trie.insert(listOf("你", "好"))

        val children = trie.getChildEntries(listOf("我"))
        val tokens = children.map { it.first[1] }.toSet()

        assertEquals(setOf("爱", "想"), tokens)
        // 每个条目应恰好比 prefix 多一个 token
        children.forEach { assertEquals(2, it.first.size) }
    }

    @Test
    fun `getChildEntries matches getAllEntries filtered result`() {
        val trie = NgramTrie()
        listOf(
            listOf("a", "b"),
            listOf("a", "b"),
            listOf("a", "c"),
            listOf("a", "b", "d"),
            listOf("x", "y"),
        ).forEach { trie.insert(it) }

        val prefix = listOf("a")
        val viaChild = trie.getChildEntries(prefix).sortedBy { it.first.joinToString() }
        val viaAll = trie.getAllEntries()
            .filter { (ngram, _) -> ngram.size == prefix.size + 1 && ngram.take(prefix.size) == prefix }
            .sortedBy { it.first.joinToString() }

        assertEquals(viaAll, viaChild)
    }

    @Test
    fun `getChildEntries returns empty for unknown prefix`() {
        val trie = NgramTrie()
        trie.insert(listOf("a", "b"))
        assertTrue(trie.getChildEntries(listOf("不存在")).isEmpty())
    }

    @Test
    fun `getChildEntries returns empty for empty prefix`() {
        val trie = NgramTrie()
        trie.insert(listOf("a", "b"))
        assertTrue(trie.getChildEntries(emptyList()).isEmpty())
    }
}
