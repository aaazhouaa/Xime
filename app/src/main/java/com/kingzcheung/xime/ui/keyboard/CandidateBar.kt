package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.service.PredictionManager
import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import com.kingzcheung.xime.service.ImeKeyRouter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.kingzcheung.xime.R
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PreeditBubbleMetrics
import com.kingzcheung.xime.keyboard.PanelType
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.shuangpin.LocalShuangpinKeyHint
import com.kingzcheung.xime.shuangpin.ShuangpinSchemes
import com.kingzcheung.xime.speech.RecognitionState

@Immutable
data class CandidateBarVisuals(
    val backgroundColor: Color,
    val textColor: Color,
    val dividerColor: Color,
    val accentColor: Color = Color(0xFF1A73E8),
    val selectedTextColor: Color = Color(0xFF1A73E8),
    val isDarkTheme: Boolean = false,
)

data class CandidateBarCallbacks(
    val onCandidateSelect: (Int) -> Unit,
    val onLogoClick: (() -> Unit)? = null,
    val onBack: (() -> Unit)? = null,
    val onHideKeyboard: (() -> Unit)? = null,
    val onShowMoreCandidates: (() -> Unit)? = null,
    val onClearAssociation: (() -> Unit)? = null,
    val onInputTextClick: (() -> Unit)? = null,
    val onAssociationSelect: ((Int) -> Unit)? = null,
    // 长按候选：抛事件给宿主（键盘视图内弹确认覆盖层，不弹独立窗口——
    // 焦点型弹窗会抢焦点导致 IME 被系统收起）。
    val onCandidateLongPress: ((Int) -> Unit)? = null,
    val onPinyinCaretMove: ((Int) -> Unit)? = null,
    /** 非编辑态单击气泡：入参为原始输入串下标，进入编辑态并把光标落到该处。 */
    val onPinyinEditAt: ((Int) -> Unit)? = null,
    val onPinyinEditingToggle: ((Boolean) -> Unit)? = null,
)

