package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.kingzcheung.xime.settings.ButtonLayout
import com.kingzcheung.xime.util.CharInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment

/**
 * 横向滑动移动光标回调（入参为字符步长增量）。
 *
 * 由 KeyboardView 通过 CompositionLocalProvider 提供，KeyboardLayout 读取后
 * 显式传给【字母键与空格键】；其余按键不传（为 null）→ 不支持横向滑光标。
 * 这样既满足“左右滑动仅移动光标、不触发按键原功能”，又不破坏按键组件复用。
 */
val LocalCursorMove = staticCompositionLocalOf<((Int) -> Unit)?> { null }

/** 按键视觉缩进（padding），用于消除 spacedBy 死区。
 *  pointerInput 在 padding 之前，触摸区=全尺寸；
 *  shadow/clip/background 在 padding 之后，视觉区=缩进后。
 *  各布局按需要覆盖：QWERTY 默认 (2.dp, 4.25.dp)，T9/数字 (2.dp, 2.dp) */
val LocalKeyVisualPadding = staticCompositionLocalOf {
    PaddingValues(horizontal = 2.dp, vertical = 4.25.dp)
}

/** 按键圆角半径，由各布局在根层通过 CompositionLocalProvider 提供。
 *  独立于 shadow.shape_radius，为统一配置化而设。 */
val LocalKeyCornerRadius = staticCompositionLocalOf { 8.dp }

/** 按键内容随按键实际高度放大；手机尺寸下保持原字号。 */
internal fun adaptiveKeyContentScale(
    keyHeightDp: Float,
    referenceHeightDp: Float = 56f,
): Float {
    if (!keyHeightDp.isFinite() || keyHeightDp <= 0f) return 1f
    return (keyHeightDp / referenceHeightDp).coerceIn(1f, 1.5f)
}

/** 滑动提示在大按键上比主字符增长稍快，避免视觉上仍然偏小。 */
internal fun adaptiveHintScale(contentScale: Float): Float =
    (1f + (contentScale - 1f) * 1.5f).coerceIn(1f, 1.7f)

/** 气泡跟随提示放大，但略微收敛，避免在平板上显得过重。 */
internal fun adaptiveBubbleScale(contentScale: Float): Float =
    adaptiveHintScale(contentScale).coerceAtMost(1.5f)

/** 主字符放大时同步拉开上下提示，手机尺寸下保持原来的 14dp 间距。 */
internal fun adaptiveHintOffsetDp(contentScale: Float): Float =
    (14f + (contentScale - 1f) * 25f).coerceIn(14f, 24f)

data class SwipeState(
    val isSwiping: Boolean = false,
    val swipeText: String? = null,
    val isSwipeDown: Boolean = false,
    val charInfos: List<CharInfo> = emptyList(),
    val isPressed: Boolean = false,
    val pressedText: String? = null,
    val isDanger: Boolean = false,
    // 长按弹出选择
    val isLongPress: Boolean = false,
    val longPressItems: List<String> = emptyList(),
    val selectedLongPressIndex: Int = 0,
    val longPressDrawableIds: List<Int> = emptyList(),
)

private val shadowColorCache = HashMap<Color, Color>()

internal fun crispShadowColor(backgroundColor: Color): Color {
    return shadowColorCache.getOrPut(backgroundColor) {
        val r = backgroundColor.red
        val g = backgroundColor.green
        val b = backgroundColor.blue
        val maxChroma = maxOf(r, g, b) - minOf(r, g, b)
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        if (maxChroma > 0.05f) {
            Color(r * 0.95f, g * 0.95f, b * 0.95f, backgroundColor.alpha)
        } else if (luminance > 0.5f) {
            Color.Black.copy(alpha = 0.10f)
        } else {
            Color.White.copy(alpha = 0.12f)
        }
    }
}

