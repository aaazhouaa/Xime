package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内联填充建议 chip 的对比度保障。
 *
 * 背景（2026-10 截图实证）：候选栏背景 #E3E4E8、chip 文字实测最深仅 #CDD5E2，
 * 对比度约 1.2:1（WCAG 正文门槛 4.5:1），用户反馈"字体看不清"。
 * 本测试锁死"任何主题取色都不会产出不可读的 chip 前景色"。
 */
class InlineSuggestionPaletteTest {

    /** 截图中实测的候选栏背景（slate_gray 亮色主题 keyboard_background）。 */
    private val barBgLight = 0xFFE3E4E8.toInt()

    /** 截图实测的 chip 文字色（浅蓝，几乎与背景同色）。 */
    private val washedOutText = 0xFFCDD5E2.toInt()

    // ── 对比度基础 ──

    @Test
    fun `黑白对比度为最大值21`() {
        assertEquals(21.0, InlineSuggestionPalette.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun `同色对比度为1`() {
        assertEquals(1.0, InlineSuggestionPalette.contrastRatio(barBgLight, barBgLight), 0.001)
    }

    @Test
    fun `对比度与参数顺序无关`() {
        val a = InlineSuggestionPalette.contrastRatio(0xFF1A73E8.toInt(), barBgLight)
        val b = InlineSuggestionPalette.contrastRatio(barBgLight, 0xFF1A73E8.toInt())
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun `截图中的浅蓝文字在候选栏背景上确实不达标`() {
        // 这正是用户看到的"看不清"，须低于门槛，否则说明门槛或公式被改坏了
        assertTrue(
            InlineSuggestionPalette.contrastRatio(washedOutText, barBgLight) <
                InlineSuggestionPalette.MIN_CONTRAST_RATIO
        )
    }

    // ── ensureReadableOn ──

    @Test
    fun `已达标的前景色原样返回`() {
        val black = 0xFF000000.toInt()
        assertEquals(black, InlineSuggestionPalette.ensureReadableOn(black, barBgLight))
    }

    @Test
    fun `截图中的浅蓝文字被修正到达标`() {
        val fixed = InlineSuggestionPalette.ensureReadableOn(washedOutText, barBgLight)
        assertTrue(
            "修正后对比度仍不足：${InlineSuggestionPalette.contrastRatio(fixed, barBgLight)}",
            InlineSuggestionPalette.contrastRatio(fixed, barBgLight) >=
                InlineSuggestionPalette.MIN_CONTRAST_RATIO
        )
    }

    @Test
    fun `浅底上的低对比前景色被压暗`() {
        val fixed = InlineSuggestionPalette.ensureReadableOn(washedOutText, barBgLight)
        // 压暗 = 亮度下降
        assertTrue(
            InlineSuggestionPalette.relativeLuminance(fixed) <
                InlineSuggestionPalette.relativeLuminance(washedOutText)
        )
    }

    @Test
    fun `深底上的低对比前景色被提亮`() {
        val barBgDark = 0xFF1E1838.toInt()
        val darkText = 0xFF2A2244.toInt()   // 在深底上几乎不可见
        val fixed = InlineSuggestionPalette.ensureReadableOn(darkText, barBgDark)
        assertTrue(
            InlineSuggestionPalette.relativeLuminance(fixed) >
                InlineSuggestionPalette.relativeLuminance(darkText)
        )
        assertTrue(
            InlineSuggestionPalette.contrastRatio(fixed, barBgDark) >=
                InlineSuggestionPalette.MIN_CONTRAST_RATIO
        )
    }

    @Test
    fun `极端情形_前景与背景同色也能被修正到达标`() {
        val fixed = InlineSuggestionPalette.ensureReadableOn(barBgLight, barBgLight)
        assertTrue(
            InlineSuggestionPalette.contrastRatio(fixed, barBgLight) >=
                InlineSuggestionPalette.MIN_CONTRAST_RATIO
        )
    }

    @Test
    fun `结果保持不透明`() {
        val fixed = InlineSuggestionPalette.ensureReadableOn(0x00CDD5E2, barBgLight)
        assertEquals(0xFF, (fixed ushr 24) and 0xFF)
    }

    @Test
    fun `各内置主题强调色在对应明暗栏底上都能达标`() {
        // 取自 xime.yaml 的 candidate_text_color / _dark
        val lightTexts = listOf(0xFF1A73E8, 0xFF7D3A52, 0xFF2D5F7A, 0xFF3C5C44, 0xFF14444A).map { it.toInt() }
        val darkTexts = listOf(0xFF8AB4F8, 0xFFE0F7FA, 0xFFFFE0D6, 0xFFD6EAFF, 0xFFE8DEF8).map { it.toInt() }
        lightTexts.forEach { fg ->
            val chip = InlineSuggestionPalette.chipBackground(barBgLight, isDark = false)
            val fixed = InlineSuggestionPalette.ensureReadableOn(fg, chip)
            assertTrue(
                "亮色主题 $fg 修正后仍不足",
                InlineSuggestionPalette.contrastRatio(fixed, chip) >=
                    InlineSuggestionPalette.MIN_CONTRAST_RATIO
            )
        }
        val barBgDark = 0xFF1A1A1A.toInt()
        darkTexts.forEach { fg ->
            val chip = InlineSuggestionPalette.chipBackground(barBgDark, isDark = true)
            val fixed = InlineSuggestionPalette.ensureReadableOn(fg, chip)
            assertTrue(
                "深色主题 $fg 修正后仍不足",
                InlineSuggestionPalette.contrastRatio(fixed, chip) >=
                    InlineSuggestionPalette.MIN_CONTRAST_RATIO
            )
        }
    }

    // ── chipBackground ──

    @Test
    fun `chip底色始终不透明`() {
        // 半透明底色会与随机壁纸叠色，无法预估最终对比度
        listOf(0x00E3E4E8, 0x80E3E4E8, 0xFFE3E4E8, 0x001A1A1A).map { it.toInt() }.forEach { bg ->
            listOf(true, false).forEach { dark ->
                val chip = InlineSuggestionPalette.chipBackground(bg, dark)
                assertEquals("bg=$bg dark=$dark", 0xFF, (chip ushr 24) and 0xFF)
            }
        }
    }

    @Test
    fun `亮色主题下 chip底色比候选栏背景暗`() {
        val chip = InlineSuggestionPalette.chipBackground(barBgLight, isDark = false)
        assertTrue(
            "chip 应与背景有可见区分，实测此前只差 1 个色阶",
            InlineSuggestionPalette.relativeLuminance(chip) <
                InlineSuggestionPalette.relativeLuminance(barBgLight)
        )
    }

    @Test
    fun `深色主题下 chip底色比候选栏背景亮`() {
        val barBgDark = 0xFF1A1A1A.toInt()
        val chip = InlineSuggestionPalette.chipBackground(barBgDark, isDark = true)
        assertTrue(
            InlineSuggestionPalette.relativeLuminance(chip) >
                InlineSuggestionPalette.relativeLuminance(barBgDark)
        )
    }

    @Test
    fun `chip底色与背景的区分足够肉眼可辨`() {
        val chip = InlineSuggestionPalette.chipBackground(barBgLight, isDark = false)
        // 截图实证：差值仅 1 个灰度阶时用户看不出 chip 边界；要求至少 6 阶
        val diff = ((barBgLight and 0xFF) - (chip and 0xFF))
        assertTrue("亮度差仅 $diff 阶", diff >= 6)
    }
}
