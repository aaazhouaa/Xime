package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.AutoFixHigh
import androidx.compose.material.icons.twotone.Calculate
import androidx.compose.material.icons.twotone.CloudDownload
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.kingzcheung.xime.settings.GrammarModelManager
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SchemaSwitch
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.PermissionHelper
import kotlinx.coroutines.launch

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

            // 万象语法模型（.gram）：可选安装，未安装不影响基本输入
            item {
                GrammarModelSection()
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

/**
 * 万象语法模型（.gram）安装区。
 *
 * 模型约 400MB，不随 app 发布，由用户按需下载；未安装时语法加权静默跳过，
 * 输入方案照常可用。安装或删除后需重启输入法进程，引擎才会重新加载/卸载模型。
 */
@Composable
private fun GrammarModelSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(GrammarModelManager.isInstalled(context)) }
    var installedSize by remember { mutableStateOf(GrammarModelManager.installedSize(context)) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var showConfirm by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    SettingsSection(title = "语法模型", content = {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "万象语法模型",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "提升长句、整句输入的候选准确性。模型较大（约 400 MB），安装后可随时删除；" +
                    "未安装不影响基本输入。安装/删除后需重启输入法生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            when {
                downloading -> {
                    LinearProgressIndicator(
                        progress = { if (progress < 0f) 0f else progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (progress < 0f) "正在下载…"
                        else "已下载 ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                installed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "已安装（${formatSize(installedSize)}）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            if (GrammarModelManager.uninstall(context)) {
                                installed = false
                                installedSize = 0L
                                showRestartDialog = true
                            }
                        }) {
                            Icon(Icons.TwoTone.Delete, contentDescription = null)
                            Text("删除")
                        }
                    }
                }
                else -> {
                    TextButton(onClick = { showConfirm = true }) {
                        Icon(Icons.TwoTone.CloudDownload, contentDescription = null)
                        Text("下载安装（约 400 MB）")
                    }
                }
            }
        }
    })

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("下载语法模型") },
            text = {
                Text("模型约 400 MB，建议在 Wi-Fi 环境下下载。下载完成后需重启输入法生效。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    downloading = true
                    progress = 0f
                    scope.launch {
                        val ok = GrammarModelManager.install(context) { p, _, _ -> progress = p }
                        downloading = false
                        if (ok) {
                            installed = true
                            installedSize = GrammarModelManager.installedSize(context)
                            showRestartDialog = true
                        }
                    }
                }) { Text("开始下载") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            }
        )
    }

    // 语法模型缓存在输入法进程内（进程级 OctagramComponent），无法热加载；
    // Android 也不提供重启输入法进程的 API，只能引导用户去系统「应用管理」强制停止。
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("需重启输入法生效") },
            text = {
                Text(
                    "语法模型在输入法进程启动时加载，须先停止应用再重新开启才能生效。\n\n" +
                        "请到系统「应用管理」中结束本应用，然后重新调出键盘使用。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    PermissionHelper.openAppSettings(context)
                }) { Text("去应用管理") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("稍后") }
            }
        )
    }
}

/** 字节数格式化为易读体积。 */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) String.format("%.2f GB", mb / 1024) else String.format("%.1f MB", mb)
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
