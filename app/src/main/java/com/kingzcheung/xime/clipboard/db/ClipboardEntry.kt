package com.kingzcheung.xime.clipboard.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "clipboard_entries",
    indices = [Index(value = ["text"])]
)
data class ClipboardEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    /** 快捷发送触发编码（如 dh）：用户输入编码前缀命中后，对应快捷条目进入候选栏。 */
    @ColumnInfo(defaultValue = "") val code: String = "",
    @ColumnInfo(defaultValue = "0") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isQuickSend: Boolean = false,
    @ColumnInfo(defaultValue = "0") val consumed: Boolean = false,
    /** 图片私有存储路径，为空表示纯文本条目。 */
    @ColumnInfo(defaultValue = "") val imagePath: String = "",
    /** MIME 类型，如 "image/png"、"image/jpeg" 等。 */
    @ColumnInfo(defaultValue = "") val mimeType: String = ""
) {
    val isImage: Boolean get() = imagePath.isNotEmpty() && mimeType.startsWith("image/")
}
