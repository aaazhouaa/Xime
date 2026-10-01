package com.kingzcheung.xime.service

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.SurfaceControl
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlineContentView
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executor

/**
 * 进程内共享的 InlineSuggestion 视图缓存。
 *
 * [InlineSuggestion.inflate] 对同一个对象实例只能调用一次，重复调用会抛出
 * `IllegalStateException("Already called #inflate()")`。这里按对象缓存已
 * inflate 的 [InlineContentView]，跨重组/进出组合复用，并在建议更新或清除时
 * 释放旧视图对应的 surface。
 */
internal object InlineSuggestionViews {
    val views = mutableStateMapOf<InlineSuggestion, InlineContentView?>()

    /**
     * 已调用过 inflate 的建议（永不移除）。
     *
     * [views] 会在清空建议时移除条目驱动 UI 重组，但平台限制是"同一对象只能 inflate
     * 一次"，与是否仍在展示无关——若只靠 [views] 判重，清空后同一条建议再次到达就会
     * 重复 inflate 而崩溃。故另用只增集合记录"已 inflate 过"。
     */
    private val inflated = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<InlineSuggestion, Boolean>()
    )

    private val inflateExecutor: Executor =
        Executor { command -> Handler(Looper.getMainLooper()).post(command) }

    @RequiresApi(Build.VERSION_CODES.R)
    fun inflate(suggestion: InlineSuggestion, context: Context, size: Size) {
        if (views[suggestion] != null || !inflated.add(suggestion)) return
        suggestion.inflate(context, size, inflateExecutor) { contentView ->
            views[suggestion] = contentView
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun retain(keep: Collection<InlineSuggestion>) {
        val keepSet = keep.toSet()
        views.keys.filter { it !in keepSet }.forEach { release(it) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun releaseAll() {
        views.keys.toList().forEach { release(it) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun release(suggestion: InlineSuggestion) {
        views.remove(suggestion)?.surfaceControl?.let { sc ->
            SurfaceControl.Transaction().reparent(sc, null).apply()
        }
    }
}

class InlineSuggestionManager(private val context: Context) {

    var suggestions by mutableStateOf<List<InlineSuggestion>>(emptyList())
        private set

    var isAvailable: Boolean = false
        private set

    var candidateTextColorArgb: Int = Color.BLACK
    var labelTextColorArgb: Int = Color.GRAY
    var isDarkTheme: Boolean = false

    /**
     * 是否存在待展示的建议（与 [suggestions] 同步维护）。
     *
     * 退格处理跑在 key-processing 线程，而 [suggestions] 是 Compose 状态（不可跨线程读）。
     * 这个 volatile 镜像只用于"要不要跑一趟主线程去清"的快速判定：
     * 绝大多数用户没装密码管理器，绝大多数退格也没有建议可清，
     * 没有它就得每次退格都插一次主线程往返——而退格是长按 80ms 重复的高频键，
     * 会直接压在按键延迟预算上（参见 keypress-latency-profile 记录）。
     */
    @Volatile
    private var hasActiveSuggestions: Boolean = false

    /** 跨线程安全：key-processing 线程用它决定是否需要清建议。 */
    fun hasSuggestionsForDelete(): Boolean = hasActiveSuggestions

    /**
     * 候选栏背景色（ARGB）。chip 底色由此派生，保证与键盘其余部分同色系。
     * 由服务层在每次创建请求前写入当前主题取色。
     */
    var candidateBarBackgroundArgb: Int = Color.WHITE

    @RequiresApi(Build.VERSION_CODES.R)
    fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        return try {
            // 自动填充服务（密码管理器）自己决定 chip 上的文字与图标，我们只能下发样式约束。
            // 但对方是否遵守不保证（实测 KeePassDX 的图标保留品牌色、文字沿用其自身前景色），
            // 所以这里除了给出高对比度文字色，还必须给 chip 一层足够实的不透明底色兜底，
            // 才能保证无论对方下发什么前景色，文字都落在可读的浅底/深底上。
            val chipBgColor = chipBackgroundArgb()
            val textColor = InlineSuggestionPalette.ensureReadableOn(candidateTextColorArgb, chipBgColor)
            val altTextColor = InlineSuggestionPalette.ensureReadableOn(labelTextColorArgb, chipBgColor)
            val density = context.resources.displayMetrics.density
            val style = InlineSuggestionUi.newStyleBuilder()
                .setSingleIconChipStyle(
                    ViewStyle.Builder()
                        .setBackgroundColor(Color.TRANSPARENT)
                        .setPadding(0, 0, 0, 0)
                        .build()
                )
                .setChipStyle(
                    ViewStyle.Builder()
                        // 用不透明纯色而非 drawable：设了背景色就不再走以前的
                        // "icon + tint" 路径——那条路径会把 6% alpha 的底色再乘上文字色，
                        // 结果 chip 底与候选栏背景几乎同色（实测只差 1 个色阶），用户看不出边界。
                        .setBackgroundColor(chipBgColor)
                        .setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
                        .build()
                )
                .setTitleStyle(
                    TextViewStyle.Builder()
                        .setTextColor(textColor)
                        .setTextSize(15f)
                        .build()
                )
                .setSubtitleStyle(
                    TextViewStyle.Builder()
                        .setTextColor(altTextColor)
                        .setTextSize(12f)
                        .build()
                )
                .setStartIconStyle(
                    ImageViewStyle.Builder()
                        .setTintList(ColorStateList.valueOf(altTextColor))
                        .build()
                )
                .setEndIconStyle(
                    ImageViewStyle.Builder()
                        .setTintList(ColorStateList.valueOf(altTextColor))
                        .build()
                )
                .build()
            val styleBundle = UiVersions.newStylesBuilder()
                .addStyle(style)
                .build()
            val spec = InlinePresentationSpec.Builder(
                Size(0, 0), Size(800, 400)
            ).setStyle(styleBundle).build()
            InlineSuggestionsRequest.Builder(listOf(spec))
                .setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
                .build()
        } catch (e: Throwable) {
            Log.w("InlineSuggestionManager", "onCreateInlineSuggestionsRequest failed", e)
            null
        }
    }

    /** chip 底色：候选栏背景按主题明暗微调，与相邻候选词区域区分但不突兀。 */
    internal fun chipBackgroundArgb(): Int =
        InlineSuggestionPalette.chipBackground(candidateBarBackgroundArgb, isDarkTheme)

    /**
     * 退格清除后抑制本轮回填。
     *
     * 退格会改变输入框内容 → autofill 服务重新下发同一条建议（实测密码框内如此）；
     * 若只清当前列表，建议会立刻回来，用户看到的就是"按退格删不掉"。
     * 抑制在下一次用户真实输入（[clear]）时解除。
     */
    private var suppressResponses: Boolean = false

    @RequiresApi(Build.VERSION_CODES.R)
    fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        isAvailable = true
        if (suppressResponses) {
            // 用户刚用退格把建议清掉，这里丢弃回填，避免"删了又立刻回来"
            suggestions = emptyList()
            hasActiveSuggestions = false
            return true
        }
        val newSuggestions = response.inlineSuggestions
        // 空响应表示宿主撤销了建议（如用户开始输入），需清除旧建议
        if (newSuggestions.isEmpty() && suggestions.isEmpty()) {
            hasActiveSuggestions = false
            return true
        }
        suggestions = newSuggestions
        hasActiveSuggestions = newSuggestions.isNotEmpty()
        InlineSuggestionViews.retain(newSuggestions)
        return true
    }

    /** 用户开始输入：清空当前建议并恢复接收后续建议。 */
    fun clear() {
        suppressResponses = false
        clearInternal()
    }

    /**
     * 退格清除建议：清当前建议并抑制本轮回填。
     *
     * 仅在确实存在建议时才动抑制位：若无建议则完全不介入，
     * 避免一次"空退格"把后续所有建议都吞掉直到用户开始打字。
     *
     * @return 清除前是否存在建议。调用方用它决定"本次退格是否已被建议消费"
     *   （已消费则不再连带删除输入框字符）。
     */
    fun dismissForBackspace(): Boolean {
        if (suggestions.isEmpty()) return false
        clearInternal()
        suppressResponses = true
        return true
    }

    private fun clearInternal() {
        suggestions = emptyList()
        hasActiveSuggestions = false
        isAvailable = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            InlineSuggestionViews.releaseAll()
        }
    }
}
