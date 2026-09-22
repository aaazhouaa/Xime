package com.kingzcheung.xime.keyboard

/**
 * 拼音编辑气泡的尺寸与可见性判定。
 *
 * UI 层（CandidateBar 实际渲染气泡）与服务层（XimeInputMethodService 预算容器总高）
 * 必须使用同一份定义：气泡现在真实占位，若容器高度没同步加上这部分，
 * 容器会偏矮导致气泡被裁掉或把键盘内容顶出可见区。
 */
object PreeditBubbleMetrics {

    /**
     * 气泡自身高度。
     *
     * 取固定值（而非随内容自适应）的原因：服务层需要确定性数字来撑高 IME 容器，
     * 自适应高度会让容器与服务两侧永久对不齐。
     * 32dp 的下限由编辑态决定：行内有 24dp 的◀/▶/✓ 按钮 + 上下各 3dp 内边距 = 30dp，
     * 再留 2dp 余量；普通态（12sp 文字）远低于此值。
     */
    const val HEIGHT_DP = 32

    /** 气泡与候选栏之间的垂直间距。 */
    const val GAP_DP = 2

    /** 气泡左侧外边距。 */
    const val HORIZONTAL_MARGIN_DP = 4

    /** 编辑态行内按钮（◀ / ▶ / ✓）尺寸。 */
    const val EDIT_BUTTON_SIZE_DP = 24

    /** 气泡显示时需额外计入容器的高度（气泡 + 间距）。 */
    const val TOTAL_DP = HEIGHT_DP + GAP_DP

    /**
     * 是否显示拼音编辑气泡。
     *
     * @param composingText 当前编码展示文本（preedit，空则回退 input）；非空即处于组合态
     * @param inputBoxMode  编码是否改为写入输入框（该模式下气泡不显示）
     * @param overlayClipboard 当前是否为剪贴板覆盖页（不显示气泡）
     */
    fun isVisible(composingText: String, inputBoxMode: Boolean, overlayClipboard: Boolean): Boolean =
        composingText.isNotEmpty() && !inputBoxMode && !overlayClipboard
}