@Composable
fun KeyButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    swipeText: String? = null,
    swipeDownText: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeDown: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState) -> Unit)? = null,
    fontSize: androidx.compose.ui.unit.TextUnit? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    /** 长按回调（含震动反馈），点按仍走 [onClick] */
    onLongClick: (() -> Unit)? = null,
    /** 右上角角标文字（如 T9 数字键的数字浮标） */
    badgeText: String? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var hasTriggeredSwipeUp by remember { mutableStateOf(false) }
    var hasTriggeredSwipeDown by remember { mutableStateOf(false) }
    var isSwiping by remember { mutableStateOf(false) }
    var isSwipeDown by remember { mutableStateOf(false) }
    var longPressActivated by remember { mutableStateOf(false) }
    var dragActivated by remember { mutableStateOf(false) }
    
    val density = LocalDensity.current
    val view = LocalView.current
    val keyFontFamily = AppFonts.keyFontFamily
    val keyLabelFontFamily = AppFonts.keyLabelFontFamily
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val swipeUpThreshold = with(density) { (-50).dp.toPx() }
    val swipeDownThreshold = with(density) { 50.dp.toPx() }
    val bubbleShowThresholdUp = swipeUpThreshold
    val bubbleShowThresholdDown = swipeDownThreshold
    // 水平位移超过该值视为横向手势（如键盘区滑动移动光标），不再触发点击。
    // 与 KeyboardView 光标手势激活阈值（activationThresholdPx = 60dp）对齐，
    // 消除 30~60dp 位移区间"点击被取消但光标手势未激活"的死区（打字吃键）。
    val horizontalClickCancelThreshold = with(density) { 60.dp.toPx() }

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }
    
    // 辅助函数：生成更深的颜色（混合黑色）
    fun darkenColor(color: Color, factor: Float = 0.15f): Color {
        return Color(
            red = (color.red * (1 - factor)).coerceIn(0f, 1f),
            green = (color.green * (1 - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
    
        Box(
            modifier = modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragActivated = true
                            isPressed = true
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            hasTriggeredSwipeUp = false
                            hasTriggeredSwipeDown = false
                            isSwiping = false
                            isSwipeDown = false
                        },
                        onDragEnd = {
                            val shouldClick = !hasTriggeredSwipeUp && !hasTriggeredSwipeDown && abs(dragOffsetX) < horizontalClickCancelThreshold
                            if (shouldClick) {
                                currentOnClick()
                            }
                            isPressed = false
                            currentOnRelease?.invoke()
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            hasTriggeredSwipeUp = false
                            hasTriggeredSwipeDown = false
                            isSwiping = false
                            isSwipeDown = false
                            longPressActivated = false
                            dragActivated = false
                            onSwipeStateChange?.invoke(SwipeState(false, null, false))
                        },
                        onDragCancel = {
                            isPressed = false
                            currentOnRelease?.invoke()
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            hasTriggeredSwipeUp = false
                            hasTriggeredSwipeDown = false
                            isSwiping = false
                            isSwipeDown = false
                            dragActivated = false
                            onSwipeStateChange?.invoke(SwipeState(false, null, false))
                        },
                        onDrag = { change, dragAmount ->
                            dragOffsetX += dragAmount.x
                            dragOffsetY += dragAmount.y
                            
                            if (dragOffsetY < 0) {
                                if (abs(dragOffsetY) > abs(dragOffsetX) * 1.1f) {
                                    val shouldShowBubble = dragOffsetY < bubbleShowThresholdUp && swipeText != null
                                    if (shouldShowBubble != isSwiping) {
                                        isSwiping = shouldShowBubble
                                        isSwipeDown = false
                                        onSwipeStateChange?.invoke(SwipeState(shouldShowBubble, swipeText, false))
                                    }
                                    
                                    if (dragOffsetY < swipeUpThreshold && !hasTriggeredSwipeUp && swipeText != null && onSwipe != null) {
                                        hasTriggeredSwipeUp = true
                                        onSwipe(swipeText)
                                    }
                                }
                            } else if (dragOffsetY > 0) {
                                if (dragOffsetY > abs(dragOffsetX) * 1.1f) {
                                    val shouldShowBubble = dragOffsetY > bubbleShowThresholdDown && swipeDownText != null
                                    if (shouldShowBubble != isSwipeDown) {
                                        isSwipeDown = shouldShowBubble
                                        isSwiping = shouldShowBubble
                                        onSwipeStateChange?.invoke(SwipeState(shouldShowBubble, swipeDownText, true))
                                    }
                                    
                                    if (dragOffsetY > swipeDownThreshold && !hasTriggeredSwipeDown && swipeDownText != null && onSwipeDown != null) {
                                        hasTriggeredSwipeDown = true
                                        onSwipeDown(swipeDownText)
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(currentOnLongClick != null) {
                    if (currentOnLongClick == null) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                onPress?.invoke()
                                val released = tryAwaitRelease()
                                // 位移/消费导致的取消：保留按压效果，由 onDragEnd/onDragCancel 统一清理，
                                // 避免快速打字时按压反馈提前消失（无气泡感）。
                                // outOfBounds 取消但拖动未激活时立即清理，防止状态泄漏。
                                if (released || !dragActivated) {
                                    isPressed = false
                                    currentOnRelease?.invoke()
                                }
                            },
                            onTap = {
                                if (!dragActivated && !hasTriggeredSwipeUp && !hasTriggeredSwipeDown) currentOnClick()
                            }
                        )
                    } else {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                longPressActivated = false
                                onPress?.invoke()
                                val released = tryAwaitRelease()
                                if (released || !dragActivated) {
                                    isPressed = false
                                    currentOnRelease?.invoke()
                                }
                            },
                            onTap = {
                                if (!dragActivated && !hasTriggeredSwipeUp && !hasTriggeredSwipeDown && !longPressActivated) {
                                    currentOnClick()
                                }
                                longPressActivated = false
                            },
                            onLongPress = {
                                longPressActivated = true
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                currentOnLongClick?.invoke()
                            }
                        )
                    }
                }
            .padding(LocalKeyVisualPadding.current)
            .then(shadowModifier)
            .clip(keyClipShape)
            .background(
                if (isPressed) darkenColor(backgroundColor, 0.2f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor
            ),
        contentAlignment = Alignment.Center
    ) {
        val resolvedFontSize = fontSize ?: if (text.length > 2) 14.sp else 16.sp
        Text(
            text = text,
            color = textColor,
            fontSize = resolvedFontSize,
            fontWeight = if (text.length > 2) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            // 双韵母键面（如 "iang\nuang"）允许两行显示并压缩行距，避免与上标重合；普通键面仍单行
            lineHeight = if (text.contains('\n')) (resolvedFontSize.value * 0.85f).sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            maxLines = if (text.contains('\n')) 2 else 1,
            fontFamily = keyFontFamily
        )

        if (!swipeText.isNullOrEmpty()) {
            val displayText = if (swipeText.length <= 4) swipeText else swipeText.take(4)
            Text(
                text = displayText,
                color = textColor.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(y = (-14).dp),
                fontFamily = keyLabelFontFamily
            )
        }
        
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = textColor.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
                fontFamily = keyLabelFontFamily
            )
        }
    }
}

