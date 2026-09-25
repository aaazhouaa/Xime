package com.kingzcheung.xime.util

/**
 * 拼音分词切分与无损光标映射工具。
 *
 * 在拼音编辑气泡中以带 `'` 分隔符的形式展示（如 shan'shui、vs'ld），
 * 避免字符挤在一起难辨认，同时通过严格的字母数统计实现与原始 rawInput 字符下标的
 * 无损双向映射，绝不产生跳光标或删错音节问题。
 */
object PinyinFormatter {

    // 双拼模式：每 2 个编码字符插入一个分隔符
    fun formatShuangpin(input: String): String {
        if (input.length <= 2) return input
        val sb = StringBuilder()
        for (i in input.indices) {
            if (i > 0 && i % 2 == 0) sb.append('\'')
            sb.append(input[i])
        }
        return sb.toString()
    }

    // 从 preedit 中提取纯拼音音节切分（若 preedit 去除分隔符后完全等同于 input）
    fun tryExtractFromPreedit(preedit: String, input: String): String? {
        if (preedit.isEmpty() || input.isEmpty()) return null
        val parts = preedit.split(' ', '\'', '　').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        // 如果包含非 ASCII 字母（如汉字、特殊符号），放弃从 preedit 提取
        if (parts.any { part -> part.any { it !in 'a'..'z' && it !in 'A'..'Z' } }) return null
        val joined = parts.joinToString("")
        if (joined.equals(input, ignoreCase = true)) {
            return parts.joinToString("'")
        }
        return null
    }

    // 标准汉语拼音全量音节表（小写）
    private val PINYIN_SYLLABLES: Set<String> = setOf(
        "a", "ai", "an", "ang", "ao",
        "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
        "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
        "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
        "e", "ei", "en", "eng", "er",
        "fa", "fan", "fang", "fei", "fen", "feng", "fiao", "fo", "fou", "fu",
        "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
        "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
        "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "lo", "long", "lou", "lu", "luan", "lue", "lun", "luo", "lv", "lve",
        "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
        "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nia", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nun", "nuo", "nv", "nve",
        "o", "ou",
        "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
        "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
        "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "ruan", "rui", "run", "ruo",
        "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
        "ta", "tai", "tan", "tang", "tao", "te", "tei", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
        "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
        "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
        "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
        "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo"
    )

    fun splitPinyin(input: String): String {
        if (input.length <= 1) return input
        val lower = input.lowercase()
        val n = lower.length
        val segments = mutableListOf<String>()
        var i = 0
        while (i < n) {
            var matchedLen = 0
            val maxLen = minOf(6, n - i)
            for (len in maxLen downTo 1) {
                val sub = lower.substring(i, i + len)
                if (PINYIN_SYLLABLES.contains(sub)) {
                    matchedLen = len
                    break
                }
            }
            if (matchedLen > 0) {
                segments.add(input.substring(i, i + matchedLen))
                i += matchedLen
            } else {
                segments.add(input.substring(i, i + 1))
                i++
            }
        }
        return segments.joinToString("'")
    }

    /** 格式化拼音展示：优先使用预切分，否则智能分词 */
    fun formatPinyin(input: String, preedit: String, isShuangpin: Boolean): String {
        if (input.isEmpty()) return ""
        if (isShuangpin) return formatShuangpin(input)
        val extracted = tryExtractFromPreedit(preedit, input)
        if (extracted != null) return extracted
        return splitPinyin(input)
    }

    /** 从带 ' 的分词串下标映射到 rawInput 的字符下标（统计 ' 之前的有效字符个数） */
    fun formattedIndexToInputIndex(formatted: String, formattedIndex: Int): Int {
        if (formatted.isEmpty() || formattedIndex <= 0) return 0
        val clamped = formattedIndex.coerceIn(0, formatted.length)
        var count = 0
        for (i in 0 until clamped) {
            if (formatted[i] != '\'') {
                count++
            }
        }
        return count
    }

    /** 从 rawInput 的光标下标映射到带 ' 的分词串中的字符下标 */
    fun inputIndexToFormattedIndex(formatted: String, inputIndex: Int): Int {
        if (formatted.isEmpty() || inputIndex <= 0) return 0
        var count = 0
        for (i in formatted.indices) {
            if (count == inputIndex) return i
            if (formatted[i] != '\'') {
                count++
            }
        }
        return formatted.length
    }
}
