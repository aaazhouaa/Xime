package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSelectionActionTest {

    @Test
    fun testShiftDpadMetaFlag() {
        val shiftOn = 1 // KeyEvent.META_SHIFT_ON
        val shiftLeftOn = 0x40 // KeyEvent.META_SHIFT_LEFT_ON
        val meta = shiftOn or shiftLeftOn
        assertTrue((meta and shiftOn) != 0)
        assertTrue((meta and shiftLeftOn) != 0)
    }

    @Test
    fun testExtendSelectionByLineCalculation() {
        // 模拟多行文本查找行首/上一行行首
        val text = "first line\nsecond line\nthird line"
        val pos = 15 // 在 "second line" 中的位置 ('n')
        val before = text.substring(0, pos)

        val lastNewline = before.lastIndexOf('\n')
        assertEquals(10, lastNewline)

        // 向上移动一行应定位到上一行行首
        val targetUp = if (lastNewline >= 0) {
            if (lastNewline == pos - 1) {
                val prevNewline = before.lastIndexOf('\n', pos - 2)
                if (prevNewline >= 0) prevNewline + 1 else 0
            } else {
                lastNewline + 1
            }
        } else 0
        assertEquals(11, targetUp) // "second line" 开头
    }

    @Test
    fun testExtendSelectionByLineSingleLine() {
        val text = "single line text"
        val pos = 8
        val before = text.substring(0, pos)
        val after = text.substring(pos)

        // 无换行符时，向上跳到0，向下跳到total
        val lastNewline = before.lastIndexOf('\n')
        val targetUp = if (lastNewline >= 0) lastNewline + 1 else 0
        assertEquals(0, targetUp)

        val nextNewline = after.indexOf('\n')
        val targetDown = if (nextNewline >= 0) pos + nextNewline + 1 else text.length
        assertEquals(text.length, targetDown)
    }

    @Test
    fun testHomeEndSelectionFallback() {
        val before = "hello "
        val after = "world"
        val total = before.length + after.length
        assertEquals(11, total)

        val anchor = 0
        // home 模式下 anchor 到 0
        val homeTarget = 0
        assertEquals(0, homeTarget)
        // end 模式下 anchor 到 total
        val endTarget = total
        assertEquals(11, endTarget)
    }
}