/** 将按键右上角角标符号精简为紧凑显示字符，避免全角字符过宽导致视觉居中 */
private fun toCompactCornerSymbol(symbol: String): String {
    if (symbol.isEmpty()) return symbol
    val text = if (symbol.length <= 2) symbol else symbol.take(2)
    return when (text) {
        "～" -> "~"
        "／" -> "/"
        "：" -> ":"
        "；" -> ";"
        "“", "”" -> "\""
        "－", "——" -> "-"
        "（" -> "("
        "）" -> ")"
        "＊" -> "*"
        "＠" -> "@"
        "？" -> "?"
        "！" -> "!"
        "％" -> "%"
        "＃" -> "#"
        else -> text
    }
}

@Composable
fun SwipeableKeyButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    layoutMode: ButtonLayout = ButtonLayout.STANDARD,
    icon: Painter? = null,
    swipeText: String? = null,
    swipeDownText: String? = null,
    /** 下滑文本显示在按键上（气泡为空，用于 display:key） */
    swipeDownKeyLabel: String? = null,
    /** 上滑文本显示在按键上（气泡则为空，用于 display:bubble） */
    swipeUpKeyLabel: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeDown: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onLongPressSelect: ((String) -> Unit)? = null,
    longPressItems: List<String>? = null,
    longPressDrawableIds: List<Int>? = null,
    /**
     * 横向滑动移动光标（入参为字符步长增量）。
     *
     * 仅字母键与空格键传入（其他键为 null → 不支持横向滑光标）。
     * 一旦判定为横向手势，本键不再上屏（onDragEnd 不触发 onClick），且屏蔽上/下滑，
     * 实现“左右滑动仅为移动光标，不触发该键原功能”。
     */
    onCursorMove: ((Int) -> Unit)? = null,
    /** 右上角角标文字（如 T9 数字键的数字浮标） */
    badgeText: String? = null,
    /** 双拼静态底部助记小字（显示在字母下方居中位置） */
    shuangpinBottomHint: String? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    swipeFontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var hasTriggeredSwipeUp by remember { mutableStateOf(false) }
    var hasTriggeredSwipeDown by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var isSwiping by remember { mutableStateOf(false) }
    var isSwipeDown by remember { mutableStateOf(false) }
    var buttonBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var dragActivated by remember { mutableStateOf(false) }
    // 横向滑光标手势状态：判定为横向后，本键不再上屏（仅移光标）
    var isCursorGesture by remember { mutableStateOf(false) }
    var cursorAnchorX by remember { mutableStateOf(0f) }
    var lastCursorSteps by remember { mutableStateOf(0) }
    
    val currentText by rememberUpdatedState(text)
    val currentSwipeText by rememberUpdatedState(swipeText)
    val currentSwipeDownText by rememberUpdatedState(swipeDownText)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnSwipeStateChange by rememberUpdatedState(onSwipeStateChange)
    val currentOnCursorMove by rememberUpdatedState(onCursorMove)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPressSelect by rememberUpdatedState(onLongPressSelect)
    val currentLongPressItems by rememberUpdatedState(longPressItems)
    val currentLongPressDrawableIds by rememberUpdatedState(longPressDrawableIds)
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    
    val density = LocalDensity.current
    val swipeUpThreshold = with(density) { (-50).dp.toPx() }
    val swipeDownThreshold = with(density) { 50.dp.toPx() }
    val bubbleShowThresholdUp = swipeUpThreshold
    val bubbleShowThresholdDown = swipeDownThreshold
    // 水平位移超过该值视为横向手势（如键盘区滑动移动光标），不再触发点击。
    // 与 KeyboardView 光标手势激活阈值（activationThresholdPx = 60dp）对齐，
    // 消除 30~60dp 位移区间"点击被取消但光标手势未激活"的死区（打字吃键）。
    val horizontalClickCancelThreshold = with(density) { 60.dp.toPx() }
    // 横向手势进入阈值：取小值使光标手势尽早接管（原键盘层为 60dp，
    // 起手要先跑 60dp 才激活，一次滑动只够移动四五个字符）。
    val cursorActivateThresholdPx = with(density) { 12.dp.toPx() }
    // 每移动一个字符所需的水平位移（14dp 比原 25dp 更跟手）
    val cursorStepPx = with(density) { 14.dp.toPx() }

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }
    val keyLabelFontFamily = AppFonts.keyLabelFontFamily
    val keyFontFamily = AppFonts.keyFontFamily

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragActivated = true
                        isPressed = true
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        hasTriggeredSwipeUp = false
                        hasTriggeredSwipeDown = false
                        isSwiping = false
                        isSwipeDown = false
                        isCursorGesture = false
                        lastCursorSteps = 0
                    },
                    onDragEnd = {
                        // 横向手势不触发点击：左右滑动仅为移动光标，不上屏本键
                        val shouldClick = !isCursorGesture &&
                            !hasTriggeredSwipeUp && !hasTriggeredSwipeDown &&
                            abs(dragOffsetX) < horizontalClickCancelThreshold
                        if (shouldClick) {
                            currentOnClick()
                        }
                        isPressed = false
                        currentOnRelease?.invoke()
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        hasTriggeredSwipeUp = false
                        hasTriggeredSwipeDown = false
                        isSwiping = false
                        isSwipeDown = false
                        isCursorGesture = false
                        lastCursorSteps = 0
                        dragActivated = false
                        currentOnSwipeStateChange?.invoke(SwipeState(false, null, false, emptyList(), false, null), buttonBounds)
                    },
                    onDragCancel = {
                        isPressed = false
                        currentOnRelease?.invoke()
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        hasTriggeredSwipeUp = false
                        hasTriggeredSwipeDown = false
                        isSwiping = false
                        isSwipeDown = false
                        isCursorGesture = false
                        lastCursorSteps = 0
                        dragActivated = false
                        currentOnSwipeStateChange?.invoke(SwipeState(false, null, false, emptyList(), false, null), buttonBounds)
                    },
                    onDrag = { change, dragAmount ->
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y

                        // ── 横向滑光标（仅 onCursorMove 非空时启用，即字母/空格键）──
                        // 判定条件放宽（横向为纵向 1.5 倍即可，原为 4 倍）：
                        // 手指滑动必然带轻微上下抖动，4 倍要求过苛，导致大部分横向滑动
                        // 被判为纵向而根本不识别。
                        var handledByCursor = false
                        if (currentOnCursorMove != null) {
                            if (!isCursorGesture &&
                                abs(dragOffsetX) > cursorActivateThresholdPx &&
                                abs(dragOffsetX) > abs(dragOffsetY) * 1.5f
                            ) {
                                isCursorGesture = true
                                cursorAnchorX = change.position.x
                                // 取消长按重删（与上/下滑语义一致）
                                currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                            }
                            if (isCursorGesture) {
                                change.consume()
                                val dxFromAnchor = change.position.x - cursorAnchorX
                                val steps = (dxFromAnchor / cursorStepPx).toInt()
                                if (steps != lastCursorSteps) {
                                    currentOnCursorMove?.invoke(steps - lastCursorSteps)
                                    lastCursorSteps = steps
                                }
                                handledByCursor = true
                            }
                        }

                        // 横向手势期间不上报上/下滑（避免边移光标边触发清空/撤回）
                        if (!handledByCursor) {
                        if (dragOffsetY < 0) {
                            if (abs(dragOffsetY) > abs(dragOffsetX) * 1.1f) {
                                val shouldShowBubble = dragOffsetY < bubbleShowThresholdUp && currentSwipeText != null
                                if (shouldShowBubble != isSwiping) {
                                    isSwiping = shouldShowBubble
                                    isSwipeDown = false
                                    currentOnSwipeStateChange?.invoke(SwipeState(shouldShowBubble, currentSwipeText, false, emptyList(), false, null), buttonBounds)
                                }
                                
                                // 上滑触发只看回调绑定，不依赖提示文本（swipeText 仅控制气泡/键面提示）：
                                // 提示开关关闭或横屏紧凑不印提示时手势仍可用，与下滑触发语义一致。
                                val onSwipeValue = currentOnSwipe
                                if (dragOffsetY < swipeUpThreshold && !hasTriggeredSwipeUp && onSwipeValue != null) {
                                    hasTriggeredSwipeUp = true
                                    onSwipeValue(currentSwipeText ?: "")
                                }
                            }
                        } else if (dragOffsetY > 0) {
                            if (dragOffsetY > abs(dragOffsetX) * 1.1f) {
                                val shouldShowBubble = dragOffsetY > bubbleShowThresholdDown && currentSwipeDownText != null
                                if (shouldShowBubble != isSwipeDown) {
                                    isSwipeDown = shouldShowBubble
                                    isSwiping = shouldShowBubble
                                    currentOnSwipeStateChange?.invoke(SwipeState(shouldShowBubble, currentSwipeDownText, true, emptyList(), false, null), buttonBounds)
                                }
                                
                                val swipeDownTextValue = currentSwipeDownText
                                val onSwipeDownValue = currentOnSwipeDown
                                if (dragOffsetY > swipeDownThreshold && !hasTriggeredSwipeDown && onSwipeDownValue != null) {
                                    hasTriggeredSwipeDown = true
                                    onSwipeDownValue(swipeDownTextValue ?: "")
                                }
                            }
                        }
                        }
                    }
                )
            }
            .pointerInput(text, currentLongPressItems.isNullOrEmpty()) {
                if (currentLongPressItems.isNullOrEmpty()) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            currentOnSwipeStateChange?.invoke(SwipeState(isPressed = true, pressedText = currentText), buttonBounds)
                            currentOnPress?.invoke()
                            val released = tryAwaitRelease()
                            if (released || !dragActivated) {
                                isPressed = false
                                currentOnRelease?.invoke()
                                currentOnSwipeStateChange?.invoke(SwipeState(false, null, false, emptyList(), false, null), buttonBounds)
                            }
                        },
                        onTap = {
                            if (!dragActivated && !hasTriggeredSwipeUp && !hasTriggeredSwipeDown) currentOnClick()
                        }
                    )
                    return@pointerInput
                }
                
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    var localLongPressTriggered = false
                    var selectedIdx = 0
                    val downX = down.position.x
                    val items = currentLongPressItems ?: return@awaitEachGesture
                    
                    currentOnSwipeStateChange?.invoke(
                        SwipeState(isPressed = true, pressedText = currentText), buttonBounds
                    )
                    currentOnPress?.invoke()
                    
                    val longPressJob = scope.launch {
                        delay(400L)
                        localLongPressTriggered = true
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        currentOnSwipeStateChange?.invoke(
                            SwipeState(
                                isPressed = true,
                                isLongPress = true,
                                longPressItems = items,
                                selectedLongPressIndex = 0,
                                longPressDrawableIds = currentLongPressDrawableIds ?: emptyList()
                            ),
                            buttonBounds
                        )
                    }
                    
                    val cancelThresholdPx = with(density) { 5.dp.toPx() }
                    val downY = down.position.y
                    var swipeDetected = false
                    
                    try {
                        var lastReportedIdx = -1
                        var completed = false
                        while (!completed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            
                            if (change.isConsumed) continue
                            
                            if (!localLongPressTriggered) {
                                val deltaX = change.position.x - downX
                                val deltaY = change.position.y - downY
                                if (kotlin.math.abs(deltaX) > cancelThresholdPx || kotlin.math.abs(deltaY) > cancelThresholdPx) {
                                    swipeDetected = true
                                    longPressJob.cancel()
                                }
                            }
                            
                            if (localLongPressTriggered) {
                                val deltaX = change.position.x - downX
                                val itemWidth = buttonBounds.width / items.size
                                selectedIdx = ((deltaX / itemWidth) + if (items.size > 1) 0.5f else 0f).toInt()
                                    .coerceIn(0, items.size - 1)
                                
                                if (selectedIdx != lastReportedIdx) {
                                    lastReportedIdx = selectedIdx
                                    currentOnSwipeStateChange?.invoke(
                                        SwipeState(
                                            isPressed = true,
                                            isLongPress = true,
                                            longPressItems = items,
                                            selectedLongPressIndex = selectedIdx,
                                            longPressDrawableIds = currentLongPressDrawableIds ?: emptyList()
                                        ),
                                        buttonBounds
                                    )
                                }
                                change.consume()
                            }
                            
                            if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Release) {
                                completed = true
                                if (localLongPressTriggered) {
                                    val selected = items.getOrNull(selectedIdx)
                                    if (selected != null) {
                                        currentOnLongPressSelect?.invoke(selected)
                                    }
                                } else if (!dragActivated) {
                                    // 注意：不能再用 swipeDetected 抑制点击——swipeDetected 由 5dp 位移触发，
                                    // 而 dragActivated 由 touch slop（更大）触发。两者之间的位移区间
                                    // （5dp~touchSlop）若被 swipeDetected 吞掉点击，且 drag 未激活无 dragEnd
                                    // 兜底，会造成快速打字漏键（吃键）。5dp 位移只用于取消长按（longPressJob）。
                                    currentOnClick()
                                }
                            }
                        }
                    } finally {
                        longPressJob.cancel()
                        isPressed = false
                        currentOnRelease?.invoke()
                        currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                    }
                }
            }
            .onGloballyPositioned { coordinates ->
                buttonBounds = coordinates.boundsInRoot()
            }
            .padding(LocalKeyVisualPadding.current)
            .then(shadowModifier)
            .clip(keyClipShape)
            .background(
                if (isPressed) backgroundColor.copy(alpha = 0.7f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor
            ),
        contentAlignment = if (layoutMode == ButtonLayout.COMPACT) Alignment.TopStart else Alignment.Center
    ) {
        val contentScale = adaptiveKeyContentScale(maxHeight.value)
        val hintScale = adaptiveHintScale(contentScale)
        val hintOffset = adaptiveHintOffsetDp(contentScale).dp
        val effectiveSwipeFontSize = (11f * hintScale).sp

        if (layoutMode == ButtonLayout.COMPACT) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = text,
                        tint = textColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 2.dp, start = 4.dp)
                            .size(16.dp)
                    )
                } else {
                    Text(
                        text = text,
                        color = textColor,
                        fontSize = ((if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) fontSize.value else if (text.length > 2) 13f else 16f) * contentScale).sp,
                        fontWeight = if (text.length > 2) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        lineHeight = 1.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 2.dp, start = 4.dp),
                        fontFamily = keyFontFamily
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .padding(top = 4.dp, end = 4.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    val swipeUpHint = swipeUpKeyLabel ?: swipeText
                    if (!swipeUpHint.isNullOrEmpty()) {
                        val displayText = if (swipeUpHint.length <= 2) swipeUpHint else swipeUpHint.take(2)
                        Text(
                            text = displayText,
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = effectiveSwipeFontSize,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            lineHeight = 1.sp
                        )
                    }

                    val swipeDownHint = swipeDownKeyLabel
                    if (!swipeDownHint.isNullOrEmpty()) {
                        val hasChinese = swipeDownHint.any { it in '\u4e00'..'\u9fff' || it in '\u3400'..'\u4dbf' || it in '\uf900'..'\ufaff' }
                        val adjustedFontSize = if (hasChinese && effectiveSwipeFontSize > 6.sp) (effectiveSwipeFontSize.value * 0.85f).sp else effectiveSwipeFontSize
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            val displayText = if (swipeDownHint.length <= 12) swipeDownHint else swipeDownHint.take(12)
                            Text(
                                text = displayText,
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = adjustedFontSize,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Right,
                                maxLines = 3,
                                lineHeight = adjustedFontSize,
                                fontFamily = keyLabelFontFamily
                            )
                        }
                    }
                }
            }
        } else if (!shuangpinBottomHint.isNullOrEmpty()) {
            // 双拼助记模式：精确还原图片中的按键内布局结构
            // 左上角：主字母
            // 右上角：角标符号/数字（位于按键右上角，往右靠，与字母拉开明显间距）
            // 右下角：双拼声韵母助记文本（支持多行紧凑排列）
            Box(modifier = Modifier.fillMaxSize()) {
                val swipeUpHint = swipeUpKeyLabel ?: swipeText
                val cornerSymbol = if (!swipeUpHint.isNullOrEmpty() && swipeUpHint != badgeText) {
                    if (swipeUpHint.length <= 2) swipeUpHint else swipeUpHint.take(2)
                } else badgeText

                // 左上角：主字母
                Text(
                    text = text,
                    color = textColor,
                    fontSize = (17f * contentScale).sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    lineHeight = (18f * contentScale).sp,
                    fontFamily = keyFontFamily,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = (4f * contentScale).dp, top = (3.5f * contentScale).dp)
                )

                // 右上角：角标符号/数字（最大化靠右对齐，全角转半角避免占位居中）
                if (!cornerSymbol.isNullOrEmpty()) {
                    val displayCornerSymbol = toCompactCornerSymbol(cornerSymbol)
                    Text(
                        text = displayCornerSymbol,
                        color = textColor.copy(alpha = 0.55f),
                        fontSize = (11f * hintScale).sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        lineHeight = (11.5f * hintScale).sp,
                        fontFamily = keyLabelFontFamily,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = (3f * contentScale).dp, top = (3f * contentScale).dp)
                    )
                }

                // 右下角：双拼声韵母助记小字（所有键字号保持统一一致）
                val baseHintFontSize = 10f
                val bottomFontSize = (baseHintFontSize * hintScale).sp
                Text(
                    text = shuangpinBottomHint,
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = bottomFontSize,
                    lineHeight = (baseHintFontSize * hintScale * 1.05f).sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.End,
                    maxLines = 3,
                    fontFamily = keyLabelFontFamily,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = (4f * contentScale).dp, bottom = (3f * contentScale).dp)
                )

                // 左下角：下滑功能文字（如复制、粘贴等），双拼模式下竖排显示（每个汉字一行）
                if (!swipeDownKeyLabel.isNullOrEmpty()) {
                    val verticalText = swipeDownKeyLabel.map { it.toString() }.joinToString("\n")
                    val verticalFontSize = (8f * hintScale).sp
                    Text(
                        text = verticalText,
                        color = textColor.copy(alpha = 0.55f),
                        fontSize = verticalFontSize,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        maxLines = 4,
                        lineHeight = (8.5f * hintScale).sp,
                        fontFamily = keyLabelFontFamily,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = (3f * contentScale).dp, bottom = (2.5f * contentScale).dp)
                    )
                }
            }
        } else {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = text,
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                val resolvedFontSize = if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) fontSize
                    else if (text.length > 2) 14.sp else 18.sp
                Text(
                    text = text,
                    color = textColor,
                    fontSize = ((if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) fontSize.value else if (text.length > 2) 14f else 18f) * contentScale).sp,
                    fontWeight = if (text.length > 2) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    // 双韵母键面（如 "iang\nuang"）允许两行显示并压缩行距，避免与上标重合；普通键面仍单行
                    lineHeight = if (text.contains('\n')) (resolvedFontSize.value * contentScale * 0.85f).sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    maxLines = if (text.contains('\n')) 2 else 1,
                    fontFamily = keyFontFamily
                )
            }

            // 上滑提示与角标文字相同（如九键/笔画上滑输入键面数字）时不再重复渲染提示，
            // 角标已表达该信息；swipeText 状态保持非空，上滑触发与气泡不受影响。
            if (!(swipeUpKeyLabel ?: swipeText).isNullOrEmpty() && (swipeUpKeyLabel ?: swipeText) != badgeText) {
                val keyLabel = (swipeUpKeyLabel ?: swipeText)!!
                val displayText = if (keyLabel.length <= 4) keyLabel else keyLabel.take(4)
                Text(
                    text = displayText,
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = effectiveSwipeFontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    lineHeight = (11.5f * hintScale).sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = (3f * contentScale).dp, end = (3f * contentScale).dp),
                    fontFamily = keyLabelFontFamily
                )
            }

            if (!swipeDownKeyLabel.isNullOrEmpty()) {
                val displayText = if (swipeDownKeyLabel.length <= 4) swipeDownKeyLabel else swipeDownKeyLabel.take(4)
                Text(
                    text = displayText,
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = (9f * hintScale).sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    lineHeight = (10f * hintScale).sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (3f * contentScale).dp),
                    fontFamily = keyLabelFontFamily
                )
            }

            if (badgeText != null) {
                Text(
                    text = badgeText,
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = (10f * hintScale).sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    lineHeight = 1.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                )
            }
        }
    }
}

