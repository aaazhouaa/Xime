package com.kingzcheung.xime.service

/**
 * 内联填充建议（autofill InlineSuggestion）chip 的配色计算。
 *
 * 纯 Kotlin、不依赖 android.graphics.Color：JVM 单测里 Android 框架方法返回默认值
 * （`unitTests.isReturnDefaultValues = true`），用框架 API 算出的颜色恒为 0，
 * 无法验证对比度逻辑，故这里全部用位运算实现。
 *
 * 所有颜色均为 0xAARRGGBB。
 */
internal object InlineSuggestionPalette {

    /** WCAG 正文最小对比度（AA）。 */
    const val MIN_CONTRAST_RATIO = 4.5

    private fun redOf(argb: Int): Int = (argb ushr 16) and 0xFF
    private fun greenOf(argb: Int): Int = (argb ushr 8) and 0xFF
    private fun blueOf(argb: Int): Int = argb and 0xFF

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    /**
     * 由候选栏背景色派生 chip 底色，与背景同色系、仅明度略偏一档。
     *
     * 入参的 alpha 被忽略，结果恒为不透明（见内注释）。
     */
    fun chipBackground(barBackgroundArgb: Int, isDark: Boolean): Int {
        fun lighten(c: Int) = (c + (255 - c) * 0.16f).toInt().coerceIn(0, 255)
        fun darken(c: Int) = (c * 0.94f).toInt().coerceIn(0, 255)
        val transform = if (isDark) ::lighten else ::darken
        // 恒输出不透明：即便调用方传入半透明/无 alpha 的背景，也不能把半透明传到 chip——
        // 半透明底色会与随机壁纸/图片背景叠色，无法预估最终对比度，而这正是本类要杜绝的。
        return argb(
            0xFF,
            transform(redOf(barBackgroundArgb)),
            transform(greenOf(barBackgroundArgb)),
            transform(blueOf(barBackgroundArgb)),
        )
    }

    /** 相对亮度（WCAG 2.x 定义），范围 0.0 ~ 1.0。 */
    fun relativeLuminance(argb: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(redOf(argb)) +
            0.7152 * channel(greenOf(argb)) +
            0.0722 * channel(blueOf(argb))
    }

    /** 两色对比度，范围 1.0 ~ 21.0。 */
    fun contrastRatio(fg: Int, bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /**
     * 把 [fg] 朝黑或白插值，直到在 [bg] 上达到 [minRatio]；已达标时原样返回。
     *
     * 方向取与背景亮度相反的一侧（浅底压暗、深底提亮），只改明度、不动色相。
     * 20 步插值即可覆盖 0→1 的过渡，且避免逐位收敛的浮点边界问题。
     */
    fun ensureReadableOn(
        fg: Int,
        bg: Int,
        minRatio: Double = MIN_CONTRAST_RATIO,
    ): Int {
        if (contrastRatio(fg, bg) >= minRatio) return fg
        val targetIsBlack = relativeLuminance(bg) > 0.5
        val r0 = redOf(fg); val g0 = greenOf(fg); val b0 = blueOf(fg)
        val r1 = if (targetIsBlack) 0 else 255
        val g1 = if (targetIsBlack) 0 else 255
        val b1 = if (targetIsBlack) 0 else 255
        var best = fg
        for (i in 1..20) {
            val t = i / 20.0
            val candidate = argb(
                0xFF,
                (r0 + (r1 - r0) * t).toInt().coerceIn(0, 255),
                (g0 + (g1 - g0) * t).toInt().coerceIn(0, 255),
                (b0 + (b1 - b0) * t).toInt().coerceIn(0, 255),
            )
            best = candidate
            if (contrastRatio(candidate, bg) >= minRatio) return candidate
        }
        return best
    }
}
