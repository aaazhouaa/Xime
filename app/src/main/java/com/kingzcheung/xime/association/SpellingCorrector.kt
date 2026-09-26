package com.kingzcheung.xime.association

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 英文拼写纠错。
 *
 * 背景：Xime 英文模式下已有 TrieAssociationEngine 的前缀补全（hel → hello），
 * 但输入拼错时（helo）不匹配任何词前缀，补全返回空。此处补齐"拼错 → 正确拼写"这一半。
 *
 * 参数与 fcitx5 SpellCustomDict::getDistance（src/modules/spell/spell-custom-dict.cpp）同源：
 * 替换/插入/删除权重均为 3，编辑预算 maxDiff = len/3、删除预算 maxRemove = (len-2)/3。
 * 实现上用标准受限编辑距离 DP 替代上游的双指针贪心，语义更易推理与测试。
 *
 * 触发策略（与上游的有意偏离）：上游把纠错建议混入候选，导致 cat → bat/can/cap 噪声。
 * 此处仅在"前缀补全无结果"时才纠错——前缀能补全的词不进入本模块，故 cat 不会推 bat。
 */
object SpellingCorrector {

    /** 单次纠错建议上限，避免短词刷屏。 */
    const val CANDIDATE_LIMIT = 5

    /** 每个编辑操作的权重（与 fcitx5 一致，仅决定返回的距离量级）。 */
    private const val OP_WEIGHT = 3

    /** 供编码 (总操作数, remove 数) 用的基数，需大于 remove 数可能的最大和。 */
    private const val REMOVE_BASE = 1000

    /**
     * 对输入词给出纠错建议。
     *
     * 前置条件（任一不满足即返回空）：
     * - 输入长度不足以构成编辑预算（len < 3）；
     * - 词表引擎未初始化；
     * - 输入存在前缀补全结果（此时属于补全场景，不纠错）。
     *
     * @param input 英文输入串（原始大小写，内部转小写比较）
     * @return 按"编辑距离升序 → 词频降序"排序的建议词，最多 [CANDIDATE_LIMIT] 个
     */
    suspend fun suggest(input: String, limit: Int = CANDIDATE_LIMIT): List<String> {
        val word = input.trim().lowercase()
        if (word.length < 3) return emptyList()

        val engine = TrieAssociationEngine.getInstance()
        if (!engine.isInitialized()) return emptyList()
        if (engine.predict(word, 1).isNotEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            // 纠错建议比前缀补全克制：即使调用方传入更大的 limit（如 20），也封顶 CANDIDATE_LIMIT
            rank(word, engine.allWords(), minOf(limit, CANDIDATE_LIMIT))
        }
    }

    /**
     * 在候选词表中排序纠错结果（纯函数，便于单测）。
     *
     * @param word       已小写化的输入词
     * @param candidates 词表 (词, 词频序)，序小者更常见
     * @param limit      返回上限
     */
    internal fun rank(
        word: String,
        candidates: List<Pair<String, Int>>,
        limit: Int,
    ): List<String> {
        val maxDiff = word.length / 3
        if (maxDiff <= 0) return emptyList()
        val maxRemove = (word.length - 2) / 3

        val scored = ArrayList<Triple<String, Int, Int>>()
        for ((cand, freq) in candidates) {
            if (cand == word) continue
            // 输入是候选词的前缀 → 属于补全场景，交由 Trie 前缀补全，不在此处当作纠错
            if (cand.startsWith(word)) continue
            val dist = distance(word, cand, maxDiff, maxRemove)
            if (dist >= 0) scored.add(Triple(cand, dist, freq))
        }

        return scored
            .sortedWith(
                compareBy<Triple<String, Int, Int>> { it.second }.thenBy { it.third }
            )
            .take(limit)
            .map { it.first }
    }

    /**
     * 受限编辑距离：把 [word] 改成 [dict] 所需操作数的加权值。
     *
     * 返回 -1 表示超出预算（不应作为纠错候选）。预算内返回 `操作数 * OP_WEIGHT`。
     *
     * 三种操作：
     * - 替换（replace）：word 与 dict 当前字符不同，各前进一位；
     * - 删除（remove）：跳过 word 的字符（word 比 dict 长），受 [maxRemove] 约束；
     * - 插入（insert）：跳过 dict 的字符（word 比 dict 短）。
     *
     * 采用 (总操作数, remove 数) 的字典序最小化——即先取操作最少者，
     * 同操作数下取删除更少者，避免为凑出词表词而大量删字。
     */
    internal fun distance(word: String, dict: String, maxDiff: Int, maxRemove: Int): Int {
        // 编辑距离不小于长度差，长度差已超预算则无需计算
        if (abs(word.length - dict.length) > maxDiff) return -1

        val n = word.length
        val m = dict.length
        // 编码 (总操作数, remove 数) 为单值：基数远大于 remove 可能的最大和，加法不产生进位串扰
        var prev = IntArray(m + 1) { it * REMOVE_BASE }        // 首行：全插入，无删除
        var cur = IntArray(m + 1)

        for (i in 1..n) {
            cur[0] = i * REMOVE_BASE + i                      // 首列：全删除
            for (j in 1..m) {
                cur[j] = if (word[i - 1] == dict[j - 1]) {
                    prev[j - 1]
                } else {
                    minOf(
                        prev[j - 1] + REMOVE_BASE,            // 替换
                        prev[j] + REMOVE_BASE + 1,            // 删除 word 字符
                        cur[j - 1] + REMOVE_BASE,             // 插入 dict 字符
                    )
                }
            }
            val swap = prev
            prev = cur
            cur = swap
        }

        val code = prev[m]
        val total = code / REMOVE_BASE
        val removes = code % REMOVE_BASE
        if (total > maxDiff || removes > maxRemove) return -1
        return total * OP_WEIGHT
    }
}
