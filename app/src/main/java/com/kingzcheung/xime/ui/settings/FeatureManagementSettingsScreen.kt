package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.AutoFixHigh
import androidx.compose.material.icons.twotone.Calculate
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SchemaSwitch
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 功能管理页面。
 *
 * 分两部分：
 * 1. Xime 自身实现的扩展功能（数字金额大写、英文拼写纠错），与输入方案无关；
 * 2. 当前输入方案自带的开关（rime 原生 switches），随方案切换而变——万象等方案以
 *    switches 声明超级提示、字符集、简繁转换等功能，由方案自身的 lua 读取。
 *    这里按方案声明动态生成控件：两态开关渲染为 Switch，多选一渲染为选项标签。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureManagementSettingsContent(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("功能管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "开关调整后即时生效，无需重新部署。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                SettingsSection(title = "输入与转换", content = {
                    var numberTranslatorEnabled by remember {
                        mutableStateOf(SettingsPreferences.isNumberTranslatorEnabled(context))
                    }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.Calculate,
                        title = "数字与金额大写",
                        subtitle = "输入 v 或 R 加数字（如 v123.45）转换大写金额与数字",
                        checked = numberTranslatorEnabled,
                        showArrow = false,
                        onCheckedChange = { enabled ->
                            numberTranslatorEnabled = enabled
                            SettingsPreferences.setNumberTranslatorEnabled(context, enabled)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var spellCheckEnabled by remember {
                        mutableStateOf(SettingsPreferences.isSpellCheckEnabled(context))
                    }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.AutoFixHigh,
                        title = "英文拼写纠错",
                        subtitle = "英文输入拼错时提示正确拼写（如输入 helo 提示 hello）",
                        checked = spellCheckEnabled,
                        showArrow = false,
                        onCheckedChange = { enabled ->
                            spellCheckEnabled = enabled
                            SettingsPreferences.setSpellCheckEnabled(context, enabled)
                        }
                    )
                })
            }

            // 当前方案自带的开关（rime switches）：随方案切换而变，动态生成
            item {
                val schemaSwitches = remember {
                    runCatching { SchemaManager.getCurrentSchemaSwitches(context) }
                        .getOrDefault(emptyList())
                        .filterNot { SchemaManager.isHiddenSchemaSwitch(it) }
                }
                if (schemaSwitches.isNotEmpty()) {
                    SettingsSection(title = "方案功能开关", content = {
                        var refresh by remember { mutableIntStateOf(0) }
                        key(refresh) {
                            Column {
                                schemaSwitches.forEachIndexed { index, sw ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 56.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    if (sw.name.isNotEmpty()) {
                                        SettingsToggleItem(
                                            icon = Icons.TwoTone.Tune,
                                            title = schemaSwitchTitle(sw),
                                            subtitle = schemaSwitchSubtitle(sw),
                                            checked = SettingsPreferences.getSchemaOptionEnabled(sw.name),
                                            showArrow = false,
                                            onCheckedChange = { enabled ->
                                                SettingsPreferences.setSchemaOptionEnabled(sw.name, enabled)
                                                refresh++
                                            }
                                        )
                                    } else {
                                        SchemaOptionGroupRow(
                                            sw = sw,
                                            onSelected = { option ->
                                                SettingsPreferences.setSchemaOptionGroup(sw.options, option)
                                                refresh++
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    })
                }
            }
        }
    }
}

/**
 * 多选一开关组（方案 switches 的 `options` 形态，如简繁转换 简体/通繁/港繁/台繁）。
 * 选项以标签形式平铺，点击即切换；当前项高亮。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchemaOptionGroupRow(
    sw: SchemaSwitch,
    onSelected: (String) -> Unit
) {
    val current = remember {
        val active = sw.options.indexOfFirst { SettingsPreferences.getSchemaOptionEnabled(it) }
        if (active >= 0) active else if (sw.reset in sw.options.indices) sw.reset else 0
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = schemaSwitchTitle(sw),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = schemaSwitchSubtitle(sw),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sw.states.forEachIndexed { i, label ->
                val option = sw.options.getOrNull(i)
                FilterChip(
                    selected = i == current,
                    onClick = { if (option != null) onSelected(option) },
                    label = { Text(label) }
                )
            }
        }
    }
}

/** 方案开关的界面标题：优先用内置中文名，其次退回开关名。 */
private fun schemaSwitchTitle(sw: SchemaSwitch): String {
    SCHEMA_SWITCH_LABELS[sw.name]?.first?.let { return it }
    SCHEMA_OPTION_GROUP_LABELS[sw.options.firstOrNull()]?.first?.let { return it }
    if (sw.name.isNotEmpty()) return sw.name
    return sw.states.firstOrNull() ?: "方案开关"
}

/** 方案开关的说明文字：优先用内置说明，其次退回状态列表。 */
private fun schemaSwitchSubtitle(sw: SchemaSwitch): String {
    SCHEMA_SWITCH_LABELS[sw.name]?.second?.let { return it }
    SCHEMA_OPTION_GROUP_LABELS[sw.options.firstOrNull()]?.second?.let { return it }
    return sw.states.joinToString(" / ")
}

/** 已知方案开关（name 型）的中文标题与说明。未收录的开关按名称兜底展示。 */
private val SCHEMA_SWITCH_LABELS: Map<String, Pair<String, String>> = mapOf(
    "ascii_punct" to ("中英标点" to "中文输入状态下输出英文标点符号"),
    "full_shape" to ("全角字符" to "输出全角字符与标点"),
    "emoji" to ("候选表情" to "候选词后附带 emoji 提示"),
    "chinese_english" to ("中英翻译" to "候选词后显示英文翻译"),
    "context_reorder" to ("上下文调频" to "根据已上屏内容自动调整候选顺序"),
    "abbrev" to ("简码前置" to "按简码把常用词前置到候选"),
    "super_tips" to ("超级提示" to "编码后提示表情、翻译、符号等对应关系"),
    "charset_filter" to ("字符集范围" to "大字集含 CJK 扩展字，小字集限 8105 通规字"),
    "char_priority" to ("辅码查词排序" to "反查与辅码查词时词组优先或单字优先"),
    "english" to ("英文候选" to "输出英文单词候选")
)

/** 已知/可能出现的开关组（options 型）的中文标题与说明，按组内首个选项名索引。 */
private val SCHEMA_OPTION_GROUP_LABELS: Map<String, Pair<String, String>> = mapOf(
    "raw_input" to ("编码显示" to "输入框内编码的显示形式"),
    "s2s" to ("简繁转换" to "输出简体、通繁、港繁或台繁"),
    "comment_off" to ("候选注释" to "候选注释显示带声调或不带声调全拼")
)
