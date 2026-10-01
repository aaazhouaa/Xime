package com.kingzcheung.xime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // applicationId 带版本后缀（本地 / .baishuang / .wanxiang），
        // 不写死具体值，改为与 BuildConfig.APPLICATION_ID 对齐，换后缀无需改测试。
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }
}