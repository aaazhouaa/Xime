package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.service.PredictionManager
import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PreeditBubbleMetrics
import com.kingzcheung.xime.keyboard.PanelType
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.shuangpin.LocalShuangpinKeyHint
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

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 拼音编辑气泡：在候选栏之上真实占位（不再用 0 高度绘制到容器顶边之外）。
        // IME 仅把 contentTopInsets 以下区域上报为可触摸（TOUCHABLE_INSETS_VISIBLE），
        // 画在容器外的气泡落在可触摸区之外，触摸会被系统判给背后 App → 点了没反应。
        // 占位高度由 PreeditBubbleMetrics 定义，服务层同步把该高度计入容器总高。
        if (showPreeditBubble) {
            val cs = state as? CandidateBarState.ChineseCandidates
            val isEditing = cs?.isEditingPinyin == true
            val caretInInput = cs?.caretPosition ?: -1
            val rawInput = cs?.inputText ?: ""
            // 编辑态显示真实输入编码（preedit 带回显分隔符），保证点击位置到 input
            // 下标的映射准确；非编辑态仍按双拼提示决定展示内容。
            val editText = cs?.preeditText?.ifEmpty { rawInput } ?: ""
            val barText = if (isEditing && editText.isNotEmpty()) editText else preeditBubbleText

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
                        // 非编辑态：气泡可能展示的是双拼分解文本（与原始编码下标不同源），
                        // 此时不做下标映射，仅进入编辑态（光标置末尾）；
                        // 进入编辑态后气泡改显带分隔符的原始编码，再点击即可精确定位。
                        callbacks.onPinyinEditingToggle?.invoke(true)
                    } else {
                        callbacks.onPinyinCaretMove?.invoke(charIndex)
                    }
                },
                onMoveLeft = {
                    val cur = if (caretInInput >= 0) caretInInput else rawInput.length
                    val next = (cur - 1).coerceAtLeast(0)
                    val nextCharIdx = ImeKeyRouter.inputIndexToPreeditIndex(barText, next)
                    callbacks.onPinyinCaretMove?.invoke(nextCharIdx)
                },
                onMoveRight = {
                    val cur = if (caretInInput >= 0) caretInInput else rawInput.length
                    val next = (cur + 1).coerceAtMost(rawInput.length)
                    val nextCharIdx = ImeKeyRouter.inputIndexToPreeditIndex(barText, next)
                    callbacks.onPinyinCaretMove?.invoke(nextCharIdx)
                },
                onCloseEditing = {
                    callbacks.onPinyinEditingToggle?.invoke(false)
                },
                modifier = Modifier.padding(
                    start = PreeditBubbleMetrics.HORIZONTAL_MARGIN_DP.dp,
                    bottom = PreeditBubbleMetrics.GAP_DP.dp
                )
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

        val displayText = (state as? CandidateBarState.ChineseCandidates)?.preeditText
            ?: (state as? CandidateBarState.ChineseCandidates)?.inputText ?: ""
        // 编码显示已改为候选栏上方的悬浮气泡（PreeditBubbleBar，见上方渲染），
        // 栏内不再为编码保留布局空间——打字态与联想态的候选行共用同一垂直位置。
        preeditBubbleText = displayText

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
                modifier = if (state is CandidateBarState.Idle) Modifier else Modifier.weight(1f),
                state = candidateListState,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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

                // 短信验证码：作为候选栏内的一项显示（分割线分隔，键盘整体不动）
                if (smsCode != null) {
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
                    val smsCodeText = smsCode
                    item(key = "sms-code") {
                        SmsCodeCandidateItem(
                            code = smsCodeText,
                            textColor = visuals.textColor,
                            accentColor = visuals.accentColor,
                            onClick = onSmsCodeClick,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                state is CandidateBarState.Idle -> {
                    // 显示内联建议时隐藏工具栏按钮区，把宽度让给建议；logo 与
                    // 收起按钮保留，退格回到 idle 时的状态感知不变
                    if (inlineSuggestions.isEmpty()) {
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
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onCloseEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return

    val isDarkTheme = textColor.luminance() > 0.5f
    val bubbleBaseColor = if (backgroundColor.alpha > 0.01f) {
        backgroundColor
    } else {
        if (isDarkTheme) Color(0xFF2D2F31) else Color(0xFFFAFAFA)
    }
    val bubbleBgColor = if (isEditing) bubbleBaseColor.copy(alpha = 0.95f) else bubbleBaseColor.copy(alpha = 0.72f)

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

    val preeditCursorIndex = remember(text, caretPosInInput, rawInput) {
        if (caretPosInInput >= 0) {
            ImeKeyRouter.inputIndexToPreeditIndex(text, caretPosInInput)
        } else {
            text.length
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 气泡在布局中占用固定高度：高度固定后容器总高可确定性计算（PreeditBubbleMetrics），
    // 保证气泡始终完整落在 IME 可触摸区内（而非随文字宽度/行高浮动）。
    Surface(
        // 气泡必须真实占据布局高度：IME 只把 contentTopInsets 以下的区域上报为可触摸
        // （TOUCHABLE_INSETS_VISIBLE），若沿用旧的 layout(宽, 0) + placeRelative(y=负值)
        // 把气泡画到容器顶边之外，该区域不属于 IME 可触摸区，触摸会被系统判给背后 App，
        // 导致气泡点了没反应。占位后容器顶边上移，气泡落入可触摸区。
        modifier = modifier
            .height(PreeditBubbleMetrics.HEIGHT_DP.dp)
            .shadow(
                elevation = if (isEditing) 4.dp else 1.dp,
                shape = RoundedCornerShape(6.dp)
            ),
        shape = RoundedCornerShape(6.dp),
        color = bubbleBgColor,
        border = BorderStroke(
            width = if (isEditing) 1.dp else 0.5.dp,
            color = if (isEditing) accentColor.copy(alpha = 0.7f) else textColor.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(
                    horizontal = if (isEditing) 8.dp else 6.dp,
                    vertical = if (isEditing) 4.dp else 2.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拼音文字区（点击任意位置定位光标）
            Box(
                modifier = Modifier
                    .pointerInput(text, rawInput, isEditing) {
                        detectTapGestures { offset ->
                            textLayoutResult?.let { layout ->
                                val clickedOffset = layout.getOffsetForPosition(offset)
                                onCharClick(clickedOffset)
                            } ?: run {
                                onCharClick(text.length)
                            }
                        }
                    }
                    .padding(vertical = 1.dp)
            ) {
                if (isEditing) {
                    val clampedCursor = preeditCursorIndex.coerceIn(0, text.length)
                    val before = text.substring(0, clampedCursor)
                    val after = text.substring(clampedCursor)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (before.isNotEmpty()) {
                            Text(
                                text = before,
                                color = textColor.copy(alpha = 0.95f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                onTextLayout = { textLayoutResult = it }
                            )
                        }
                        // 竖线光标
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(accentColor.copy(alpha = cursorAlpha))
                        )
                        if (after.isNotEmpty()) {
                            Text(
                                text = after,
                                color = textColor.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                onTextLayout = { if (before.isEmpty()) textLayoutResult = it }
                            )
                        }
                    }
                } else {
                    Text(
                        text = text,
                        color = textColor.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        onTextLayout = { textLayoutResult = it }
                    )
                }
            }

            // 编辑模式下的操作区：微调与关闭
            if (isEditing) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(textColor.copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.width(4.dp))

                // 左移光标
                Box(
                    modifier = Modifier
                        .size(PreeditBubbleMetrics.EDIT_BUTTON_SIZE_DP.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onMoveLeft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "光标左移",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 右移光标
                Box(
                    modifier = Modifier
                        .size(PreeditBubbleMetrics.EDIT_BUTTON_SIZE_DP.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onMoveRight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "光标右移",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 完成按钮
                Box(
                    modifier = Modifier
                        .size(PreeditBubbleMetrics.EDIT_BUTTON_SIZE_DP.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onCloseEditing),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "完成编辑",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

