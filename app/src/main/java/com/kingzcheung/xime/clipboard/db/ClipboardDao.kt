package com.kingzcheung.xime.clipboard.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 0 ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 1 ORDER BY timestamp DESC")
    fun observeQuickSend(): Flow<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_entries WHERE text = :text AND isQuickSend = 0 LIMIT 1")
    suspend fun findByText(text: String): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE text = :text AND isQuickSend = 1 LIMIT 1")
    suspend fun findQuickSendByText(text: String): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE imagePath = :imagePath AND isQuickSend = 0 LIMIT 1")
    suspend fun findByImagePath(imagePath: String): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE imagePath = :imagePath AND isQuickSend = 1 LIMIT 1")
    suspend fun findQuickSendByImagePath(imagePath: String): ClipboardEntry?

    @Query("SELECT DISTINCT imagePath FROM clipboard_entries WHERE isQuickSend = 1 AND imagePath != ''")
    suspend fun getQuickSendImagePaths(): List<String>

    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 0 AND imagePath != '' AND timestamp < :expireTime")
    suspend fun findExpiredUnquickImages(expireTime: Long): List<ClipboardEntry>

    @Query("SELECT * FROM clipboard_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ClipboardEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ClipboardEntry>)

    @Query("UPDATE clipboard_entries SET timestamp = :timestamp, consumed = 0 WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long)

    @Query("DELETE FROM clipboard_entries WHERE isQuickSend = 0 AND id = :id")
    suspend fun deleteClipboardById(id: Long)

    @Query("DELETE FROM clipboard_entries WHERE isQuickSend = 1 AND id = :id")
    suspend fun deleteQuickSendById(id: Long)

    @Query("DELETE FROM clipboard_entries WHERE isQuickSend = 0 AND id IN (:ids)")
    suspend fun deleteClipboardByIds(ids: List<Long>)

    @Query("DELETE FROM clipboard_entries WHERE isQuickSend = 0")
    suspend fun clearAllClipboard()

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0")
    suspend fun clearUnpinned()

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE isPinned = 0")
    suspend fun countUnpinned(): Int

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0 AND id IN (SELECT id FROM clipboard_entries WHERE isPinned = 0 ORDER BY timestamp ASC LIMIT :limit)")
    suspend fun trimUnpinned(limit: Int)

    @Query("DELETE FROM clipboard_entries WHERE isQuickSend = 1 AND id IN (SELECT id FROM clipboard_entries WHERE isQuickSend = 1 ORDER BY timestamp ASC LIMIT :limit)")
    suspend fun trimQuickSend(limit: Int)

    @Query("UPDATE clipboard_entries SET text = :text, timestamp = :now WHERE id = :id")
    suspend fun updateText(id: Long, text: String, now: Long)

    @Query("UPDATE clipboard_entries SET text = :text, code = :code, timestamp = :now WHERE id = :id")
    suspend fun updateQuickSendItem(id: Long, text: String, code: String, now: Long)

    @Query("UPDATE clipboard_entries SET consumed = 1 WHERE id = :id")
    suspend fun markConsumed(id: Long)

    @Query("UPDATE clipboard_entries SET consumed = 1 WHERE imagePath = :imagePath")
    suspend fun markConsumedByImagePath(imagePath: String)

    @Query("UPDATE clipboard_entries SET timestamp = :timestamp, consumed = 0 WHERE id = :id")
    suspend fun updateTimestampAndUnconsume(id: Long, timestamp: Long)

    @Query("DELETE FROM clipboard_entries")
    suspend fun deleteAll()

    @Transaction
    suspend fun upsertAndTrim(text: String, now: Long, maxItems: Int, consumed: Boolean = false) {
        val existing = findByText(text)
        if (existing != null) {
            if (!consumed) {
                updateTimestampAndUnconsume(existing.id, now)
            } else {
                updateTimestamp(existing.id, now)
            }
        } else {
            insert(ClipboardEntry(text = text, timestamp = now, consumed = consumed))
            val unpinned = countUnpinned()
            if (unpinned > maxItems) {
                trimUnpinned(unpinned - maxItems)
            }
        }
    }

    /**
     * 记录一条图片剪贴板条目（按内容哈希路径去重），返回是否产生了剪贴板记录。
     *
     * 返回 false 表示该图已作为快捷发送条目存在（`isQuickSend = 1`）而不应再进剪贴板历史。
     * 场景：`commitImage` 把图片写入系统剪贴板供宿主粘贴后会触发剪贴板监听回声，
     * 若源剪贴板条目已被用户删除或 6 小时过期，回声会重复插入同内容条目，
     * 使「已发送的图」重新出现在候选栏/剪贴板历史。调用方应据此跳过事件广播。
     */
    @Transaction
    suspend fun upsertImageAndTrim(
        imagePath: String,
        mimeType: String,
        now: Long,
        maxItems: Int,
        consumed: Boolean = false
    ): Boolean {
        val existing = findByImagePath(imagePath)
        if (existing != null) {
            if (!consumed) {
                updateTimestampAndUnconsume(existing.id, now)
            } else {
                updateTimestamp(existing.id, now)
            }
            return true
        }
        if (findQuickSendByImagePath(imagePath) != null) return false
        insert(
            ClipboardEntry(
                text = "[图片]",
                imagePath = imagePath,
                mimeType = mimeType,
                timestamp = now,
                consumed = consumed
            )
        )
        val unpinned = countUnpinned()
        if (unpinned > maxItems) {
            trimUnpinned(unpinned - maxItems)
        }
        return true
    }

    @Transaction
    suspend fun addQuickSend(sourceId: Long, now: Long, maxQuickSend: Int) {
        val source = findById(sourceId) ?: return
        if (source.imagePath.isNotEmpty()) {
            val existing = findQuickSendByImagePath(source.imagePath)
            if (existing != null) {
                updateTimestamp(existing.id, now)
            } else {
                insert(
                    ClipboardEntry(
                        text = source.text.ifEmpty { "[图片]" },
                        imagePath = source.imagePath,
                        mimeType = source.mimeType,
                        timestamp = now,
                        isPinned = true,
                        isQuickSend = true
                    )
                )
            }
        } else {
            val existing = findQuickSendByText(source.text)
            if (existing != null) {
                updateTimestamp(existing.id, now)
            } else {
                insert(
                    ClipboardEntry(
                        text = source.text,
                        timestamp = now,
                        isPinned = true,
                        isQuickSend = true
                    )
                )
            }
        }
        val count = countQuickSend()
        if (count > maxQuickSend) {
            trimQuickSend(count - maxQuickSend)
        }
    }

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE isQuickSend = 1")
    suspend fun countQuickSend(): Int

    @Transaction
    suspend fun insertQuickSend(text: String, code: String, now: Long, maxQuickSend: Int) {
        val existing = findQuickSendByText(text)
        if (existing != null) {
            updateQuickSendItem(existing.id, text, code, now)
        } else {
            insert(
                ClipboardEntry(
                    text = text,
                    code = code,
                    timestamp = now,
                    isPinned = true,
                    isQuickSend = true
                )
            )
        }
        val count = countQuickSend()
        if (count > maxQuickSend) {
            trimQuickSend(count - maxQuickSend)
        }
    }
}
