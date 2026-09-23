package com.kingzcheung.xime.service

import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.util.MimeTypeSupport
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文本上屏与剪贴板提交。
 *
 * 承载 commitImage（图片上屏）、剪贴板候选提交（selectClipboardItem/commitClipboardText/
 * deleteClipboardChars）与语音撤销/搜索动作（performUndo/performSearch）。
 * 共享状态通过 service 引用访问。
 */
internal class ImeTextCommit(private val service: XimeInputMethodService) {
    internal fun performUndo() {
        val currentTextBeforeCursor = service.currentInputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        val currentLength = currentTextBeforeCursor.length
        
        val charsToDelete = currentLength - service.voiceRecognitionHandler.textLengthBeforeVoiceInput
        
        if (charsToDelete > 0) {
            for (i in 0 until charsToDelete) {
                service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }
        
        service.voiceRecognitionHandler.textBeforeVoiceInput = ""
        service.voiceRecognitionHandler.textLengthBeforeVoiceInput = 0
    }
    
    internal fun performSearch() {
        service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    internal fun commitImage(imagePath: String, mimeType: String = ""): Boolean {
        return try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                FileLogger.e(XimeInputMethodService.TAG, "Image file not found: $imagePath")
                return false
            }

            // 按文件特征与扩展名检测真实 MIME 类型
            val actualMimeType = when {
                mimeType.isNotBlank() && mimeType.startsWith("image/") -> mimeType
                imageFile.extension.equals("png", true) -> "image/png"
                imageFile.extension.equals("gif", true) -> "image/gif"
                imageFile.extension.equals("webp", true) -> "image/webp"
                else -> "image/jpeg"
            }

            val editorInfo = service.currentInputEditorInfo
            val inputConnection = service.currentInputConnection

            val uri = getContentUriForImage(imageFile, actualMimeType) ?: return false

            // 先将图片写入系统剪贴板并赋予读取权限，保障无论目标应用走何种协议均能读取。
            // 使用 xime_internal_clip 标识输入法内部写入，避免 captureClipboard 作为新复制回声塞入候选栏推荐。
            val clip = ClipData(
                ClipDescription("xime_internal_clip", arrayOf(actualMimeType)),
                ClipData.Item(uri)
            )
            val cm = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(clip)

            val targetPackage = editorInfo?.packageName
            if (!targetPackage.isNullOrEmpty()) {
                try {
                    service.grantUriPermission(
                        targetPackage,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    FileLogger.w(XimeInputMethodService.TAG, "grantUriPermission failed for $targetPackage", e)
                }
            }

            // 1. 若宿主输入框声明支持富文本，通过 InputConnectionCompat.commitContent 跨进程发送
            if (editorInfo != null && inputConnection != null && supportsMimeType(editorInfo, actualMimeType)) {
                val inputContentInfo = InputContentInfoCompat(
                    uri,
                    ClipDescription("image", arrayOf(actualMimeType)),
                    null
                )
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                val commitResult = InputConnectionCompat.commitContent(inputConnection, editorInfo, inputContentInfo, flags, null)
                FileLogger.i(XimeInputMethodService.TAG, "commitContent result=$commitResult for $imagePath")
                if (commitResult) {
                    return true
                }
            }

            // 2. 宿主未声明富文本支持或 commitContent 失败：当前应用不支持将图片粘贴到此处，返回 false
            false
        } catch (e: Exception) {
            FileLogger.e(XimeInputMethodService.TAG, "Failed to commit image", e)
            false
        }
    }

    /**
     * 判断宿主目标输入框声明的 contentMimeTypes 是否支持指定 MIME 类型（支持通配符匹配）。
     *
     * 匹配规则委托 [MimeTypeSupport.matchesAny]（纯函数、可单测直调），
     * 额外接受的形态由宿主声明侧兼容处理。
     */
    internal fun supportsMimeType(editorInfo: EditorInfo?, mimeType: String): Boolean {
        if (editorInfo == null) return false
        val declaredMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        if (MimeTypeSupport.matchesAny(declaredMimeTypes, mimeType)) return true
        // 兼容宿主声明带参数（如 "image/png;charset=utf-8"）等非标准写法
        return declaredMimeTypes.orEmpty().any { ClipDescription.compareMimeTypes(mimeType, it) }
    }
    

    internal fun selectClipboardItem(text: String) {
        if (service.candidateState.value.isComposing) {
            service.keyRouter.postRimeJob {
                service.rimeEngine.clearComposition()
                withContext(Dispatchers.Main) {
                    service.updateUI()
                }
            }
        }
        // 标记为已消费：候选栏/剪贴板点选上屏后不再重复出现在候选栏
        service.clipboardManager.markConsumed(text)
        // 粘贴不计打字统计（不投 text_committed），联想照常
        service.commitPastedText(text)
    }

    internal fun commitClipboardText(text: String) {
        service.commitPastedText(text)
    }

    internal fun deleteClipboardChars(count: Int) {
        service.currentInputConnection?.deleteSurroundingText(count, 0)
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
                service,
                "${service.packageName}.fileprovider",
                imageFile
            )
        } catch (e: IllegalArgumentException) {
            FileLogger.w(XimeInputMethodService.TAG, "FileProvider direct uri failed, trying cache dir fallback", e)
            try {
                val cacheDir = File(service.cacheDir, "emoji_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val cacheFile = File(cacheDir, imageFile.name)
                if (cacheFile.absolutePath != imageFile.absolutePath) {
                    FileInputStream(imageFile).use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                return FileProvider.getUriForFile(
                    service,
                    "${service.packageName}.fileprovider",
                    cacheFile
                )
            } catch (e2: Exception) {
                FileLogger.w(XimeInputMethodService.TAG, "FileProvider cache fallback also failed", e2)
            }
        } catch (e: Exception) {
            FileLogger.w(XimeInputMethodService.TAG, "FileProvider getUriForFile failed, falling back to MediaStore", e)
        }

        return insertImageToMediaStore(imageFile, mimeType)
    }

    /** 把图片插入 MediaStore（Pictures/Xime），返回系统 content URI。 */
    private fun insertImageToMediaStore(imageFile: File, mimeType: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            FileLogger.e(XimeInputMethodService.TAG, "MediaStore fallback requires API 29+, image commit failed")
            return null
        }
        return try {
            val resolver = service.contentResolver
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val update = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, update, null, null)
                }
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } catch (e: Exception) {
            FileLogger.e(XimeInputMethodService.TAG, "MediaStore insert failed", e)
            null
        }
    }
}