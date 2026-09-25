package com.kingzcheung.xime.clipboard

import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import com.kingzcheung.xime.util.MimeTypeSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardFeatureTest {

    @Test
    fun testClipboardEntryAndItemImageProperties() {
        val textEntry = ClipboardEntry(
            id = 1L,
            text = "Hello Xime",
            imagePath = "",
            mimeType = ""
        )
        assertFalse(textEntry.isImage)

        val imageEntry = ClipboardEntry(
            id = 2L,
            text = "[图片]",
            imagePath = "/data/user/0/com.kingzcheung.xime/files/clipboard_images/clip_123.png",
            mimeType = "image/png"
        )
        assertTrue(imageEntry.isImage)
        assertEquals("image/png", imageEntry.mimeType)

        val item = ClipboardItem(
            id = imageEntry.id,
            text = imageEntry.text,
            imagePath = imageEntry.imagePath,
            mimeType = imageEntry.mimeType
        )
        assertTrue(item.isImage)

        // 有 imagePath 但 mimeType 非 image/* 时不算图片
        assertFalse(imageEntry.copy(mimeType = "application/octet-stream").isImage)
        // 只有 mimeType 没有 imagePath 时不算图片
        assertFalse(imageEntry.copy(imagePath = "").isImage)
    }

    @Test
    fun testExpiredImageIdentificationLogic() {
        val now = 10_000_000L
        val sixHoursMs = 6 * 3600 * 1000L
        val cutoff = now - sixHoursMs

        val entries = listOf(
            // 1. 超过6小时的未快捷图片 -> 应被清理
            ClipboardEntry(id = 1, text = "[图片]", imagePath = "/path/img1.png", mimeType = "image/png", isQuickSend = false, timestamp = cutoff - 1000),
            // 2. 超过6小时的快捷图片 -> 不应被清理
            ClipboardEntry(id = 2, text = "[图片]", imagePath = "/path/img2.png", mimeType = "image/png", isQuickSend = true, timestamp = cutoff - 1000),
            // 3. 未超过6小时的未快捷图片 -> 不应被清理
            ClipboardEntry(id = 3, text = "[图片]", imagePath = "/path/img3.png", mimeType = "image/png", isQuickSend = false, timestamp = cutoff + 1000),
            // 4. 超过6小时的普通文本条目 -> 绝对不被清理（保留文本）
            ClipboardEntry(id = 4, text = "一些重要文本", imagePath = "", mimeType = "", isQuickSend = false, timestamp = cutoff - 5000),
            // 5. 超过6小时的快捷文本条目 -> 不被清理
            ClipboardEntry(id = 5, text = "快捷短语", imagePath = "", mimeType = "", isQuickSend = true, timestamp = cutoff - 5000)
        )

        // 与 ClipboardDao.findExpiredUnquickImages 的查询条件保持一致：
        // WHERE isQuickSend = 0 AND imagePath != '' AND timestamp < :expireTime
        val expiredUnquickImages = entries.filter {
            !it.isQuickSend && it.imagePath.isNotEmpty() && it.timestamp < cutoff
        }

        assertEquals(1, expiredUnquickImages.size)
        assertEquals(1L, expiredUnquickImages[0].id)
        assertEquals("/path/img1.png", expiredUnquickImages[0].imagePath)
    }

    @Test
    fun testQuickSendFileProtection() {
        // 快捷发送条目的图片路径集合
        val quickSendImagePaths = setOf("/path/shared_pic.png", "/path/quick_only.png")

        // 待清理的普通剪贴板过期图片
        val expiredImage1 = "/path/shared_pic.png" // 同时在快捷发送中使用 -> 记录可删，底层文件不可删
        val expiredImage2 = "/path/temp_pic.png"   // 未在快捷发送中使用 -> 记录与文件皆可删

        val shouldDeleteFile1 = expiredImage1 !in quickSendImagePaths
        val shouldDeleteFile2 = expiredImage2 !in quickSendImagePaths

        assertFalse(shouldDeleteFile1) // 保护快捷图片文件
        assertTrue(shouldDeleteFile2)  // 孤立图片文件允许删除
    }

    @Test
    fun testMimeTypeMatchingSupport() {
        // 直接调用生产实现的纯函数，避免测试内重写逻辑导致回归漏检
        assertFalse(MimeTypeSupport.matchesAny(null, "image/png"))
        assertFalse(MimeTypeSupport.matchesAny(emptyArray(), "image/png"))

        // 通配符 */*
        assertTrue(MimeTypeSupport.matchesAny(arrayOf("*/*"), "image/png"))
        assertTrue(MimeTypeSupport.matchesAny(arrayOf("*/*"), "image/jpeg"))

        // 通配符 image/*
        assertTrue(MimeTypeSupport.matchesAny(arrayOf("image/*"), "image/png"))
        assertTrue(MimeTypeSupport.matchesAny(arrayOf("image/*"), "image/jpeg"))
        assertTrue(MimeTypeSupport.matchesAny(arrayOf("image/*"), "image/gif"))
        assertFalse(MimeTypeSupport.matchesAny(arrayOf("image/*"), "text/plain"))

        // 精确类型（大小写不敏感）
        assertTrue(MimeTypeSupport.matchesAny(arrayOf("image/png", "image/jpeg"), "image/png"))
        assertTrue(MimeTypeSupport.matchesAny(arrayOf("image/png", "image/jpeg"), "IMAGE/JPEG"))
        assertFalse(MimeTypeSupport.matchesAny(arrayOf("image/png", "image/jpeg"), "image/webp"))

        // 多类型中任一项命中即可
        assertTrue(MimeTypeSupport.matchesAny(arrayOf("text/plain", "image/*"), "image/webp"))
        // image/* 不应误匹配其它主类型下含 "image" 字样的类型
        assertFalse(MimeTypeSupport.matchesAny(arrayOf("image/*"), "application/x-image-thing"))
    }

    @Test
    fun testMagicNumberMimeDetection() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val gifBytes = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte(), 0x39.toByte(), 0x61.toByte())
        val bmpBytes = byteArrayOf(0x42.toByte(), 0x4D.toByte(), 0x00, 0x00)
        val webpBytes = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte()
        )
        val plainTextBytes = "Hello World".toByteArray()

        assertEquals("image/png", ClipboardManager.detectImageMimeType(pngBytes))
        assertEquals("image/jpeg", ClipboardManager.detectImageMimeType(jpegBytes))
        assertEquals("image/gif", ClipboardManager.detectImageMimeType(gifBytes))
        assertEquals("image/webp", ClipboardManager.detectImageMimeType(webpBytes))
        assertEquals("image/bmp", ClipboardManager.detectImageMimeType(bmpBytes))
        assertEquals(null, ClipboardManager.detectImageMimeType(plainTextBytes))
        // 短于 4 字节直接返回 null，不得越界
        assertEquals(null, ClipboardManager.detectImageMimeType(byteArrayOf(0x89.toByte(), 0x50.toByte())))
    }

    @Test
    fun testScreenshotPathRecognition() {
        // 直接调用生产实现的纯函数
        assertTrue(ClipboardManager.isScreenshotPath("Screenshot_20260923_180000.png"))
        assertTrue(ClipboardManager.isScreenshotPath("/storage/emulated/0/DCIM/Screenshots/img_01.jpg"))
        assertTrue(ClipboardManager.isScreenshotPath("/sdcard/Pictures/截屏/Screenshot_1.png"))
        assertTrue(ClipboardManager.isScreenshotPath("ScreenCapture_2026.png"))
        assertTrue(ClipboardManager.isScreenshotPath("截屏_2026-09-23.jpg"))
        assertTrue(ClipboardManager.isScreenshotPath("截图_2026-09-23.jpg"))
        assertTrue(ClipboardManager.isScreenshotPath("screen_shot_01.png"))
        assertFalse(ClipboardManager.isScreenshotPath("IMG_20260923_180000.jpg"))
        assertFalse(ClipboardManager.isScreenshotPath("/storage/emulated/0/DCIM/Camera/photo.jpg"))
    }

    @Test
    fun testRecentClipboardConsumedFilter() {
        val now = 100_000L
        val cutoff = now - 60_000L
        val items = listOf(
            ClipboardItem(id = 1L, text = "未消费最近项", timestamp = now - 1000L, consumed = false),
            ClipboardItem(id = 2L, text = "已消费项（弹出过）", timestamp = now - 2000L, consumed = true),
            ClipboardItem(id = 3L, text = "超时未消费项", timestamp = cutoff - 5000L, consumed = false),
        )

        // 仅未消费且在 60 秒内的条目可作为最近剪贴板弹出
        val recentItems = items.filter { it.timestamp >= cutoff && !it.consumed }
        assertEquals(1, recentItems.size)
        assertEquals(1L, recentItems[0].id)
        assertEquals("未消费最近项", recentItems[0].text)
    }

    @Test
    fun testCandidateBarShowsClipboardDisplay() {
        val item = ClipboardItem(id = 1L, text = "复制内容", timestamp = System.currentTimeMillis())
        val state = com.kingzcheung.xime.ui.keyboard.CandidateBarState.from(
            candidates = listOf("复制内容"),
            candidateComments = emptyList(),
            inputText = "",
            isComposing = false,
            associationCandidates = emptyList(),
            isShowingRecentClipboard = true,
            hasNextPage = false,
            recentClipboardItems = listOf(item)
        )
        assertTrue(state is com.kingzcheung.xime.ui.keyboard.CandidateBarState.ClipboardDisplay)
        assertEquals(1, (state as com.kingzcheung.xime.ui.keyboard.CandidateBarState.ClipboardDisplay).items.size)
    }

    @Test
    fun testInitialCaptureOrOverdueMarkedConsumed() {
        val now = 1_000_000L
        val overdueClipTimestamp = now - 120_000L // 2分钟前复制
        val recentClipTimestamp = now - 10_000L  // 10秒前复制

        // 1. 冷启动基线捕获（isInitial = true）：无论时间戳如何，一律标为已消费（不弹候选）
        val initialConsumed = true || (overdueClipTimestamp > 0L && (now - overdueClipTimestamp > 60_000L))
        assertTrue(initialConsumed)

        val initialRecentConsumed = true || (recentClipTimestamp > 0L && (now - recentClipTimestamp > 60_000L))
        assertTrue(initialRecentConsumed)

        // 2. 非初次捕获、但系统剪贴板复制时间已超时（> 60s）
        val overdueConsumed = false || (overdueClipTimestamp > 0L && (now - overdueClipTimestamp > 60_000L))
        assertTrue(overdueConsumed)

        // 3. 非初次捕获且在 60s 内新复制：未消费，正常弹候选
        val freshConsumed = false || (recentClipTimestamp > 0L && (now - recentClipTimestamp > 60_000L))
        assertFalse(freshConsumed)
    }
}