@Composable
fun CandidateBar(
    state: CandidateBarState,
    page: KeyboardPage = KeyboardPage.Main(com.kingzcheung.xime.keyboard.MainType.FULL),
    candidatePageExpanded: Boolean = false,
    toolbarActions: List<ToolbarAction> = emptyList(),
    visuals: CandidateBarVisuals,
    callbacks: CandidateBarCallbacks,
    inlineSuggestions: List<*> = listOf<Any>(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    isFloatingMode: Boolean = false,
    isVoiceSticky: Boolean = false,
    voiceAmplitude: Float = 0f,
    voiceSpectrum: FloatArray = FloatArray(16),
    voiceRecognitionState: RecognitionState = RecognitionState.IDLE,
    voicePluginName: String = "",
    /** 短信验证码：非空时在候选行内以分割线分隔显示，点击回调 [onSmsCodeClick]。 */
    smsCode: String? = null,
    /**
     * 拼音气泡在窗口中的实时边界（left, top, right, bottom，px）。
     * 服务层用它构造 TOUCHABLE_INSETS_REGION 并集（气泡浮在键盘内容区之上）。
     */
    onPreeditBubbleBounds: ((Int, Int, Int, Int) -> Unit)? = null,
    onSmsCodeClick: (() -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = !isFloatingMode && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (isLandscape) 50.dp else 8.dp
    val context = LocalContext.current

    // M3 角色色：图标按钮背景用 surface 与 primary 的混合色调（带种子色但不过于强烈），
    // 按压态用 onSurface 12% state layer
    val iconButtonContainer = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.15f
    )
    val iconButtonTint = MaterialTheme.colorScheme.onSurfaceVariant
    val showComments = SettingsPreferences.showCandidateComments(context)
    val inputTextLocation = SettingsPreferences.getInputTextLocation(context)
    val showInputBoxStyle = inputTextLocation == SettingsPreferences.INPUT_TEXT_INPUT_BOX
    val candidateTextSize = SettingsPreferences.getCandidateTextSize(context)
    val candidateFontFamily = AppFonts.candidateFontFamily
    val commentFontFamily = AppFonts.commentFontFamily

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val itemPaddingPx = with(density) { 8.dp.toPx() }
    val spacingPx = with(density) { 4.dp.toPx() }

    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val rowPaddingPx = with(density) { 16.dp.toPx() }
    val rightSidePx = with(density) {
        val moreBtn = if (callbacks.onShowMoreCandidates != null) 38.dp.toPx() else 0f
        val clearBtn = if (callbacks.onClearAssociation != null) 38.dp.toPx() else 0f
        val hideBtn = if (callbacks.onHideKeyboard != null) 28.dp.toPx() else 0f
        rowPaddingPx + maxOf(moreBtn, clearBtn) + hideBtn + 8.dp.toPx()
    }

    // 候选行滚动状态：需在 state 分支前声明——ChineseCandidates 的 hasAnyMore
    // 叠加 canScrollForward 判断（见分支内注释）
    val candidateListState = rememberLazyListState()

    val displayCandidates: List<String>
    val displayAssociation: List<String>
    val displayComments: List<String>
    val hasAnyMore: Boolean
    val showInputTextRow: Boolean
    val showLeftIcon: Boolean

    when (val s = state) {
        is CandidateBarState.Idle -> {
            displayCandidates = emptyList()
            displayAssociation = emptyList()
            displayComments = emptyList()
            hasAnyMore = false
            showLeftIcon = true
        }
        is CandidateBarState.ChineseCandidates -> {
            val taken = s.candidates.take(20)
            // 候选栏按设置的"每页候选词数"显示引擎当前页，可左右滑动查看放不下的候选
            displayCandidates = taken
            displayComments = s.comments

            hasAnyMore = s.hasMore || candidateListState.canScrollForward
            showLeftIcon = false
            displayAssociation = remember(s.associationCandidates, taken, s.inputText, textMeasurer) {
                if (taken.isEmpty()) {
                    s.associationCandidates.take(PredictionManager.MAX_ASSOCIATION_COUNT)
                } else {
                    val measureText = { text: String ->
                        textMeasurer.measure(
                            text = AnnotatedString(text),
                            style = TextStyle(fontSize = candidateTextSize.sp)
                        ).size.width.toFloat()
                    }
                    val leftPx = with(density) { rowPaddingPx + 32.dp.toPx() }
                    val rowWidthPx = screenWidthPx - leftPx - rightSidePx
                    val regularWidthPx = taken.sumOf { c ->
                        measureText(c).toDouble() + itemPaddingPx
                    }.toFloat()
                    val dividerWidthPx = with(density) { 9.dp.toPx() }
                    val availablePx = rowWidthPx - regularWidthPx - dividerWidthPx

                    var used = 0f
                    val result = mutableListOf<String>()
                    for (c in s.associationCandidates) {
                        val w =
                            measureText(c) + itemPaddingPx + (if (result.isEmpty()) 0f else spacingPx)
                        if (used + w <= availablePx) {
                            used += w
                            result.add(c)
                        } else break
                    }
                    result
                }
            }
        }
        is CandidateBarState.AssociationOnly -> {
            displayCandidates = emptyList()
            displayAssociation = s.candidates.take(PredictionManager.MAX_ASSOCIATION_COUNT)
            hasAnyMore = s.hasMore
            showLeftIcon = false
            displayComments = s.comments
        }
        is CandidateBarState.EnglishCandidates -> {
            displayCandidates = s.candidates.take(20)
            displayComments = s.comments
            displayAssociation = emptyList()
            hasAnyMore = false
            showLeftIcon = false
        }
        is CandidateBarState.ClipboardDisplay -> {
            displayCandidates = s.candidates.take(20)
            displayComments = emptyList()
            displayAssociation = emptyList()
            hasAnyMore = false
            showLeftIcon = true
        }
        is CandidateBarState.Calculator -> {
            displayCandidates = s.candidates.take(20)
            displayComments = s.comments
            displayAssociation = emptyList()
            hasAnyMore = false
            showLeftIcon = false
        }
    }
    showInputTextRow = when (page) {
        is KeyboardPage.Overlay -> page.route !is OverlayRoute.Clipboard
        else -> true
    }

    LaunchedEffect(displayCandidates) {
        candidateListState.scrollToItem(0)
    }

    // 编码气泡文本：候选栏内计算后供 PreeditBubbleBar 渲染（位于候选栏之上，真实占位）。
    // 初始值取自当前 state 保证首帧即显示。
    // 小鹤双拼方案下，气泡内直接显示「先声母后韵母」分解（如 vc → zh + ao），
    // 输入内容显示在候选栏内、键盘整体不动（类似雾凇拼音）。
    val shuangpinHint = LocalShuangpinKeyHint.current
    var preeditBubbleText by remember(state, shuangpinHint.active) {
        val cs = state as? CandidateBarState.ChineseCandidates
        val rawInput = cs?.inputText ?: ""
        val text = if (shuangpinHint.active && rawInput.isNotEmpty()) {
            shuangpinHint.scheme?.decompose(rawInput)?.joinToString("　") ?: rawInput
        } else {
            cs?.preeditText ?: rawInput
        }
        mutableStateOf(text)
    }
    val showPreeditBubble = showInputTextRow && preeditBubbleText.isNotEmpty() && !showInputBoxStyle

    // 气泡不渲染时清掉服务层缓存的气泡矩形；不清会在键盘上方留一块"看不见但可点"的
    // 幽灵触摸热点（上屏/清空后还能吃掉一次点击）。空矩形 = 隐藏。
    LaunchedEffect(showPreeditBubble) {
        if (!showPreeditBubble) {
            onPreeditBubbleBounds?.invoke(0, 0, 0, 0)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 拼音编辑气泡：悬浮绘制，不占用候选栏 44dp 内部空间（仅编辑态略放大，
        // 靠 scale 实现，不影响其他元素布局）。
        //
        // 能点得到的关键不在气泡自身，而在服务层：容器向上多出了 PreeditBubbleMetrics
        // 那块 "悬浮层" 高度（contentTopInsets 已扣除，应用可视区不变），气泡落在容器
        // View bounds 之内 → Android 命中测试才会派发触摸（clipChildren 只管绘制）。
        if (showPreeditBubble) {
            val cs = state as? CandidateBarState.ChineseCandidates
            val isEditing = cs?.isEditingPinyin == true
            val caretInInput = cs?.caretPosition ?: -1
            val rawInput = cs?.inputText ?: ""
            // 编辑态显示真实输入编码（preedit 带回显分隔符），保证点击位置到 input
            // 下标的映射准确；非编辑态仍按双拼提示决定展示内容。
            val editText = cs?.preeditText?.ifEmpty { rawInput } ?: ""
            val barText = if (isEditing && editText.isNotEmpty()) editText else preeditBubbleText

            PreeditBubble(
                state = state,
                visuals = visuals,
                callbacks = callbacks,
                preeditBubbleText = preeditBubbleText,
                floatingPlacement = true,
                onBoundsChanged = onPreeditBubbleBounds
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(visuals.backgroundColor)
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.Center
        ) {
        if (isVoiceSticky) {
            // 常驻语音模式：候选栏显示语音引擎名 + 频谱
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (voicePluginName.isNotEmpty()) {
                    Text(
                        text = voicePluginName,
                        color = visuals.textColor.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AudioSpectrumAnimation(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 2.dp),
                        isActive = voiceRecognitionState == RecognitionState.LISTENING ||
                            voiceRecognitionState == RecognitionState.PROCESSING,
                        amplitude = voiceAmplitude,
                        spectrum = voiceSpectrum,
                        barWidthFactor = 4f,
                        barCount = 16,
                        spacingRatio = 1.6f,
                        heightScale = 0.6f
                    )
                }
            }
            return@Column
        }

        val cs = state as? CandidateBarState.ChineseCandidates
        val rawInput = cs?.inputText ?: ""
        val text = if (shuangpinHint.active && rawInput.isNotEmpty()) {
            shuangpinHint.scheme?.decompose(rawInput)?.joinToString("　") ?: rawInput
        } else {
            cs?.preeditText?.ifEmpty { rawInput } ?: rawInput
        }
        preeditBubbleText = text

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showLeftIcon) {
                when (state) {
                    is CandidateBarState.Idle -> {
                        if (page is KeyboardPage.Overlay && page.route is OverlayRoute.SchemaList && callbacks.onBack != null) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(iconButtonContainer)
                                    .clickable { callbacks.onBack() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "返回菜单",
                                    tint = visuals.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(iconButtonContainer)
                                    .clickable { callbacks.onLogoClick?.invoke() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = if (visuals.isDarkTheme) R.drawable.logo_dark else R.drawable.logo),
                                    contentDescription = "曦码 Logo",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    is CandidateBarState.ClipboardDisplay -> {
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "剪切板",
                                tint = visuals.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }

            if (inlineSuggestions.isNotEmpty()) {
                LazyRow(
                    // 占满配额：内容少时建议靠左、右侧留白到收起按钮（收起按钮
                    // 因此固定最右）；内容超出配额时占满并可横向滑动查看后续建议；
                    // clipToBounds：滑动时滑出边界的建议裁剪掉，避免与左侧 logo 重叠
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds(),
                ) {
                    itemsIndexed(inlineSuggestions, key = { index, _ -> index }) { _, suggestion ->
                        Box(modifier = Modifier.fillMaxHeight()) {
                            InlineSuggestionView(
                                suggestion = suggestion,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(180.dp),
                            )
                            // 每条尾部 1dp 分隔线：条目之间为间隔，最后一条的尾线
                            // 同时充当与候选词区的分界（与旧平铺布局视觉一致）
                            InlineSuggestionDivider(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                color = visuals.dividerColor,
                            )
                        }
                    }
                }
            }

            LazyRow(
                modifier = if (state is CandidateBarState.Idle && smsCode == null) Modifier else Modifier.weight(1f),
                state = candidateListState,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 短信验证码：作为候选栏内最优先项显示（在打字候选前），点击直接插入上屏
                if (smsCode != null) {
                    val smsCodeText = smsCode
                    item(key = "sms-code") {
                        SmsCodeCandidateItem(
                            code = smsCodeText,
                            textColor = visuals.textColor,
                            accentColor = visuals.accentColor,
                            onClick = onSmsCodeClick,
                        )
                    }
                    if (displayCandidates.isNotEmpty() || displayAssociation.isNotEmpty()) {
                        item(key = "sms-divider") {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(visuals.dividerColor.copy(alpha = 0.5f))
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                if (state is CandidateBarState.ClipboardDisplay && state.items.isNotEmpty()) {
                    itemsIndexed(state.items.take(20), key = { _, item -> item.id }) { index, item ->
                        if (item.isImage) {
                            ClipboardImageChip(
                                imagePath = item.imagePath,
                                onClick = { callbacks.onCandidateSelect(index) },
                                accentColor = visuals.accentColor
                            )
                        } else {
                            CandidateItem(
                                text = item.text,
                                index = index,
                                onClick = { callbacks.onCandidateSelect(index) },
                                onLongClick = if (callbacks.onCandidateLongPress != null) {
                                    { callbacks.onCandidateLongPress(index) }
                                } else null,
                                textColor = visuals.textColor,
                                comment = "",
                                isSelected = index == 0,
                                accentColor = visuals.accentColor,
                                selectedTextColor = visuals.selectedTextColor,
                                fontSize = candidateTextSize.sp,
                                candidateFontFamily = candidateFontFamily,
                                commentFontFamily = commentFontFamily
                            )
                        }
                    }
                } else {
                    itemsIndexed(displayCandidates, key = { index, _ -> index }) { index, candidate ->
                        CandidateItem(
                            text = candidate,
                            index = index,
                            onClick = { callbacks.onCandidateSelect(index) },
                            onLongClick = if (callbacks.onCandidateLongPress != null) {
                                { callbacks.onCandidateLongPress(index) }
                            } else null,
                            textColor = visuals.textColor,
                            comment = if (showComments) {
                                when (val s = state) {
                                    is CandidateBarState.ChineseCandidates -> s.comments.getOrElse(index) { "" }
                                    is CandidateBarState.EnglishCandidates -> s.comments.getOrElse(index) { "" }
                                    else -> ""
                                }
                            } else "",
                            isSelected = index == 0,
                            accentColor = visuals.accentColor,
                            selectedTextColor = visuals.selectedTextColor,
                            fontSize = candidateTextSize.sp,
                            candidateFontFamily = candidateFontFamily,
                            commentFontFamily = commentFontFamily
                        )
                    }
                }

                // 仅当左侧存在打字候选时才需要分隔线；纯联想态（无打字候选）下
                // 该竖线会孤悬列表最左缘，属多余元素。
                // 注意：分隔线在条件内，联想词 items 必须在条件外——纯联想态
                // displayCandidates 为空，若一并包进条件会导致联想词整个不渲染。
                if (displayCandidates.isNotEmpty() && displayAssociation.isNotEmpty()) {
                    item(key = "divider") {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(visuals.dividerColor.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp)
                        )
                    }
                }

                itemsIndexed(displayAssociation, key = { index, _ -> "assoc-$index" }) { index, candidate ->
                    val assocState = state as? CandidateBarState.AssociationOnly
                    CandidateItem(
                        text = candidate,
                        index = -1,
                        onClick = { callbacks.onAssociationSelect?.invoke(index) },
                        textColor = visuals.textColor,
                        comment = displayComments.getOrElse(index) { "" },
                        isSelected = assocState?.highlightIndex == index,
                        accentColor = visuals.accentColor,
                        selectedTextColor = visuals.selectedTextColor,
                        fontSize = candidateTextSize.sp,
                        candidateFontFamily = candidateFontFamily,
                        commentFontFamily = commentFontFamily
                    )
                }


            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                state is CandidateBarState.Idle -> {
                    // 显示内联建议或有短信验证码时隐藏工具栏按钮区，把宽度让给验证码/建议；logo 与
                    // 收起按钮保留，退格回到 idle 时的状态感知不变
                    if (inlineSuggestions.isEmpty() && smsCode == null) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = true)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            if (toolbarActions.isNotEmpty()) {
                                toolbarActions.forEach { action ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 5.dp)
                                            .size(32.dp)
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = action.onClick
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ToolbarButtonIcon(
                                            item = action.item,
                                            tint = if (isPressed) iconButtonTint.copy(alpha = 0.6f) else iconButtonTint,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (callbacks.onHideKeyboard != null) {
                        val hideKeyboardInteractionSource = remember { MutableInteractionSource() }
                        val isHideKeyboardPressed by hideKeyboardInteractionSource.collectIsPressedAsState()

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    interactionSource = hideKeyboardInteractionSource,
                                    indication = null,
                                    onClick = { callbacks.onHideKeyboard() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "收起键盘",
                                tint = if (isHideKeyboardPressed) iconButtonTint.copy(alpha = 0.6f) else iconButtonTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                candidatePageExpanded -> {
                    if (callbacks.onBack != null) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(iconButtonContainer)
                                .clickable { callbacks.onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "返回键盘",
                                tint = visuals.accentColor,
                                    modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                displayAssociation.isNotEmpty() && callbacks.onClearAssociation != null -> {
                    val clearInteractionSource = remember { MutableInteractionSource() }
                    val isClearPressed by clearInteractionSource.collectIsPressedAsState()

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(visuals.dividerColor).padding(end = 1.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isClearPressed) (if (visuals.isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(
                                    alpha = 0.1f
                                ))
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = clearInteractionSource,
                                indication = null,
                                onClick = { callbacks.onClearAssociation() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "清空",
                            color = if (isClearPressed) visuals.textColor.copy(alpha = 0.6f) else visuals.textColor,
                            fontSize = 11.sp
                        )
                    }
                }
                hasAnyMore && callbacks.onShowMoreCandidates != null -> {
                    val moreInteractionSource = remember { MutableInteractionSource() }
                    val isMorePressed by moreInteractionSource.collectIsPressedAsState()

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isMorePressed) (if (visuals.isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(
                                    alpha = 0.1f
                                ))
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = moreInteractionSource,
                                indication = null,
                                onClick = { callbacks.onShowMoreCandidates() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "更多",
                            color = if (isMorePressed) visuals.textColor.copy(alpha = 0.6f) else visuals.textColor,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardImageChip(
    imagePath: String,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .width(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "剪贴板图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun CandidateItem(
    text: String,
    index: Int,
    onClick: () -> Unit,
    textColor: Color,
    comment: String = "",
    isSelected: Boolean = false,
    accentColor: Color = Color(0xFF1A73E8),
    selectedTextColor: Color = Color(0xFF1A73E8),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 19.sp,
    candidateFontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    commentFontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = if (isSelected) selectedTextColor else textColor,
            fontSize = fontSize,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            fontFamily = candidateFontFamily
        )
        if (comment.isNotEmpty()) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = comment,
                color = if (isSelected) selectedTextColor.copy(alpha = 0.6f) else textColor.copy(alpha = 0.5f),
                fontSize = (fontSize.value * 11f / 19f).sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                fontFamily = commentFontFamily
            )
        }
    }
}

/**
 * 候选栏内的短信验证码项：小号「验证码」标签 + 加粗验证码（强调色），
 * 点击回调插入上屏；非空点击回调时以浅强调色底提示可点。
 */
@Composable
fun SmsCodeCandidateItem(
    code: String,
    textColor: Color,
    accentColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (onClick != null) accentColor.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "验证码",
            color = textColor.copy(alpha = 0.55f),
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = code,
            color = accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * 悬浮拼音编辑气泡。
 *
 * - 视觉上悬浮于候选栏之上，不占用键盘内容布局（用 layout 谎报高 0 + 向上偏移绘制）；
 * - 编辑态整体略微放大（scale）并加粗描边，强调"正在编辑"，同样不影响其他元素布局；
 * - 点击热区覆盖整块气泡（含内边距与圆角边），坐标换算到文字局部坐标后映射字符下标。
 *
 * 可触摸的前提在服务层：容器已向上扩出 [PreeditBubbleMetrics.TOTAL_DP] 高度的悬浮层
 * （见 XimeInputMethodService.onComputeInsets），气泡落在容器 View bounds 内才能被命中。
 */
@Composable
private fun PreeditBubble(
    state: CandidateBarState,
    visuals: CandidateBarVisuals,
    callbacks: CandidateBarCallbacks,
    preeditBubbleText: String,
    floatingPlacement: Boolean,
    onBoundsChanged: ((Int, Int, Int, Int) -> Unit)?,
) {
    val cs = state as? CandidateBarState.ChineseCandidates
    val isEditing = cs?.isEditingPinyin == true
    val caretInInput = cs?.caretPosition ?: -1
    val rawInput = cs?.inputText ?: ""
    val shuangpinHint = LocalShuangpinKeyHint.current
    val isShuangpin = shuangpinHint.active
    // 编辑态下使用 PinyinFormatter 插入 '\'' 音节分隔符（如 shan'shui、vs'ld），
    // 视觉上音节清晰隔开不再紧挤，且通过纯字母数双向映射，绝不产生汉字跳动或错位；
    // 非编辑态则保留双拼助记/带分词符的展示。
    val editText = cs?.preeditText?.ifEmpty { rawInput } ?: ""
    val formattedEditPinyin = remember(rawInput, cs?.preeditText, isEditing, isShuangpin) {
        if (isEditing && rawInput.isNotEmpty()) {
            com.kingzcheung.xime.util.PinyinFormatter.formatPinyin(rawInput, cs.preeditText, isShuangpin)
        } else {
            rawInput
        }
    }
    val barText = if (isEditing) formattedEditPinyin else preeditBubbleText

    // 非编辑态且气泡展示的是双拼分解文本时，需要「显示下标 → 原始编码下标」的映射，
    // 否则点击只能落到末尾（分解文本如 vc → 「zh + ao」，与编码不同源）。
    // 只在实际展示串就是分解串时启用（不靠状态推断，避免与展示串错位）。
    val shuangpinIndexMap = remember(
        barText, editText, rawInput, isEditing, shuangpinHint.active, shuangpinHint.scheme
    ) {
        val scheme = shuangpinHint.scheme
        val decompText = if (shuangpinHint.active && scheme != null && rawInput.isNotEmpty()) {
            scheme.decompose(rawInput).joinToString("\u3000")
        } else {
            null
        }
        if (!isEditing && scheme != null && decompText != null && barText == decompText && decompText != editText) {
            ShuangpinSchemes.buildShuangpinBubbleIndexMap(rawInput, scheme.decompose(rawInput))
        } else {
            null
        }
    }

    // 编辑态放大：只改视觉缩放，不参与布局测量，因此不会挤出/推挤任何其他组件。
    // 用 animateFloatAsState 做过渡，避免状态切换时突兀跳变。
    val scale by animateFloatAsState(
        targetValue = if (isEditing) PreeditBubbleMetrics.EDIT_SCALE else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "preeditBubbleScale"
    )

    val placement = if (floatingPlacement) {
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val gapPx = PreeditBubbleMetrics.GAP_DP.dp.roundToPx()
            val marginPx = PreeditBubbleMetrics.HORIZONTAL_MARGIN_DP.dp.roundToPx()
            // 高度上报 0：完全不占键盘内容布局，靠负 y 向上绘制到悬浮层。
            layout(placeable.width, 0) {
                placeable.placeRelative(x = marginPx, y = -placeable.height - gapPx)
            }
        }
    } else {
        Modifier
    }

    PreeditBubbleBar(
        text = barText,
        rawInput = rawInput,
        caretPosInInput = caretInInput,
        isEditing = isEditing,
        accentColor = visuals.accentColor,
        textColor = visuals.textColor,
        backgroundColor = visuals.backgroundColor,
        onCharClick = { charIndex ->
            if (!isEditing) {
                // 非编辑态单击：直接进入编辑态并把光标落到点击处（不再需要先放大再点一次）。
                // 展示双拼分解文本时先用 [shuangpinIndexMap] 把显示下标换算为编码下标；
                // 否则用 preeditIndexToInputIndex 换算到 rawInput 字符下标。
                val map = shuangpinIndexMap
                val inputIndex = if (map != null && map.isNotEmpty()) {
                    val safeChar = charIndex.coerceIn(0, map.size - 1)
                    map[safeChar]
                } else {
                    ImeKeyRouter.preeditIndexToInputIndex(barText, rawInput, charIndex)
                }
                callbacks.onPinyinEditAt?.invoke(inputIndex)
            } else {
                // 编辑态下 barText 为带有 '\'' 的音节切分串，通过 PinyinFormatter 严格映射到 rawInput 下标
                val inputIndex = com.kingzcheung.xime.util.PinyinFormatter.formattedIndexToInputIndex(barText, charIndex)
                callbacks.onPinyinCaretMove?.invoke(inputIndex)
            }
        },
        modifier = placement.graphicsLayer {
            scaleX = scale
            scaleY = scale
            // 以左下角为缩放锚点：气泡贴着候选栏上缘生长，放大时不会往下压到候选栏。
            transformOrigin = TransformOrigin(0f, 1f)
        },
        onBoundsChanged = onBoundsChanged
    )
}

/**
 * 悬浮拼音编辑条：悬浮于候选栏顶部的可交互组件。
 *
 * - 普通打字态：轻巧胶囊药丸，点击任意位置即可进入编辑模式并在点击处放置光标；
 * - 编辑态：展开编辑模式，文字微大，呈现呼吸闪烁光标，并提供左右微调键与完成键；
 *   用户可直接点击键盘上的字母插入、按退格键原地删除、或点击候选词上屏退出。
 */
@Composable
fun PreeditBubbleBar(
    text: String,
    rawInput: String,
    caretPosInInput: Int,
    isEditing: Boolean,
    accentColor: Color,
    textColor: Color,
    backgroundColor: Color,
    onCharClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 气泡在窗口中的实时边界回调（left, top, right, bottom，px）。
     *  服务层用它构造 TOUCHABLE_INSETS_REGION 并集（气泡在容器顶边之上）。 */
    onBoundsChanged: ((Int, Int, Int, Int) -> Unit)? = null,
) {
    if (text.isEmpty()) return

    val isDarkTheme = textColor.luminance() > 0.5f
    val bubbleBaseColor = if (backgroundColor.alpha > 0.01f) {
        backgroundColor
    } else {
        if (isDarkTheme) Color(0xFF2D2F31) else Color(0xFFFAFAFA)
    }
    // 背景必须【不透明】：气泡悬浮在键盘之上、下方是宿主 App 的界面，
    // 半透明会让 App 的背景（如白色输入框）透出来，看起来像“气泡里还有一层浅色方块”。
    val bubbleBgColor = bubbleBaseColor.copy(alpha = 1f)

    // 光标呼吸动画（530ms 闪烁）
    val transition = rememberInfiniteTransition(label = "pinyinCursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    val preeditCursorIndex = remember(text, caretPosInInput, rawInput, isEditing) {
        if (caretPosInInput >= 0) {
            if (isEditing) {
                com.kingzcheung.xime.util.PinyinFormatter.inputIndexToFormattedIndex(text, caretPosInInput)
            } else {
                ImeKeyRouter.inputIndexToPreeditIndex(text, caretPosInInput)
            }
        } else {
            text.length
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 气泡固定高度（固定后容器高度可确定性推导）；边界回传给服务层做触摸区域上报。
    Surface(
        modifier = modifier
            .height(PreeditBubbleMetrics.HEIGHT_DP.dp)
            .onGloballyPositioned { coords ->
                if (onBoundsChanged != null) {
                    val pos = coords.positionInWindow()
                    val size = coords.size
                    onBoundsChanged(
                        pos.x.roundToInt(),
                        pos.y.roundToInt(),
                        (pos.x + size.width).roundToInt(),
                        (pos.y + size.height).roundToInt()
                    )
                }
            }
            .shadow(
                elevation = if (isEditing) 4.dp else 1.dp,
                shape = RoundedCornerShape(6.dp)
            ),
        shape = RoundedCornerShape(6.dp),
        color = bubbleBgColor,
        // 边框用主题强调色：气泡悬浮在键盘/宿主界面上，淡色描边能清晰勾出轮廓，
        // 与内部不透明填充不冲突（不是半透明玻璃下的双背景问题）。
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        // 拼音文字区铺满整块气泡（取消原有的 hPad/vPad 内边距）：
        // 内边距取消了，气泡自然缩窄，拼音区更大。
        // 但文字仍不能紧贴边框，故在文字层左右各留 6dp（编辑态 8dp）。
        // 点击命中区覆盖整块气泡，坐标换算仍减去该文字内边距以映射到正确字符下标。
        val textHPad = if (isEditing) 8.dp else 6.dp
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .pointerInput(text, rawInput, isEditing) {
                    detectTapGestures { offset ->
                        val local = Offset(offset.x - textHPad.toPx(), offset.y)
                        textLayoutResult?.let { layout ->
                            val clickedOffset = layout.getOffsetForPosition(local)
                            onCharClick(clickedOffset)
                        } ?: run {
                            onCharClick(text.length)
                        }
                    }
                }
                // 内边距只留左右一点点（上下为 0：利用气泡全高显示拼音）
                .padding(horizontal = textHPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拼音文字区：整串一次性渲染。
            //
            // 不自拆为「光标前/光标/光标后」三段：三段渲染下 textLayoutResult 只覆盖
            // 其中一段，getOffsetForPosition 把点击位置映射到错误的下标，导致"光标
            // 之后的字符点不动"。整串渲染后任意位置都能直接点击定位。
            // 光标改为按整串 layout 的水平坐标叠加绘制，不影响命中映射。
            Box(
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    text = text,
                    color = textColor.copy(alpha = if (isEditing) 0.92f else 0.9f),
                    // 字号放大（原 13/12sp）：气泡内边距取消后空间更大，拼音更醒目
                    fontSize = if (isEditing) 16.sp else 15.sp,
                    fontWeight = if (isEditing) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart),
                    onTextLayout = { textLayoutResult = it }
                )
                if (isEditing) {
                    val caretIndex = preeditCursorIndex.coerceIn(0, text.length)
                    // layout 可能滞后一帧（text 已变而 onTextLayout 未回）：
                    // 仅在布局文本与当前文本一致时取水平坐标，未完成时保持上一次有效坐标，避免闪跳到 0f。
                    val layout = textLayoutResult?.takeIf { it.layoutInput.text.length == text.length }
                    val targetCaretX = layout?.getHorizontalPosition(caretIndex, usePrimaryDirection = true)
                    var lastValidCaretX by remember { mutableStateOf(0f) }
                    if (targetCaretX != null) {
                        lastValidCaretX = targetCaretX
                    }
                    val caretX = targetCaretX ?: lastValidCaretX
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset(caretX.roundToInt(), 0) }
                            .width(2.dp)
                            // 光标高度随字号同步放大（原 14dp）
                            .height(18.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(accentColor.copy(alpha = cursorAlpha))
                    )
                }
            }

        }
    }
}

