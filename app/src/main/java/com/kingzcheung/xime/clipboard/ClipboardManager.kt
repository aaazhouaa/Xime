package com.kingzcheung.xime.clipboard

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.kingzcheung.xime.clipboard.db.ClipboardDatabase
import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import com.kingzcheung.xime.util.PermissionHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import android.content.ClipboardManager as AndroidClipboardManager

internal data class ClipItemSnapshot(
    val uri: Uri? = null,
    val text: String? = null,
    val declaredMimeType: String? = null
)

data class ClipboardItem(
    val id: Long = 0,
    val text: String,
    /** 快捷发送触发编码（如 dh），空 = 仅内容命中不参与编码匹配。 */
    val code: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isQuickSend: Boolean = false,
    val consumed: Boolean = false,
    val imagePath: String = "",
    val mimeType: String = ""
) {
    val isImage: Boolean get() = imagePath.isNotEmpty() && mimeType.startsWith("image/")
}

class ClipboardManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ClipboardManager"
        private const val MAX_ITEMS = 1000
        private const val MAX_QUICK_SEND_ITEMS = 20
        private const val IMAGE_EXPIRE_DURATION_MS = 6 * 3600 * 1000L // 6 小时自动清理未设为快捷的图片
        private const val PREFS_NAME = "clipboard_prefs"
        private const val KEY_CLIPBOARD_ITEMS = "clipboard_items"
        private const val KEY_QUICK_SEND_ITEMS = "quick_send_items"

        /**
         * 判断文件名/路径/相册名是否为截图（纯函数，供单测直调）。
         * 覆盖系统与主流 ROM 的命名约定：Screenshot / ScreenCapture / 截屏 / 截图。
         */
        fun isScreenshotPath(pathOrName: String): Boolean {
            val lower = pathOrName.lowercase()
            return lower.contains("screenshot") ||
                lower.contains("screen_shot") ||
                lower.contains("screencapture") ||
                lower.contains("截屏") ||
                lower.contains("截图")
        }

        fun detectImageMimeType(bytes: ByteArray): String? {
            if (bytes.size < 4) return null
            // PNG: 89 50 4E 47
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
                return "image/png"
            }
            // JPEG: FF D8 FF
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
                return "image/jpeg"
            }
            // GIF: 47 49 46 38 ("GIF8")
            if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) {
                return "image/gif"
            }
            // WebP: RIFF....WEBP (size >= 12)
            if (bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) {
                return "image/webp"
            }
            // BMP: 42 4D ("BM")
            if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) {
                return "image/bmp"
            }
            return null
        }

        @Volatile
        private var instance: ClipboardManager? = null

        fun getInstance(context: Context): ClipboardManager {
            return instance ?: synchronized(this) {
                instance ?: ClipboardManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val androidClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager

    private val clipboardListener = AndroidClipboardManager.OnPrimaryClipChangedListener {
        readClipboard()
    }

    private val screenshotObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            detectRecentScreenshot()
        }
    }

    private fun readClipboard(retries: Int = 3) {
        captureClipboard(retries)
    }

    fun captureClipboard(retries: Int = 3) {
        cleanExpiredImages()
        detectRecentScreenshot()

        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                captureClipboard(retries)
            }
            return
        }

        try {
            val clipData = androidClipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val snapshot = mutableListOf<ClipItemSnapshot>()
                val desc = clipData.description
                for (i in 0 until clipData.itemCount) {
                    val item = clipData.getItemAt(i)
                    var uri = item.uri
                    val text = item.text?.toString()
                    if (uri == null && text != null) {
                        val trimmed = text.trim()
                        if (trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
                            try {
                                uri = Uri.parse(trimmed)
                            } catch (_: Exception) {}
                        }
                    }
                    var declaredMime: String? = null
                    if (desc != null) {
                        for (mIndex in 0 until desc.mimeTypeCount) {
                            val m = desc.getMimeType(mIndex)
                            if (m.startsWith("image/")) {
                                declaredMime = m
                                break
                            }
                        }
                    }
                    snapshot.add(ClipItemSnapshot(uri = uri, text = text, declaredMimeType = declaredMime))
                }
                processClipSnapshot(snapshot)
                return
            }
            if (retries > 0) {
                Handler(Looper.getMainLooper()).postDelayed({ captureClipboard(retries - 1) }, 150L)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read clipboard: missing permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error capturing clipboard", e)
        }
    }

    private fun processClipSnapshot(snapshot: List<ClipItemSnapshot>) {
        scope.launch {
            for (item in snapshot) {
                if (item.uri != null) {
                    var mimeType = item.declaredMimeType
                    if (mimeType.isNullOrEmpty()) {
                        mimeType = try {
                            context.contentResolver.getType(item.uri)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    val saved = saveAndAddImage(item.uri, mimeType)
                    if (saved) return@launch
                }
                if (!item.text.isNullOrBlank()) {
                    addItem(item.text)
                    return@launch
                }
            }
        }
    }

    fun detectRecentScreenshot() {
        if (!PermissionHelper.hasMediaImagesPermission(context)) {
            Log.d(TAG, "detectRecentScreenshot skipped: missing media permission")
            return
        }

        scope.launch {
            try {
                // 1. 通过 MediaStore 查询最近 300 秒（5分钟）内新增或修改的截图
                val windowSeconds = 300L
                val cutoffSeconds = (System.currentTimeMillis() - windowSeconds * 1000L) / 1000L
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                val projectionList = mutableListOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.MIME_TYPE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    projectionList.add(MediaStore.Images.Media.RELATIVE_PATH)
                    projectionList.add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                }

                val projection = projectionList.toTypedArray()
                val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
                val selectionArgs = arrayOf(cutoffSeconds.toString())
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                var foundUri: Uri? = null
                var foundMime: String? = null

                try {
                    context.contentResolver.query(
                        collection,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                        val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                        val mimeColumn = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                        val relPathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                        } else -1
                        val bucketColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        } else -1

                        while (cursor.moveToNext()) {
                            val name = if (nameColumn >= 0) cursor.getString(nameColumn) ?: "" else ""
                            val data = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else ""
                            val relPath = if (relPathColumn >= 0) cursor.getString(relPathColumn) ?: "" else ""
                            val bucket = if (bucketColumn >= 0) cursor.getString(bucketColumn) ?: "" else ""

                            if (isScreenshotPath(name) || isScreenshotPath(data) || isScreenshotPath(relPath) || isScreenshotPath(bucket)) {
                                val id = cursor.getLong(idColumn)
                                foundUri = android.content.ContentUris.withAppendedId(collection, id)
                                foundMime = if (mimeColumn >= 0) cursor.getString(mimeColumn) else null
                                Log.i(TAG, "Detected recent screenshot in MediaStore: id=$id, name=$name")
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore query for screenshot failed", e)
                }

                if (foundUri != null) {
                    saveAndAddImage(foundUri!!, foundMime)
                }
            } catch (e: Exception) {
                Log.e(TAG, "detectRecentScreenshot failed", e)
            }
        }
    }

    private fun saveAndAddImage(uri: Uri, declaredMimeType: String?): Boolean {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: return false
            if (bytes.isEmpty()) return false

            val detectedMime = detectImageMimeType(bytes)
            val finalMime = detectedMime
                ?: (if (declaredMimeType?.startsWith("image/") == true) declaredMimeType else null)
                ?: when {
                    uri.path?.endsWith(".png", true) == true -> "image/png"
                    uri.path?.endsWith(".jpg", true) == true || uri.path?.endsWith(".jpeg", true) == true -> "image/jpeg"
                    uri.path?.endsWith(".gif", true) == true -> "image/gif"
                    uri.path?.endsWith(".webp", true) == true -> "image/webp"
                    else -> null
                }
                ?: return false

            val hash = computeSha256(bytes)
            val ext = when (finalMime.lowercase()) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                "image/bmp" -> "bmp"
                else -> "jpg"
            }
            val imagesDir = File(context.filesDir, "clipboard_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val destFile = File(imagesDir, "clip_${hash}.$ext")
            if (!destFile.exists()) {
                destFile.writeBytes(bytes)
            }
            addImageItem(destFile.absolutePath, finalMime)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy clipboard image stream to private dir", e)
            false
        }
    }

    private fun computeSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun cleanExpiredImages() {
        scope.launch {
            try {
                val cutoff = System.currentTimeMillis() - IMAGE_EXPIRE_DURATION_MS
                val expiredList = dao.findExpiredUnquickImages(cutoff)
                if (expiredList.isEmpty()) return@launch

                val quickPaths = dao.getQuickSendImagePaths().toSet()
                val idsToDelete = mutableListOf<Long>()

                for (entry in expiredList) {
                    idsToDelete.add(entry.id)
                    if (entry.imagePath.isNotEmpty() && entry.imagePath !in quickPaths) {
                        try {
                            val file = File(entry.imagePath)
                            if (file.exists()) {
                                file.delete()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete expired image file: ${entry.imagePath}", e)
                        }
                    }
                }
                if (idsToDelete.isNotEmpty()) {
                    dao.deleteClipboardByIds(idsToDelete)
                    Log.i(TAG, "Cleaned ${idsToDelete.size} expired non-quick clipboard images")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanExpiredImages", e)
            }
        }
    }

    private val database = ClipboardDatabase.getInstance(context)
    private val dao = database.clipboardDao()
    private val scope = ClipboardDatabase.scope()

    private val _clipboardItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardItems: StateFlow<List<ClipboardItem>> = _clipboardItems.asStateFlow()

    private val _quickSendItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val quickSendItems: StateFlow<List<ClipboardItem>> = _quickSendItems.asStateFlow()

    private val _recentItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val recentItems: StateFlow<List<ClipboardItem>> = _recentItems.asStateFlow()

    /** 本地剪贴板变更事件流（新增/更新条目时发射，供剪贴板同步等外部消费）。 */
    private val _clipboardChanged = MutableSharedFlow<ClipboardItem>(extraBufferCapacity = 16)
    val clipboardChanged: SharedFlow<ClipboardItem> = _clipboardChanged.asSharedFlow()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyData()
        scope.launch {
            dao.observeAll().collect { entries ->
                _clipboardItems.value = entries.map { it.toClipboardItem() }
                updateRecentItems()
            }
        }
        scope.launch {
            dao.observeQuickSend().collect { entries ->
                _quickSendItems.value = entries.map { it.toClipboardItem() }
            }
        }
        startListening()
    }

    /**
     * 将旧版 SharedPreferences 中的剪贴板/快捷发送数据一次性迁移到 Room。
     * 幂等：prefs 无数据时直接返回；迁移成功后删除 prefs 键，避免重复迁移。
     */
    fun migrateLegacyData() {
        val legacyClipboard = prefs.getString(KEY_CLIPBOARD_ITEMS, null)
        val legacyQuickSend = prefs.getString(KEY_QUICK_SEND_ITEMS, null)
        if (legacyClipboard == null && legacyQuickSend == null) return
        scope.launch {
            try {
                val entries = mutableListOf<ClipboardEntry>()
                legacyClipboard?.let { str ->
                    deserializeItems(str).forEach { item ->
                        entries.add(item.toEntry())
                    }
                }
                legacyQuickSend?.let { str ->
                    deserializeItems(str).forEach { item ->
                        entries.add(item.toEntry())
                    }
                }
                if (entries.isNotEmpty()) {
                    dao.insertAll(entries)
                }
                prefs.edit()
                    .remove(KEY_CLIPBOARD_ITEMS)
                    .remove(KEY_QUICK_SEND_ITEMS)
                    .apply()
                Log.i(TAG, "Migrated ${entries.size} legacy clipboard items to Room")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate legacy clipboard items", e)
            }
        }
    }

    private fun updateRecentItems() {
        val now = System.currentTimeMillis()
        val cutoff = now - 10 * 1000L
        _recentItems.value = _clipboardItems.value.filter { it.timestamp >= cutoff }
    }

    private fun deserializeItems(json: String): List<ClipboardItem> {
        if (json.isEmpty()) return emptyList()
        return json.split("|||").mapNotNull { itemStr ->
            val parts = itemStr.split(":::")
            if (parts.size == 5) {
                try {
                    ClipboardItem(
                        id = parts[0].toLong(),
                        text = parts[1].unescape(),
                        timestamp = parts[2].toLong(),
                        isPinned = parts[3].toBoolean(),
                        isQuickSend = parts[4].toBoolean()
                    )
                } catch (e: Exception) {
                    null
                }
            } else if (parts.size == 4) {
                try {
                    ClipboardItem(
                        id = parts[0].toLong(),
                        text = parts[1].unescape(),
                        timestamp = parts[2].toLong(),
                        isPinned = parts[3].toBoolean(),
                        isQuickSend = false
                    )
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }

    private fun String.unescape(): String {
        return this.replace("〈PIPE〉", "|||").replace("〈COLON〉", ":::")
    }

    private fun ClipboardItem.toEntry(): ClipboardEntry {
        return ClipboardEntry(
            id = 0,
            text = text,
            code = code,
            timestamp = timestamp,
            isPinned = isPinned,
            isQuickSend = isQuickSend,
            consumed = consumed,
            imagePath = imagePath,
            mimeType = mimeType
        )
    }

    private fun ClipboardEntry.toClipboardItem(): ClipboardItem {
        return ClipboardItem(
            id = id,
            text = text,
            code = code,
            timestamp = timestamp,
            isPinned = isPinned,
            isQuickSend = isQuickSend,
            consumed = consumed,
            imagePath = imagePath,
            mimeType = mimeType
        )
    }

    private fun startListening() {
        androidClipboardManager.addPrimaryClipChangedListener(clipboardListener)
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            context.contentResolver.registerContentObserver(collection, true, screenshotObserver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register screenshot MediaStore ContentObserver", e)
        }
    }

    fun release() {
        // Singleton — no cleanup needed.
    }

    fun addItem(text: String) {
        if (text.isBlank()) return
        scope.launch {
            dao.upsertAndTrim(text, System.currentTimeMillis(), MAX_ITEMS)
            _clipboardChanged.emit(
                ClipboardItem(
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun addImageItem(imagePath: String, mimeType: String) {
        if (imagePath.isBlank()) return
        scope.launch {
            val now = System.currentTimeMillis()
            // 已作为快捷发送条目存在时不重复入库，也不广播变更事件：
            // 否则 commitImage 写系统剪贴板的回声会把「已发送的图」塞回剪贴板历史。
            if (!dao.upsertImageAndTrim(imagePath, mimeType, now, MAX_ITEMS)) return@launch
            _clipboardChanged.emit(
                ClipboardItem(
                    text = "[图片]",
                    imagePath = imagePath,
                    mimeType = mimeType,
                    timestamp = now
                )
            )
        }
    }

    fun removeItem(id: Long) {
        scope.launch {
            val entry = dao.findById(id)
            if (entry != null && entry.imagePath.isNotEmpty()) {
                val quickPaths = dao.getQuickSendImagePaths().toSet()
                if (entry.imagePath !in quickPaths) {
                    try {
                        val file = File(entry.imagePath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete image file on removeItem", e)
                    }
                }
            }
            dao.deleteClipboardById(id)
        }
    }

    /** 批量删除剪贴板条目（仅 isQuickSend = 0，不影响快捷发送）。 */
    fun removeItems(ids: List<Long>) {
        if (ids.isEmpty()) return
        scope.launch {
            val quickPaths = dao.getQuickSendImagePaths().toSet()
            for (id in ids) {
                val entry = dao.findById(id)
                if (entry != null && entry.imagePath.isNotEmpty() && entry.imagePath !in quickPaths) {
                    try {
                        val file = File(entry.imagePath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete image file on removeItems", e)
                    }
                }
            }
            dao.deleteClipboardByIds(ids)
        }
    }

    /** 清空剪贴板（仅 isQuickSend = 0，不影响快捷发送）。 */
    fun clearClipboard() {
        scope.launch {
            val unquickImages = dao.findExpiredUnquickImages(Long.MAX_VALUE)
            val quickPaths = dao.getQuickSendImagePaths().toSet()
            for (entry in unquickImages) {
                if (entry.imagePath.isNotEmpty() && entry.imagePath !in quickPaths) {
                    try {
                        val file = File(entry.imagePath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete image file on clearClipboard", e)
                    }
                }
            }
            dao.clearAllClipboard()
        }
    }

    fun splitItem(id: Long) {
        scope.launch {
            val item = _clipboardItems.value.find { it.id == id } ?: return@launch
            dao.deleteClipboardById(id)
            val now = System.currentTimeMillis()
            item.text.forEachIndexed { index, char ->
                dao.insert(
                    ClipboardEntry(
                        text = char.toString(),
                        timestamp = now + index
                    )
                )
            }
        }
    }

    fun clearAll() {
        scope.launch {
            dao.clearUnpinned()
        }
    }

    fun addToQuickSend(id: Long) {
        scope.launch {
            dao.addQuickSend(id, System.currentTimeMillis(), MAX_QUICK_SEND_ITEMS)
        }
    }

    fun removeFromQuickSend(id: Long) {
        scope.launch {
            dao.deleteQuickSendById(id)
        }
    }

    fun togglePinQuickSend(id: Long) {
        scope.launch {
            dao.updateTimestamp(id, System.currentTimeMillis())
        }
    }

    fun updateQuickSendItem(id: Long, newText: String, newCode: String = ""): Boolean {
        if (newText.isBlank()) return false
        val index = _quickSendItems.value.indexOfFirst { it.id == id }
        if (index < 0) return false
        scope.launch {
            dao.updateQuickSendItem(id, newText, newCode.trim(), System.currentTimeMillis())
        }
        return true
    }

    fun addQuickSendItem(text: String, code: String = "") {
        if (text.isBlank()) return
        scope.launch {
            dao.insertQuickSend(text, code.trim(), System.currentTimeMillis(), MAX_QUICK_SEND_ITEMS)
        }
    }

    fun copyToSystemClipboard(text: String) {
        val clip = ClipData.newPlainText("kime_clipboard", text)
        androidClipboardManager.setPrimaryClip(clip)
    }

    fun getCurrentClipboardText(): String? {
        val clipData = androidClipboardManager.primaryClip
        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).text?.toString()
        } else null
    }

    fun getRecentItems(seconds: Int = 60): List<ClipboardItem> {
        val now = System.currentTimeMillis()
        val cutoff = now - seconds * 1000L
        // 候选栏只展示未消费的最近剪贴板项（用户点选上屏后标记 consumed 不再显示）
        return _clipboardItems.value.filter { it.timestamp >= cutoff && !it.consumed }
    }

    fun markConsumed(id: Long) {
        scope.launch {
            dao.markConsumed(id)
        }
    }

    /**
     * 标记指定文本的剪贴板条目为"已消费"（候选栏不再显示）。
     * 匹配最近一条未消费的相同文本，避免影响历史重复条目。
     */
    fun markConsumed(text: String) {
        scope.launch {
            val item = _clipboardItems.value
                .filter { it.text == text && !it.consumed }
                .maxByOrNull { it.timestamp } ?: return@launch
            dao.markConsumed(item.id)
        }
    }

    fun copyImageToSystemClipboard(imagePath: String, mimeType: String = "", label: String = "clipboard_image"): Boolean {
        return try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file not found: $imagePath")
                return false
            }

            val actualMime = when {
                mimeType.isNotBlank() -> mimeType
                imageFile.extension.equals("png", true) -> "image/png"
                imageFile.extension.equals("gif", true) -> "image/gif"
                imageFile.extension.equals("webp", true) -> "image/webp"
                else -> "image/jpeg"
            }

            val uri = getContentUriForImage(imageFile, actualMime) ?: return false

            val clip = ClipData(
                android.content.ClipDescription(label, arrayOf(actualMime)),
                ClipData.Item(uri)
            )
            androidClipboardManager.setPrimaryClip(clip)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to clipboard", e)
            false
        }
    }

    /**
     * 生成图片 content URI。
     *
     * 优先使用 FileProvider；Android 12+ 部分厂商 ROM 上
     * FileProvider.getUriForFile 内部 resolveContentProvider 以 USER_ALL(-10000)
     * 校验跨用户权限时抛 "Invalid userId -10000"，此时降级为 MediaStore
     * 插入图片获取系统 content URI（API 29+ 免权限）。
     */
    private fun getContentUriForImage(imageFile: File, mimeType: String): Uri? {
        try {
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            Log.w(TAG, "FileProvider getUriForFile failed, falling back to MediaStore", e)
        }
        return insertImageToMediaStore(imageFile, mimeType)
    }

    /** 把图片插入 MediaStore（Pictures/Xime），返回系统 content URI。 */
    private fun insertImageToMediaStore(imageFile: File, mimeType: String = "image/jpeg"): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.e(TAG, "MediaStore fallback requires API 29+, clipboard image copy failed")
            return null
        }
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Xime")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(imageFile).use { input -> input.copyTo(output) }
                } ?: return null
                val update = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, update, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore insert failed", e)
            null
        }
    }
}
