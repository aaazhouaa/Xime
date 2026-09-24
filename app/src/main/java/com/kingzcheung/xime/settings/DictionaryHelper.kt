package com.kingzcheung.xime.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class DictEntry(
    val word: String,
    val code: String,
    val weight: Int? = null
)

object DictionaryHelper {
    private const val TAG = "DictionaryHelper"

    /** 解析一个 .dict.yaml 文本里 `...` 之后的词条（`词<TAB>码`，也容忍空格分隔）。纯函数。 */
    fun parseDictEntries(text: String): List<DictEntry> {
        val out = mutableListOf<DictEntry>()
        var inData = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!inData) {
                if (line == "...") inData = true
                continue
            }
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split("\t", "  ", " ").filter { it.isNotEmpty() }
            if (parts.size >= 2) out.add(DictEntry(parts[0], parts[1]))
        }
        return out
    }

    /** 解析 .dict.yaml 头部的 `import_tables`（块式 `- x` 或内联 `[a, b]`）。纯函数。 */
    fun parseImportTables(text: String): List<String> {
        val tables = linkedSetOf<String>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line == "...") break // 头部结束，后面是词条
            if (line.startsWith("import_tables:")) {
                val inline = line.substringAfter(":").trim()
                if (inline.startsWith("[")) {
                    inline.trim('[', ']').split(",")
                        .map { it.trim().trim('"') }.filter { it.isNotEmpty() }
                        .forEach { tables.add(it) }
                } else {
                    var j = i + 1
                    while (j < lines.size && lines[j].trim().startsWith("- ")) {
                        tables.add(lines[j].trim().removePrefix("- ").trim().trim('"'))
                        j++
                    }
                    i = j - 1
                }
            }
            i++
        }
        return tables.toList()
    }

    /**
     * 跟随 `import_tables` 递归收集词条（注入读取器，便于单测；按表名去重防环）。
     * 修复"主词典靠 import_tables 组装时(如 quick5/cangjie5)词库查看器为空"。
     */
    fun collectEntries(rootDict: String, readDict: (String) -> String?): List<DictEntry> {
        val out = mutableListOf<DictEntry>()
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque(listOf(rootDict))
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (!seen.add(name)) continue
            val text = readDict(name) ?: continue
            out.addAll(parseDictEntries(text))
            for (t in parseImportTables(text)) if (t !in seen) queue.addLast(t)
        }
        return out
    }

    fun loadDictionary(context: Context, schemaId: String): List<DictEntry> {
        val dictName = SchemaManager.getReferencedDictName(context, schemaId) ?: schemaId
        val dir = SchemaManager.getRimeDir(context)
        return try {
            collectEntries(dictName) { name ->
                val f = File(dir, "$name.dict.yaml")
                if (f.exists()) f.readText(Charsets.UTF_8) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary for $schemaId", e)
            emptyList()
        }
    }

    fun searchDictionary(entries: List<DictEntry>, query: String): List<DictEntry> {
        if (query.isEmpty()) return entries.take(100)
        val lowerQuery = query.lowercase(Locale.ROOT)
        return entries.filter {
            it.word.contains(query) || it.code.contains(query) || it.code.lowercase(Locale.ROOT).contains(lowerQuery)
        }.take(100)
    }

    // 黑名单类型：英文词典、OpenCC、编码表等禁止导入到拼音词库
    private val DISALLOWED_DICT_PREFIXES = setOf(
        "en", "en_ext", "melt_eng", "easy_en", "english", "radical_pinyin", "stroke"
    )

    /**
     * 判断词库名称或文件名是否为允许导入的拼音词库类型。
     * 明确拒绝英文词库（en、melt_eng 等）及部首/笔画等非拼音码表。
     */
    fun isAllowedPinyinDict(dictName: String): Boolean {
        val lower = dictName.lowercase(Locale.ROOT)
            .removeSuffix(".dict.yaml")
            .substringAfterLast('/')
        if (DISALLOWED_DICT_PREFIXES.contains(lower)) return false
        if (lower.startsWith("en_") || lower.startsWith("melt_") || lower.contains("english")) return false
        return true
    }

    /**
     * 校验文本是否具有合法的 Rime 拼音词典 YAML 文件头：
     * 包含 `---`、`name: <dict_name>` 以及元数据结束标记 `...`，
     * 且排除英文词库及不适用的码表。
     * 返回 (是否合法, 提取的词典名, 错误原因)。
     */
    fun parseDictHeader(headerText: String): Triple<Boolean, String?, String?> {
        var foundStart = false
        var dictName: String? = null
        for (raw in headerText.lineSequence()) {
            val line = raw.trim()
            if (line == "---") {
                foundStart = true
                continue
            }
            if (foundStart) {
                if (line.startsWith("name:")) {
                    dictName = line.substringAfter("name:").trim().trim('"', '\'')
                }
                if (line == "...") {
                    if (dictName == null) {
                        return Triple(false, null, "词库头部缺少 name: 字段")
                    }
                    if (!isAllowedPinyinDict(dictName)) {
                        return Triple(false, dictName, "不支持导入英文词典或非拼音码表 ($dictName)")
                    }
                    return Triple(true, dictName, null)
                }
            }
        }
        return Triple(false, null, "缺少合法的 YAML 元数据头 (--- 与 ...)")
    }

    /** 雾凇拼音 6 份核心词库的远端下载地址与目标相对路径 */
    val FROST_DICT_SOURCES = listOf(
        "8105.dict.yaml" to "https://raw.githubusercontent.com/iDvel/rime-ice/main/cn_dicts/8105.dict.yaml",
        "41448.dict.yaml" to "https://raw.githubusercontent.com/iDvel/rime-ice/main/cn_dicts/41448.dict.yaml",
        "base.dict.yaml" to "https://raw.githubusercontent.com/iDvel/rime-ice/main/cn_dicts/base.dict.yaml",
        "ext.dict.yaml" to "https://raw.githubusercontent.com/iDvel/rime-ice/main/cn_dicts/ext.dict.yaml",
        "tencent.dict.yaml" to "https://raw.githubusercontent.com/iDvel/rime-ice/main/cn_dicts/tencent.dict.yaml",
        "others.dict.yaml" to "https://raw.githubusercontent.com/iDvel/rime-ice/main/cn_dicts/others.dict.yaml"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * 从网络下载更新雾凇拼音的 6 份核心词库。
     */
    suspend fun updateFrostDicts(
        context: Context,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val rimeDir = SchemaManager.getRimeDir(context)
        val cnDictsDir = File(rimeDir, "cn_dicts").apply { mkdirs() }
        val total = FROST_DICT_SOURCES.size

        try {
            for ((index, item) in FROST_DICT_SOURCES.withIndex()) {
                val (fileName, url) = item
                onProgress(index + 1, total, fileName)
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载 $fileName 失败: HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("$fileName 响应体为空"))
                val targetFile = File(cnDictsDir, fileName)
                val tempFile = File(cnDictsDir, "$fileName.tmp")
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update frost dicts", e)
            Result.failure(e)
        }
    }

    /**
     * 导入外部词典文件（.dict.yaml 单文件或包含词库的 .zip 压缩包）。
     */
    suspend fun importDictionary(
        context: Context,
        uri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var displayName = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: ""
            }
        }
        if (displayName.isEmpty()) {
            displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "unknown.dict.yaml"
        }

        val rimeDir = SchemaManager.getRimeDir(context)
        try {
            if (displayName.endsWith(".zip", ignoreCase = true)) {
                // 压缩包导入：过滤非拼音/英文词库
                var extractedCount = 0
                val skippedDicts = mutableListOf<String>()
                contentResolver.openInputStream(uri)?.use { stream ->
                    ZipInputStream(stream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (!entry.isDirectory && name.endsWith(".dict.yaml", ignoreCase = true)) {
                                val baseName = name.removeSuffix(".dict.yaml").substringAfterLast('/')
                                if (isAllowedPinyinDict(baseName)) {
                                    val target = File(rimeDir, name)
                                    target.parentFile?.mkdirs()
                                    FileOutputStream(target).use { out -> zis.copyTo(out) }
                                    extractedCount++
                                } else {
                                    skippedDicts.add(name)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                if (extractedCount > 0) {
                    val skippedTip = if (skippedDicts.isNotEmpty()) "（已自动过滤掉 ${skippedDicts.size} 个英文/不支持的词典）" else ""
                    Result.success("成功解压导入 $extractedCount 个拼音词典文件$skippedTip")
                } else {
                    val reason = if (skippedDicts.isNotEmpty()) "压缩包内仅包含英文或不支持的码表，已全部拦截" else "压缩包内未找到 .dict.yaml 词典文件"
                    Result.failure(Exception(reason))
                }
            } else {
                if (!displayName.endsWith(".dict.yaml", ignoreCase = true) && !displayName.endsWith(".yaml", ignoreCase = true)) {
                    return@withContext Result.failure(Exception("仅支持导入 .dict.yaml 拼音词典文件或 .zip 压缩包"))
                }

                // 单个文件导入
                val tempFile = File.createTempFile("dict_import_", ".tmp", context.cacheDir)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                } ?: return@withContext Result.failure(Exception("无法读取选择的文件"))

                // 读前 100 行检查合法文件头及类型
                val headerSnippet = tempFile.bufferedReader().useLines { lines ->
                    lines.take(100).joinToString("\n")
                }
                val (isValid, dictName, errorMsg) = parseDictHeader(headerSnippet)
                if (!isValid || dictName.isNullOrBlank()) {
                    tempFile.delete()
                    return@withContext Result.failure(Exception(errorMsg ?: "无效的词库文件格式"))
                }

                val finalName = if (displayName.endsWith(".dict.yaml", ignoreCase = true)) {
                    displayName
                } else {
                    "$dictName.dict.yaml"
                }
                val targetFile = File(rimeDir, finalName)
                if (targetFile.exists()) targetFile.delete()
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                Result.success("成功导入拼音词典：$finalName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import dictionary from $uri", e)
            Result.failure(e)
        }
    }
}
