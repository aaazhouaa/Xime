package com.kingzcheung.xime.keyboard

/**
 * 拼音编辑气泡的尺寸与可触及高度。
 *
 * 气泡视觉上是悬浮的（不占键盘内容布局），但为了让 Android 命中测试能派发触摸，
 * IME 容器必须向上多出 [TOTAL_DP] 的高度把气泡包进 View bounds
 * （clipChildren 只管绘制，不管命中）。UI 层与服务层共用这份定义才不会两边错位。
 */
object PreeditBubbleMetrics {

    /**
     * 气泡自身高度。
     *
     * 取固定值而非随内容自适应：服务层需要可确定性推导的数值来预留悬浮层高度。
     * 34dp = 编辑态文字行 + 上下内边距 + 余量（字体放大后仍不被压扁）。
     */
    const val HEIGHT_DP = 34

    /** 气泡与候选栏之间的垂直间距。 */
    const val GAP_DP = 2

    /** 气泡左侧外边距。 */
    const val HORIZONTAL_MARGIN_DP = 4

    /**
     * 编辑态整体放大倍数。
     *
     * 用 graphicsLayer 的 scale 实现（不参与布局测量），因此放大不会推挤候选栏、
     * 键盘按键或键盘上方任何组件；缩放锚点在左下角，向上生长。
     */
    const val EDIT_SCALE = 1.12f

    /**
     * 气泡悬浮层总高（含间隙）：容器比键盘内容区多出的高度。
     *
     * 必须按【放大后的高度】预留：编辑态气泡以左下角为锚点向上生长，
     * 若按未放大的 [HEIGHT_DP] 预留，长大后顶部会溢出容器 bounds，
     * 那片区域不在命中测试范围内（放大后点气泡顶部无响应）。
     * 向上取整成整数 dp，避免跨分辨率累计误差。
     */
    val TOTAL_DP: Int = kotlin.math.ceil(HEIGHT_DP * EDIT_SCALE).toInt() + GAP_DP

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
