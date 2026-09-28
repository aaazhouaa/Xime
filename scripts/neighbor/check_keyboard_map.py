#!/usr/bin/env python3
"""校验 librime corrector.cc 里 keyboard_map。

对「实际写入的 C++ 文件」解析（不信任生成脚本输出），报告三件事：
  A. 字母键内部双向对称性  —— 本次改动的正确性红线，必须为 0 违规
  B. 字母键与触屏坐标规则是否逐条一致（row1 偏移 0.5 / row2 偏移 1.5，欧氏距离<1.5）
  C. 相对「上游基线」我实际改了哪些邻接关系（含引入的非对称）

失败条件仅 A、B（字母区）；非字母键（数字/标点）上游本就全表不对称，
其不对称只作信息输出，不作为失败。

用法: python3 check_keyboard_map.py [corrector.cc 路径]
退出码 0=通过；1=字母区违规。
"""
import math
import re
import sys

DEFAULT = "../../app/src/main/jni/librime/src/rime/dict/corrector.cc"
LETTERS = set("qwertyuiopasdfghjklzxcvbnm")

# 上游 librime 基线（仅字母行；用于精确隔离本次改动）
# 注意：上游 p 与 l 分别还枚举了标点 [ 与 ;（去手滑纠错改造前为 {o,[} / {k,;}），
# 二者必须写入基线，否则会把「本次引入的 p↔[ 、l↔; 不对称」误判为上游原状。
UPSTREAM_SPEC = ("q w|w q e|e w r|r e t|t r y|y t u|u y i|i u o|o i p|p o [|"
                 "a s|s a d|d s f|f d g|g f h|h g j|j h k|k j l|l k ;|"
                 "z x|x z c|c x v|v c b|b v n|n b m|m n")


def coords():
    c = {}
    for i, ch in enumerate("qwertyuiop"):
        c[ch] = (i, 0.0)
    for i, ch in enumerate("asdfghjkl"):
        c[ch] = (0.5 + i, 1.0)
    for i, ch in enumerate("zxcvbnm"):
        c[ch] = (1.5 + i, 2.0)
    return c


def parse(path):
    src = open(path, encoding="utf-8", errors="ignore").read()
    m = re.search(r"keyboard_map\s*=\s*\{(.*?)\n\};", src, re.S)
    if not m:
        raise SystemExit(f"未找到 keyboard_map 初始化块: {path}")
    table, dup = {}, []
    for k, inner in re.findall(r"\{'(\\\\.|[^'])'\s*,\s*\{([^}]*)\}\}", m.group(1)):
        key = "\\" if k == "\\\\" else k
        if key in table:
            dup.append(key)
        items = re.findall(r"'(\\\\.|[^'])'", inner)
        table[key] = set("\\" if x == "\\\\" else x for x in items)
    return table, dup


def upstream_letters():
    m = {}
    for part in UPSTREAM_SPEC.split("|"):
        k, _, rest = part.partition(" ")
        m[k] = set(rest.split()) if rest else set()
    return m


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    table, dup = parse(path)
    fails = []

    print(f"文件: {path}")
    print(f"解析键数: {len(table)}")

    selfref = sorted(a for a in table if a in table[a])
    dup_issues = ([f"重复键 {dup}"] if dup else []) + ([f"自引用 {selfref}"] if selfref else [])
    fails += dup_issues
    print(f"重复键: {dup or '无 ✓'}")
    print(f"自引用: {selfref or '无 ✓'}")

    # A. 字母间对称性
    bad = sorted((a, b) for a in LETTERS for b in table.get(a, set()) & LETTERS
                 if a not in table.get(b, set()))
    if bad:
        fails.append(f"字母间不对称 {bad}")
    print(f"\n[A] 字母间双向对称性: {'违规 ' + str(bad) if bad else '通过 ✓'}")

    # B. 与坐标规则一致性
    C = coords()
    mismatch = []
    for a in sorted(LETTERS):
        expect = {c for c in LETTERS if c != a
                  and math.hypot(C[a][0] - C[c][0], C[a][1] - C[c][1]) < 1.5}
        got = table.get(a, set()) & LETTERS
        if expect != got:
            mismatch.append((a, "缺" + str(sorted(expect - got)), "多" + str(sorted(got - expect))))
    if mismatch:
        fails.append(f"字母与坐标规则不一致 {mismatch}")
    print(f"[B] 字母与坐标规则一致: {'违规 ' + str(mismatch) if mismatch else '通过 ✓'}")

    # C. 相对上游基线：实际改动
    up = upstream_letters()
    added, removed = [], []
    for a in sorted(LETTERS):
        got, base = table.get(a, set()) & LETTERS, up.get(a, set())
        for b in sorted(got - base):
            added.append(f"{a}→{b}")
        for b in sorted(base - got):
            removed.append(f"{a}→{b}")
    print(f"[C] 相对上游基线: 新增 {len(added)} 条, 移除 {len(removed)} 条")
    print(f"    新增示例: {', '.join(added[:8])} …" if added else "    无新增")
    print(f"    移除示例: {', '.join(removed[:8])} …" if removed else "    无移除")

    # 非字母键信息（不作为失败）：区分「上游原状」与「本次引入」
    # 上游完整表 = 当前的非字母键（本次未改） + 上游字母键
    up_full = {k: set(v) for k, v in table.items() if k not in LETTERS}
    up_full.update({k: set(v) for k, v in up.items()})

    def cross_asym(t):
        return {(a, b) for a in t for b in t[a]
                if a not in t.get(b, set()) and (a in LETTERS) != (b in LETTERS)}

    now, base = cross_asym(table), cross_asym(up_full)
    introduced, fixed = sorted(now - base), sorted(base - now)
    print(f"\n[信息] 字母↔非字母 交叉不对称: 当前 {len(now)} 条"
          f"（上游原状 {len(base)}）")
    print(f"     ⚠ 本次引入: {introduced or '无 ✓'}")
    print(f"     本次消除: {fixed or '无'}")
    print("     说明: 非字母键（数字/标点）未纳入坐标模型，沿用上游原状；"
          "其不对称不影响拼音（这些字符不在 speller/alphabet 内，音节图不经过）。")

    tot = sum(len(table.get(a, set()) & LETTERS) for a in LETTERS)
    print(f"\n字母有向边={tot} 平均扇出={tot / len(LETTERS):.2f}")

    if fails:
        print("\n结果: 字母区存在违规")
        for f in fails:
            print("  -", f)
        return 1
    print("\n结果: 字母区全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
