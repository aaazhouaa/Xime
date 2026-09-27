package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 语法模型（`.gram`）的按需安装、移除与方案配置注入。
 *
 * ## 模型定位
 *
 * librime 的语法模型由 `kGramDbType = {"gram_db", "", ".gram"}` 资源解析器定位，
 * 只在 user_data_dir / shared_data_dir 下查找 `{grammar/language}.gram`。因此本管理器
 * 必须把 `.gram` 写入 **`filesDir/rime/`**（= RimeEngine 的 user_data_dir）。
 *
 * ## 与备份的关系
 *
 * 模型体积较大，`RimeExportManager.shouldInclude` 已显式排除 `.gram`，不随备份打包。
 *
 * ## 生效方式
 *
 * 语法模块由 `grammar_module.cc` 注册进**进程级** Registry，`OctagramComponent` 的
 * `db_by_language_` 因而也是进程级缓存；一旦加载成功便一直沿用。因此安装/替换/删除
 * 后需**重启输入法进程**才能可靠生效（设置页会提示用户）。
 *
 * 未安装模型时，方案 schema 不声明 `grammar/language`，octagram 构造直接返回，
 * 语法加权静默跳过，输入方案与基本输入不受影响。
 */
object GrammarModelManager {

    private const val TAG = "GrammarModelManager"

    /** 语法模型定义：下载地址、librime 语言名与对应的 grammar 配置。 */
    data class GrammarModel(
        val id: String,
        val displayName: String,
        val description: String,
        val fileName: String,
        val language: String,
        val url: String,
        val sizeBytes: Long,
        val collocationMaxLength: Int? = null,
        val collocationMinLength: Int? = null,
        val collocationPenalty: Double? = null,
        val nonCollocationPenalty: Double? = null,
    )

    /** 可选模型清单（单选：同时只能安装一个）。 */
    val MODELS: List<GrammarModel> = listOf(
        GrammarModel(
            id = "moqi",
            displayName = "墨奇模型",
            description = "白霜自带词级轻量模型，体积小、下载快，适合轻度整句增强。",
            fileName = "zh-moqi.gram",
            language = "zh-moqi",
            url = "https://github.com/gaboolic/rime-frost/raw/master/zh-moqi.gram",
            sizeBytes = 7_339_052L,
            nonCollocationPenalty = -4.0,
        ),
        GrammarModel(
            id = "essay-bgw",
            displayName = "八股文·词级模型",
            description = "八股文简体词级模型，整句/长句消歧能力最强，体积较大。",
            fileName = "zh-hans-t-essay-bgw.gram",
            language = "zh-hans-t-essay-bgw",
            url = "https://raw.githubusercontent.com/lotem/rime-octagram-data/hans/zh-hans-t-essay-bgw.gram",
            sizeBytes = 40_925_228L,
            collocationMaxLength = 5,
            collocationMinLength = 2,
            collocationPenalty = -14.0,
            nonCollocationPenalty = -4.0,
        ),
    )

    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 600L
    private const val BUFFER_SIZE = 64 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 模型文件路径：`filesDir/rime/{fileName}`。 */
    fun modelFile(context: Context, model: GrammarModel): File =
        File(context.filesDir, "rime/${model.fileName}")

    /** 是否已安装该模型（文件存在且非空）。 */
    fun isInstalled(context: Context, model: GrammarModel): Boolean {
        val f = modelFile(context, model)
        return f.isFile && f.length() > 0
    }

    /**
     * 当前启用的模型。
     *
     * 以启用方案 `.custom.yaml` 中注入的 `grammar/language` 为准，而非文件是否存在，
     * 避免历史残留文件被误判为「使用中」。
     */
    fun currentModel(context: Context): GrammarModel? {
        val rimeDir = SchemaManager.getRimeDir(context)
        for (model in MODELS) {
            for (schemaId in SchemaManager.getEnabledSchemas(context)) {
                val custom = File(rimeDir, "$schemaId.custom.yaml")
                if (!custom.exists()) continue
                val text = custom.readText(Charsets.UTF_8)
                if (Regex(""""grammar/language"\s*:\s*"?${Regex.escape(model.language)}"?[ \t]*$""", RegexOption.MULTILINE)
                        .containsMatchIn(text)) {
                    return model
                }
            }
        }
        return null
    }

    /** 已安装模型体积（字节），未安装返回 0。 */
    fun installedSize(context: Context, model: GrammarModel): Long =
        modelFile(context, model).takeIf { it.isFile }?.length() ?: 0L

    /**
     * 下载并安装模型，随后把对应 grammar 配置注入启用方案。
     *
     * 先落到临时文件再原子改名，避免下载中断留下半个文件被引擎当作有效模型加载。
     *
     * @param onProgress 进度回调（0f~1f, 已下载字节, 总字节）；总字节未知时为 -1。
     * @return 成功与否
     */
    suspend fun install(
        context: Context,
        model: GrammarModel,
        onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        val target = modelFile(context, model)
        val tmp = File(context.cacheDir, "${model.fileName}.part")
        target.parentFile?.mkdirs()
        try {
            val request = Request.Builder().url(model.url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("响应体为空")
                val total = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(
                                if (total > 0) downloaded.toFloat() / total.toFloat() else -1f,
                                downloaded,
                                total,
                            )
                        }
                    }
                }
                if (total > 0 && downloaded != total) {
                    tmp.delete()
                    throw IOException("下载不完整：$downloaded/$total 字节")
                }
                if (downloaded == 0L) {
                    tmp.delete()
                    throw IOException("下载内容为空")
                }
            }
            // 原子改名：先删旧文件再改名（File.renameTo 在目标存在时行为不定）
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                // 跨分区改名失败时退化为复制
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            FileLogger.i(TAG, "Grammar model installed: ${target.length()} bytes")
            // 注入对应 grammar 配置到启用方案
            PersonalDictManager.applyGrammarPatch(context, model)
            true
        } catch (e: Exception) {
            tmp.delete()
            FileLogger.e(TAG, "Grammar model install failed: ${e.message}", e)
            false
        }
    }

    /** 删除已安装的模型，并清除方案中的 grammar 配置。 */
    fun uninstall(context: Context, model: GrammarModel? = null): Boolean {
        val target = model?.let { modelFile(context, it) } ?: currentModel(context)?.let { modelFile(context, it) }
        val deleted = target?.let { !it.exists() || it.delete() } ?: true
        PersonalDictManager.clearGrammarPatch(context)
        if (deleted) FileLogger.i(TAG, "Grammar model removed")
        return deleted
    }
}
