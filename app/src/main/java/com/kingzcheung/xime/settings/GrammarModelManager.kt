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
 * 万象语法模型（`.gram`）的按需安装与移除。
 *
 * ## 为什么单独实现而不复用 ModelDownloader
 *
 * `ModelDownloader` 一律落到 `filesDir/models/<id>/`（见 ModelStorage），而 librime 的
 * 语法模型由 `kGramDbType = {"gram_db", "", ".gram"}` 资源解析器定位，只在
 * user_data_dir / shared_data_dir 下查找 `{grammar/language}.gram`。因此本管理器必须
 * 写入 **`filesDir/rime/`**（= RimeEngine 的 user_data_dir），否则引擎永远找不到。
 *
 * ## 与备份的关系
 *
 * 模型约 400MB，远大于用户数据；`RimeExportManager.shouldInclude` 已显式排除
 * `.gram`，故不会被打进备份包。
 *
 * ## 生效方式
 *
 * 语法模块由 `grammar_module.cc` 注册进**进程级** Registry，`OctagramComponent` 的
 * `db_by_language_` 因而也是进程级缓存；一旦某次加载成功便会一直沿用。因此替换或
 * 新装 `.gram` 后，**重启输入法进程**才能可靠生效（设置页会提示用户）。
 * 未安装模型时 `.gram` 不存在，`grammar/language` 查找失败，语法加权静默跳过，
 * 输入方案与基本输入不受影响。
 */
object GrammarModelManager {

    private const val TAG = "GrammarModelManager"

    /** 万象语法模型文件名（librime 按 `grammar/language` 拼 `.gram` 后缀查找）。 */
    const val MODEL_FILE_NAME = "wanxiang-lts-zh-hans.gram"

    /** 下载地址（万象官方 RIME-LMDG 仓库 LTS 发布）。 */
    private const val MODEL_URL =
        "https://github.com/amzxyz/RIME-LMDG/releases/download/LTS/wanxiang-lts-zh-hans.gram"

    /** 模型体积量级（约 400MB），用于 UI 提示与下载前的可读展示。 */
    const val MODEL_SIZE_BYTES = 419_911_724L

    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 600L
    private const val BUFFER_SIZE = 64 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 模型目标文件路径：`filesDir/rime/wanxiang-lts-zh-hans.gram`。 */
    fun modelFile(context: Context): File =
        File(context.filesDir, "rime/$MODEL_FILE_NAME")

    /** 是否已安装（文件存在且非空）。 */
    fun isInstalled(context: Context): Boolean {
        val f = modelFile(context)
        return f.isFile && f.length() > 0
    }

    /** 已安装模型体积（字节），未安装返回 0。 */
    fun installedSize(context: Context): Long =
        modelFile(context).takeIf { it.isFile }?.length() ?: 0L

    /**
     * 下载并安装模型。
     *
     * 先落到临时文件再原子改名，避免下载中断留下半个文件被引擎当作有效模型加载。
     *
     * @param onProgress 进度回调（0f~1f, 已下载字节, 总字节）；总字节未知时为 -1。
     * @return 成功与否
     */
    suspend fun install(context: Context, onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> }): Boolean =
        withContext(Dispatchers.IO) {
            val target = modelFile(context)
            val tmp = File(context.cacheDir, "$MODEL_FILE_NAME.part")
            target.parentFile?.mkdirs()
            try {
                val request = Request.Builder().url(MODEL_URL).build()
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
                                    total
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
                true
            } catch (e: Exception) {
                tmp.delete()
                FileLogger.e(TAG, "Grammar model install failed: ${e.message}", e)
                false
            }
        }

    /** 删除已安装的模型，释放空间。 */
    fun uninstall(context: Context): Boolean {
        val f = modelFile(context)
        val deleted = !f.exists() || f.delete()
        if (deleted) FileLogger.i(TAG, "Grammar model removed")
        return deleted
    }
}
