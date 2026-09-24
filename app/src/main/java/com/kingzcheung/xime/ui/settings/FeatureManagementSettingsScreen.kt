package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ShortText
import androidx.compose.material.icons.automirrored.twotone.TextSnippet
import androidx.compose.material.icons.twotone.Calculate
import androidx.compose.material.icons.twotone.LowPriority
import androidx.compose.material.icons.twotone.Spellcheck
import androidx.compose.material.icons.twotone.Today
import androidx.compose.material.icons.twotone.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 拼音方案扩展功能管理页面。
 * 提供日期时间、金额大写、置顶、长词优先、英文混输降权等独立开关。
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "针对拼音输入方案提供的扩展功能。开关调整后即时生效，无需重新部署。",
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
                    var dateTranslatorEnabled by remember {
                        mutableStateOf(SettingsPreferences.isDateTranslatorEnabled(context))
                    }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.Today,
                        title = "日期时间输入",
                        subtitle = "输入 rq、sj、xq 等快捷输入当前日期、时间和星期",
                        checked = dateTranslatorEnabled,
                        showArrow = false,
                        onCheckedChange = { enabled ->
                            dateTranslatorEnabled = enabled
                            SettingsPreferences.setDateTranslatorEnabled(context, enabled)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var customPhraseEnabled by remember {
                        mutableStateOf(SettingsPreferences.isCustomPhraseEnabled(context))
                    }
                    SettingsToggleItem(
                        icon = Icons.AutoMirrored.TwoTone.TextSnippet,
                        title = "自定义短语",
                        subtitle = "在候选栏展示 custom_phrase 中的快捷自定义文本",
                        checked = customPhraseEnabled,
                        showArrow = false,
                        onCheckedChange = { enabled ->
                            customPhraseEnabled = enabled
                            SettingsPreferences.setCustomPhraseEnabled(context, enabled)
                        }
                    )
                })
            }

            item {
                SettingsSection(title = "候选词排序与提示", content = {
                    var pinCandEnabled by remember {
                        mutableStateOf(SettingsPreferences.isPinCandFilterEnabled(context))
                    }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.VerticalAlignTop,
                        title = "候选项置顶",
                        subtitle = "输入高频编码时置顶常用字词（如输入 d 首选“的”）",
                        checked = pinCandEnabled,
                        showArrow = false,
                        onCheckedChange = { enabled ->
                            pinCandEnabled = enabled
                            SettingsPreferences.setPinCandFilterEnabled(context, enabled)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var longWordFilterEnabled by remember {
                        mutableStateOf(SettingsPreferences.isLongWordFilterEnabled(context))
                    }
                    SettingsToggleItem(
                        icon = Icons.AutoMirrored.TwoTone.ShortText,
                        title = "长词优先展示",
                        subtitle = "提升简拼与短拼音匹配到的长词排位（如西安、饥饿）",
                        checked = longWordFilterEnabled,
                        showArrow = false,
                        onCheckedChange = { enabled ->
                            longWordFilterEnabled = enabled
                            SettingsPreferences.setLongWordFilterEnabled(context, enabled)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var reduceEnglishEnabled by remember {
                        mutableStateOf(SettingsPreferences.isReduceEnglishFilterEnabled(context))
                    }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.LowPriority,
                        title = "英文混输降权",
                        subtitle = "降低易冲突的英文短词（如 rug、bid、cat）的候选排位",
                        checked = reduceEnglishEnabled,
                        showArrow = false,
                        onCheckedChange = { enabled ->
                            reduceEnglishEnabled = enabled
                            SettingsPreferences.setReduceEnglishFilterEnabled(context, enabled)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var correctorEnabled by remember {
                        mutableStateOf(SettingsPreferences.isCorrectorEnabled(context))
                    }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.Spellcheck,
                        title = "错音错字提示",
                        subtitle = "在候选栏提示易读错易写错词汇的正确注音（如给予、按捺）",
                        checked = correctorEnabled,
                        showArrow = false,
                        onCheckedChange = { enabled ->
                            correctorEnabled = enabled
                            SettingsPreferences.setCorrectorEnabled(context, enabled)
                        }
                    )
                })
            }
        }
    }
}