@Composable
fun KeyboardRow(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    isShifted: Boolean,
    modifier: Modifier = Modifier,
    swipeKeys: List<String>? = null,
    swipeDownKeys: List<String>? = null,
    onSwipeKey: ((String) -> Unit)? = null,
    onSwipeDownKey: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onKeyPressDown: ((String) -> Unit)? = null,
    onKeyRelease: ((String) -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        keys.forEachIndexed { index, key ->
            val swipeText = swipeKeys?.getOrNull(index)
            val swipeDownText = swipeDownKeys?.getOrNull(index)
            val rowOnClick = remember(key, onKeyPress) { { onKeyPress(key) } }
            val rowOnPress: (() -> Unit)? = remember(key, onKeyPressDown) { { onKeyPressDown?.invoke(key); Unit } }
            val rowOnRelease: (() -> Unit)? = remember(key, onKeyRelease) { { onKeyRelease?.invoke(key); Unit } }
            SwipeableKeyButton(
                text = if (isShifted) key.uppercase() else key,
                onClick = rowOnClick,
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                swipeText = swipeText,
                swipeDownText = swipeDownText,
                onSwipe = onSwipeKey,
                onSwipeDown = onSwipeDownKey,
                onSwipeStateChange = onSwipeStateChange,
                onPress = rowOnPress,
                onRelease = rowOnRelease
            )
        }
    }
}

