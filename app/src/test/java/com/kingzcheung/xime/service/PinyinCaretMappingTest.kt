package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PinyinCaretMappingTest {

    @Test
    fun testPreeditIndexToInputIndexBasic() {
        val preedit = "ni'hao"
        val input = "nihao"

        // 0 -> 0
        assertEquals(0, ImeKeyRouter.preeditIndexToInputIndex(preedit, input, 0))
        // 点击 'n' 之后 (index 1) -> 1
        assertEquals(1, ImeKeyRouter.preeditIndexToInputIndex(preedit, input, 1))
        // 点击 'i' 之后 (index 2) -> 2
        assertEquals(2, ImeKeyRouter.preeditIndexToInputIndex(preedit, input, 2))
        // 点击 '\'' 之后 (index 3) -> 仍然是 2
        assertEquals(2, ImeKeyRouter.preeditIndexToInputIndex(preedit, input, 3))
        // 点击 'h' 之后 (index 4) -> 3
        assertEquals(3, ImeKeyRouter.preeditIndexToInputIndex(preedit, input, 4))
        // 点击 'a' 之后 (index 5) -> 4
        assertEquals(4, ImeKeyRouter.preeditIndexToInputIndex(preedit, input, 5))
        // 点击 'o' 之后 (index 6, 末尾) -> 5
        assertEquals(5, ImeKeyRouter.preeditIndexToInputIndex(preedit, input, 6))
    }

    @Test
    fun testInputIndexToPreeditIndex() {
        val preedit = "ni'hao"

        // input 索引 0 -> preedit 0
        assertEquals(0, ImeKeyRouter.inputIndexToPreeditIndex(preedit, 0))
        // input 索引 1 ('n' 之后) -> preedit 1
        assertEquals(1, ImeKeyRouter.inputIndexToPreeditIndex(preedit, 1))
        // input 索引 2 ('i' 之后) -> preedit 2
        assertEquals(2, ImeKeyRouter.inputIndexToPreeditIndex(preedit, 2))
        // input 索引 3 ('h' 之后) -> preedit 4 (跳过了 '\'')
        assertEquals(4, ImeKeyRouter.inputIndexToPreeditIndex(preedit, 3))
        // input 索引 4 ('a' 之后) -> preedit 5
        assertEquals(5, ImeKeyRouter.inputIndexToPreeditIndex(preedit, 4))
        // input 索引 5 ('o' 之后) -> preedit 6
        assertEquals(6, ImeKeyRouter.inputIndexToPreeditIndex(preedit, 5))
    }

    @Test
    fun testBoundaryAndEmpty() {
        assertEquals(0, ImeKeyRouter.preeditIndexToInputIndex("", "", 0))
        assertEquals(0, ImeKeyRouter.preeditIndexToInputIndex("abc", "abc", -5))
        assertEquals(3, ImeKeyRouter.preeditIndexToInputIndex("abc", "abc", 100))
        assertEquals(0, ImeKeyRouter.inputIndexToPreeditIndex("", 0))
        assertEquals(0, ImeKeyRouter.inputIndexToPreeditIndex("abc", -1))
        assertEquals(3, ImeKeyRouter.inputIndexToPreeditIndex("abc", 100))
    }
}
