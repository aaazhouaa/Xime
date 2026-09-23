package com.kingzcheung.xime.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BottomToastTest {

    private class ToastController(private val scope: TestScope) {
        private var job: kotlinx.coroutines.Job? = null
        private val _message = MutableStateFlow<String?>(null)
        val message = _message.asStateFlow()

        fun showToast(text: String, durationMs: Long = 1500L) {
            _message.value = text
            job?.cancel()
            job = scope.launch {
                delay(durationMs)
                _message.value = null
            }
        }

        fun dismiss() {
            job?.cancel()
            _message.value = null
        }
    }

    @Test
    fun testToastAutoDismissAfter1500Ms() = runTest {
        val controller = ToastController(this)
        controller.showToast("该应用不支持将图片粘贴到此处", 1500L)
        assertEquals("该应用不支持将图片粘贴到此处", controller.message.value)

        // 1000ms 后仍然显示
        advanceTimeBy(1000L)
        assertEquals("该应用不支持将图片粘贴到此处", controller.message.value)

        // 达到 1500ms 后自动消失
        advanceTimeBy(501L)
        assertNull(controller.message.value)
    }

    @Test
    fun testRepeatedTriggerRefreshesDuration() = runTest {
        val controller = ToastController(this)
        controller.showToast("该应用不支持将图片粘贴到此处", 1500L)

        // 1000ms 后再次触发
        advanceTimeBy(1000L)
        assertEquals("该应用不支持将图片粘贴到此处", controller.message.value)
        controller.showToast("该应用不支持将图片粘贴到此处", 1500L)

        // 又过了 1000ms（距第一次 2000ms，距第二次 1000ms）：因时间被刷新，仍然显示
        advanceTimeBy(1000L)
        assertEquals("该应用不支持将图片粘贴到此处", controller.message.value)

        // 再过 500ms（距第二次 1500ms）：消失
        advanceTimeBy(501L)
        assertNull(controller.message.value)
    }

    @Test
    fun testImmediateDismissOnClick() = runTest {
        val controller = ToastController(this)
        controller.showToast("该应用不支持将图片粘贴到此处", 1500L)
        assertEquals("该应用不支持将图片粘贴到此处", controller.message.value)

        // 任意点击立即触发 dismiss
        controller.dismiss()
        assertNull(controller.message.value)
    }
}
