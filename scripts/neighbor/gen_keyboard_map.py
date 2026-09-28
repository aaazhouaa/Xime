#!/usr/bin/env python3
"""按触屏 QWERTY 真实交错坐标 + 距离阈值生成 corrector.cc 的 keyboard_map。

坐标与阈值取自本项目历史实现 handslide_filter.cc（commit 6d8e7aca）：
  row0 偏移 0, row1 偏移 0.5, row2 偏移 1.5（键宽为单位）
  distance_threshold = 1.5
该组合已在真机调过，避免自创阈值。
"""
import math

COORDS = {}
for i, c in enumerate("qwertyuiop"):
    COORDS[c] = (float(i), 0.0)
for i, c in enumerate("asdfghjkl"):
    COORDS[c] = (0.5 + i, 1.0)
for i, c in enumerate("zxcvbnm"):
    COORDS[c] = (1.5 + i, 2.0)

THRESHOLD = 1.5

def dist(a, b):
    ax, ay = COORDS[a]; bx, by = COORDS[b]
    return math.hypot(ax - bx, ay - by)

def build():
    m = {}
    for a in COORDS:
        m[a] = sorted(c for c in COORDS
                      if c != a and dist(a, c) < THRESHOLD)
    return m

def check_symmetry(m):
    bad = []
    for a, lst in m.items():
        for b in lst:
            if a not in m[b]:
                bad.append((a, b))
    return bad

if __name__ == "__main__":
    m = build()
    bad = check_symmetry(m)
    print("# 坐标:", {k: v for k, v in sorted(COORDS.items())})
    print("# 阈值:", THRESHOLD)
    print("# 对称性违规:", bad if bad else "无")
    print()
    for k in sorted(m):
        inner = ", ".join("'" + x + "'" for x in m[k])
        print("    {'" + k + "', {" + inner + "}},")
    edges = sum(len(v) for v in m.values())
    print()
    print(f"# 键数={len(m)} 有向边={edges} 平均扇出={edges/len(m):.2f}")