@Composable
fun IconKeyButton(
    icon: Painter,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }
    
    // 辅助函数：生成更深的颜色（混合黑色）
    fun darkenColor(color: Color, factor: Float = 0.15f): Color {
        return Color(
            red = (color.red * (1 - factor)).coerceIn(0f, 1f),
            green = (color.green * (1 - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress?.invoke()
                        tryAwaitRelease()
                        isPressed = false
                        onRelease?.invoke()
                    },
                    onTap = {
                        onClick()
                    }
                )
            }
            .padding(LocalKeyVisualPadding.current)
            .then(shadowModifier)
            .clip(keyClipShape)
            .background(
                if (isPressed) darkenColor(backgroundColor, 0.1f)
                else if (isHighlighted) darkenColor(backgroundColor, 0.2f)
                else backgroundColor
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )

        // 右上角小圆点指示 — 仅在 isHighlighted 时显示
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(iconColor)
            )
        }
    }
}

@Composable
fun SwipeableIconKeyButton(
    icon: Painter,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    swipeText: String? = null,
    onSwipe: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    // 上滑/下滑/左滑增强
    swipeUpLabel: String? = null,
    swipeDownLabel: String? = null,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var hasTriggeredSwipe by remember { mutableStateOf(false) }
    var hasTriggeredSwipeDown by remember { mutableStateOf(false) }
    var hasTriggeredSwipeLeft by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isSwipingUp by remember { mutableStateOf(false) }
    var isSwipingDown by remember { mutableStateOf(false) }
    var isDangerZone by remember { mutableStateOf(false) }
    var hasReachedClearThreshold by remember { mutableStateOf(false) }
    var hasReachedUndoThreshold by remember { mutableStateOf(false) }
    var isLongPress by remember { mutableStateOf(false) }
    var hasTriggeredLongPress by remember { mutableStateOf(false) }
    var buttonBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var dragActivated by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeStateChange by rememberUpdatedState(onSwipeStateChange)
    val scope = rememberCoroutineScope()
    val keyLabelFontFamily = AppFonts.keyLabelFontFamily
    
    val density = LocalDensity.current
    val swipeUpThreshold = with(density) { (-50).dp.toPx() }
    val swipeDownThreshold = with(density) { 50.dp.toPx() }
    val swipeLeftThreshold = with(density) { (-50).dp.toPx() }
    val bubbleShowThresholdUp = swipeUpThreshold
    val bubbleShowThresholdDown = swipeDownThreshold
    
    // 上滑清空/下滑撤回需要更大的滑动距离，防止误触
    val clearActionThreshold = with(density) { (-50).dp.toPx() }
    val undoActionThreshold = with(density) { 50.dp.toPx() }
    // 水平位移超过该值视为横向手势（如键盘区滑动移动光标），不再触发点击。
    // 与 KeyboardView 光标手势激活阈值（activationThresholdPx = 60dp）对齐，
    // 消除 30~60dp 位移区间"点击被取消但光标手势未激活"的死区（打字吃键）。
    val horizontalClickCancelThreshold = with(density) { 60.dp.toPx() }

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }
    
    fun darkenColor(color: Color, factor: Float = 0.15f): Color {
        return Color(
            red = (color.red * (1 - factor)).coerceIn(0f, 1f),
            green = (color.green * (1 - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    isPressed = true
                    currentOnPress?.invoke()

                    var totalDx = 0f
                    var totalDy = 0f
                    var longPressActive = false
                    var swipeTriggered = false
                    var localLongPressTriggered = false

                    val longPressJob = if (currentOnLongClick != null) {
                        scope.launch {
                            delay(400)
                            longPressActive = true
                            localLongPressTriggered = true
                            while (true) {
                                currentOnLongClick?.invoke()
                                delay(35)
                            }
                        }
                    } else null

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }

                            val deltaX = change.position.x - change.previousPosition.x
                            val deltaY = change.position.y - change.previousPosition.y
                            totalDx += deltaX
                            totalDy += deltaY

                            // 只要位移超过大幅手势滑动阈值，取消长按
                            if (longPressActive && (totalDy < swipeUpThreshold || totalDy > swipeDownThreshold || totalDx < swipeLeftThreshold || totalDx > horizontalClickCancelThreshold)) {
                                longPressActive = false
                                longPressJob?.cancel()
                            }

                            // 移动幅度超过25dp且长按未激活，提前取消长按定时器响应滑动
                            val moveDistance = kotlin.math.sqrt(totalDx * totalDx + totalDy * totalDy)
                            if (!longPressActive && moveDistance > with(density) { 25.dp.toPx() }) {
                                longPressJob?.cancel()
                            }

                            // 左滑检测
                            if (totalDx < swipeLeftThreshold && !hasTriggeredSwipeLeft && currentOnSwipeLeft != null) {
                                hasTriggeredSwipeLeft = true
                                swipeTriggered = true
                                currentOnSwipeLeft?.invoke()
                            }

                            // 上滑气泡与状态检测
                            if (totalDy < 0 && totalDx >= swipeLeftThreshold) {
                                val showUp = totalDy < bubbleShowThresholdUp && swipeUpLabel != null
                                isSwipingUp = showUp
                                isSwipingDown = false
                                val inDanger = totalDy < clearActionThreshold
                                isDangerZone = inDanger
                                hasReachedClearThreshold = inDanger
                                currentOnSwipeStateChange?.invoke(
                                    SwipeState(isSwiping = showUp, swipeText = swipeUpLabel, isSwipeDown = false, isDanger = inDanger),
                                    buttonBounds
                                )
                            }

                            // 下滑气泡与状态检测
                            if (totalDy > 0 && totalDx >= swipeLeftThreshold) {
                                val showDown = totalDy > bubbleShowThresholdDown && swipeDownLabel != null
                                isSwipingDown = showDown
                                isSwipingUp = false
                                val inDanger = totalDy > undoActionThreshold
                                isDangerZone = inDanger
                                hasReachedUndoThreshold = inDanger
                                currentOnSwipeStateChange?.invoke(
                                    SwipeState(isSwiping = showDown, swipeText = swipeDownLabel, isSwipeDown = true, isDanger = inDanger),
                                    buttonBounds
                                )
                            }

                            change.consume()
                        }
                    } finally {
                        longPressJob?.cancel()
                        isPressed = false
                        currentOnRelease?.invoke()

                        if (hasReachedClearThreshold && currentOnSwipeUp != null) {
                            currentOnSwipeUp?.invoke()
                        } else if (hasReachedUndoThreshold && currentOnSwipeDown != null) {
                            currentOnSwipeDown?.invoke()
                        } else if (!localLongPressTriggered && !swipeTriggered && !hasTriggeredSwipeLeft) {
                            currentOnClick()
                        }

                        hasTriggeredSwipeLeft = false
                        isSwipingUp = false
                        isSwipingDown = false
                        isDangerZone = false
                        hasReachedClearThreshold = false
                        hasReachedUndoThreshold = false
                        currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                    }
                }
            }
            .onGloballyPositioned { coordinates ->
                buttonBounds = coordinates.boundsInRoot()
            }
            .padding(LocalKeyVisualPadding.current)
            .then(shadowModifier)
            .clip(keyClipShape)
            .background(
                if (isPressed) darkenColor(backgroundColor, 0.2f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
        
        if (!swipeText.isNullOrEmpty()) {
            Text(
                text = swipeText,
                color = iconColor.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(y = (-14).dp),
                fontFamily = keyLabelFontFamily
            )
        }
    }
}
