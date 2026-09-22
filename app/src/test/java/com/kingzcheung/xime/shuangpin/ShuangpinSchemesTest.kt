package com.kingzcheung.xime.shuangpin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuangpinSchemesTest {

    @Test
    fun `检测各双拼方案`() {
        assertEquals("flypy", ShuangpinSchemes.detect("double_pinyin_flypy")?.id)
        assertEquals("tongyong", ShuangpinSchemes.detect("double_pinyin")?.id)
        assertEquals("ziranma", ShuangpinSchemes.detect("double_pinyin_zrm")?.id)
        assertEquals("ziguang", ShuangpinSchemes.detect("double_pinyin_ziguang")?.id)
        assertEquals("mspy", ShuangpinSchemes.detect("double_pinyin_mspy")?.id)
        assertEquals("abc", ShuangpinSchemes.detect("double_pinyin_abc")?.id)
        assertEquals("sogou", ShuangpinSchemes.detect("double_pinyin_sogou")?.id)
        assertEquals("jiajia", ShuangpinSchemes.detect("double_pinyin_jiajia")?.id)
        assertNull(ShuangpinSchemes.detect("wubi86"))
        assertNull(ShuangpinSchemes.detect(""))
        assertFalse(ShuangpinSchemes.isShuangpinSchema("wubi86"))
        assertTrue(ShuangpinSchemes.isShuangpinSchema("double_pinyin_mspy"))
    }

    @Test
    fun `自然码双拼对应关系`() {
        val ziranma = ShuangpinSchemes.detect("double_pinyin_zrm")!!
        assertEquals("ziranma", ziranma.id)
        // 声母：zh→v、ch→i、sh→u
        assertEquals("zh", ziranma.shengmuForKey("v"))
        assertEquals("ch", ziranma.shengmuForKey("i"))
        assertEquals("sh", ziranma.shengmuForKey("u"))
        // 韵母：w→en、t→eng、m→un、j→er/iu
        assertEquals("en", ziranma.keyLabel("w", showYunmu = true))
        assertEquals("eng", ziranma.keyLabel("t", showYunmu = true))
        assertEquals("un", ziranma.keyLabel("m", showYunmu = true))
        assertEquals("er\niu", ziranma.keyLabel("j", showYunmu = true))
        // v→ui|v
        assertEquals("ui\nv", ziranma.keyLabel("v", showYunmu = true))
    }

    @Test
    fun `紫光双拼对应关系`() {
        val ziguang = ShuangpinSchemes.detect("double_pinyin_ziguang")!!
        assertEquals("ziguang", ziguang.id)
        // 声母：zh→u、ch→a、sh→i
        assertEquals("zh", ziguang.shengmuForKey("u"))
        assertEquals("ch", ziguang.shengmuForKey("a"))
        assertEquals("sh", ziguang.shengmuForKey("i"))
        // c 键无韵母
        assertTrue(ziguang.yunmuListForKey("c").isEmpty())
        // v→v（无 ui）
        assertEquals("v", ziguang.keyLabel("v", showYunmu = true))
        // 三韵母键 n→ue/ve/ui 用括号合并为两行
        assertEquals("u(v)e\nui", ziguang.keyLabel("n", showYunmu = true))
    }

    @Test
    fun `微软双拼对应关系`() {
        val mspy = ShuangpinSchemes.detect("double_pinyin_mspy")!!
        assertEquals("zh", mspy.shengmuForKey("v"))
        assertEquals("ch", mspy.shengmuForKey("i"))
        assertEquals("sh", mspy.shengmuForKey("u"))
        assertEquals("iu", mspy.keyLabel("q", showYunmu = true))
        assertEquals("er\nuan", mspy.keyLabel("r", showYunmu = true))
        assertEquals("ong\niong", mspy.keyLabel("s", showYunmu = true))
    }

    @Test
    fun `智能ABC双拼对应关系`() {
        val abc = ShuangpinSchemes.detect("double_pinyin_abc")!!
        assertEquals("zh", abc.shengmuForKey("a"))
        assertEquals("ch", abc.shengmuForKey("e"))
        assertEquals("sh", abc.shengmuForKey("v"))
        assertEquals("ei", abc.keyLabel("q", showYunmu = true))
        assertEquals("ing", abc.keyLabel("y", showYunmu = true))
        // 三韵母键 m→ue/ve/ui 用括号合并为两行
        assertEquals("u(v)e\nui", abc.keyLabel("m", showYunmu = true))
    }

    @Test
    fun `加加双拼对应关系`() {
        val jiajia = ShuangpinSchemes.detect("double_pinyin_jiajia")!!
        assertEquals("zh", jiajia.shengmuForKey("v"))
        assertEquals("ch", jiajia.shengmuForKey("u"))
        assertEquals("sh", jiajia.shengmuForKey("i"))
        assertEquals("iu", jiajia.keyLabel("n", showYunmu = true))
        assertEquals("er\ning", jiajia.keyLabel("q", showYunmu = true))
        // 三韵母键 x→ue/ve/uai 用括号合并为两行
        assertEquals("u(v)e\nuai", jiajia.keyLabel("x", showYunmu = true))
    }

    @Test
    fun `通用双拼对应关系`() {
        val tongyong = ShuangpinSchemes.detect("double_pinyin")!!
        assertNotNull(tongyong)
        assertEquals("zh", tongyong.shengmuForKey("v"))
        assertEquals("ch", tongyong.shengmuForKey("i"))
        assertEquals("sh", tongyong.shengmuForKey("u"))
        assertEquals("iu", tongyong.keyLabel("q", showYunmu = true))
        assertEquals("ing\nuai", tongyong.keyLabel("y", showYunmu = true))
    }

    @Test
    fun `气泡分解串下标映射到原始编码下标`() {
        // flypy: v→zh, c→ao，"vc" 分解为单段 "zh + ao"
        val flypy = ShuangpinSchemes.FLYPY
        val map = ShuangpinSchemes.buildShuangpinBubbleIndexMap("vc", flypy.decompose("vc"))
        val text = flypy.decompose("vc").joinToString("\u3000")
        assertEquals(text.length, map.size)
        // "zh" 属第 1 键（下标 0）
        assertEquals(0, map[0])
        assertEquals(0, map[1])
        // "+" 与左侧空格仍归第 1 键
        assertEquals(0, map.indexOf(0))
        // "ao" 在 " + " 之后，属第 2 键（下标 1）
        assertEquals(1, map[text.length - 1])
        assertEquals(1, map[text.length - 2])
    }

    @Test
    fun `气泡分解串多段下标映射`() {
        // "nihao" → flypy: ni → "n + i"；hao → "h + ao"；末尾单键 o → "o"
        val flypy = ShuangpinSchemes.FLYPY
        val segs = flypy.decompose("nihao")
        val text = segs.joinToString("\u3000")
        val map = ShuangpinSchemes.buildShuangpinBubbleIndexMap("nihao", segs)
        assertEquals(text.length, map.size)
        // 第 2 段（"h + ao"）的起始字符应映射到编码下标 2
        val secondSegStart = text.indexOf("h")
        assertTrue(secondSegStart > 0)
        assertEquals(2, map[secondSegStart])
        // 第 2 段的尾字符（韵母 ao 末尾）属第 4 个键（下标 3）
        val aoEnd = text.indexOf("h") + segs[1].length - 1
        assertEquals(3, map[aoEnd])
        // 末段为奇数单键 o，属第 5 个键（下标 4）
        assertEquals(4, map[text.length - 1])
        // 全部分布在 [0, keys.length] 内
        assertTrue(map.all { it in 0..4 })
    }

    @Test
    fun `气泡分解串奇数键末尾段映射`() {
        // "v" 单键：decompose → ["zh"]，全部字符映射到下标 0
        val flypy = ShuangpinSchemes.FLYPY
        val segs = flypy.decompose("v")
        val map = ShuangpinSchemes.buildShuangpinBubbleIndexMap("v", segs)
        assertEquals(segs.joinToString("\u3000").length, map.size)
        assertTrue(map.all { it == 0 })
    }

    @Test
    fun `气泡分解串空输入返回空数组`() {
        assertTrue(ShuangpinSchemes.buildShuangpinBubbleIndexMap("", emptyList()).isEmpty())
        assertTrue(ShuangpinSchemes.buildShuangpinBubbleIndexMap("abc", emptyList()).isEmpty())
    }
}
