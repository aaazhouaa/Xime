#!/usr/bin/env python3
"""复现 librime NearSearchCorrector::ToleranceSearch 的 BFS。
对比「上游表+tol5」与「本项目扩表+tol2」的算力与纠错可达性。

严格对照 corrector.cc 队列语义：
  - 每个节点消费输入一个字符（idx 前进一位），distance 不变；
  - 同位置换成邻键则 distance+1，且仅在 distance < tolerance 时展开替换分支。
"""
import math
from collections import deque

CO = {}
for i, c in enumerate("qwertyuiop"): CO[c] = (i, 0.0)
for i, c in enumerate("asdfghjkl"):  CO[c] = (0.5 + i, 1.0)
for i, c in enumerate("zxcvbnm"):    CO[c] = (1.5 + i, 2.0)

UP = {}
for part in ("q w|w q e|e w r|r e t|t r y|y t u|u y i|i u o|o i p|p o|"
             "a s|s a d|d s f|f d g|g f h|h g j|j h k|k j l|l k|"
             "z x|x z c|c x v|v c b|b v n|n b m|m n").split("|"):
    k, _, rest = part.partition(" ")
    UP[k] = rest.split() if rest else []

NEW = {a: [c for c in CO if c != a and math.hypot(CO[a][0]-CO[c][0], CO[a][1]-CO[c][1]) < 1.5]
       for a in CO}

SYL = set("""a ai an ang ao ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong chou chu
chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo da dai dan dang dao
de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo e ei en eng er
fa fan fang fei fen feng fo fou fu ga gai gan gang gao ge gei gen geng gong gou gu gua guai
guan guang gui gun guo ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang
hui hun huo ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun ka kai kan kang
kao ke ken keng kong kou ku kua kuai kuan kuang kui kun kuo la lai lan lang lao le lei leng
li lia lian liang liao lie lin ling liu long lou lu luan lun luo lv lve ma mai man mang mao
me mei men meng mi mian miao mie min ming miu mo mou mu na nai nan nang nao ne nei nen neng
ni nian niang niao nie nin ning niu nong nou nu nuan nuo nv nve o ou pa pai pan pang pao pei
pen peng pi pian piao pie pin ping po pou pu qi qia qian qiang qiao qie qin qing qiong qiu
qu quan que qun ran rang rao re ren reng ri rong rou ru ruan rui run ruo sa sai san sang sao
se sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan shuang
shui shun shuo si song sou su suan sui sun suo ta tai tan tang tao te teng ti tian tiao tie
ting tong tou tu tuan tui tun tuo wa wai wan wang wei wen weng wo wu xi xia xian xiang xiao
xie xin xing xiong xiu xu xuan xue xun ya yan yang yao ye yi yin ying yo yong you yu yuan
yue yun za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhen zheng zhi zhong
zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo""".split())

MAXV = 300000

def bfs(table, key, tol):
    """返回 (展开节点数, [(命中音节, distance)...])"""
    if not key:
        return 0, []
    nodes, hits = 0, []
    q = deque([(0, key, 0)])                      # (idx, cur, dist)
    for s in table.get(key[0], []):
        q.append((0, s + key[1:], 1))
    while q:
        idx, cur, dist = q.popleft()
        nodes += 1
        if nodes > MAXV:
            return nodes, hits
        new_idx = idx + 1
        if cur[:new_idx] in SYL:
            hits.append((cur[:new_idx], dist))
        if new_idx < len(key):
            q.append((new_idx, cur, dist))                    # 原字符
            if dist < tol:
                for s in table.get(key[new_idx], []):
                    q.append((new_idx, cur[:new_idx] + s + cur[new_idx+1:], dist + 1))
    return nodes, hits

def best(hits, target):
    r = [h for h in hits if h[0] == target]
    return min(r, key=lambda x: x[1]) if r else None

print("=== 算力：BFS 展开节点数 ===")
for key in ("zhang", "xian", "ni"):
    print(f"  key={key:6s}", end="")
    for name, t, tol in [("上游 tol5", UP, 5), ("扩表 tol2", NEW, 2),
                         ("扩表 tol5", NEW, 5), ("上游 tol2", UP, 2)]:
        n, _ = bfs(t, key, tol)
        print(f"  {name}={n:<6d}", end="")
    print()

print()
print("=== 纠错可达性（目标音节 vs 实际误按输入）===")
CASES = [
    ("dai", "sai", "d→s 同行"),
    ("dai", "eai", "d→e 斜向(上行)"),
    ("dai", "xai", "d→x 斜向(下行)"),
    ("ni",  "nk",  "i→k 垂直(下1行)"),
    ("de",  "dr",  "e→r 同行"),
    ("de",  "sr",  "d→s,e→r 双误(同行)"),
    ("shi", "sji", "h→j 同行"),
    ("hao", "hso", "a→s 同行"),
    ("hao", "hwo", "a→w 斜向"),
    ("zai", "xai", "z→x 同行"),
    ("dei", "dai", "反向:想打dei误按a(超阈值)"),
]
print(f"  {'目标':5s} {'输入':6s} {'上游 tol5':18s} {'扩表 tol2':18s} {'说明'}")
for target, typed, note in CASES:
    _, hu = bfs(UP,  typed, 5)
    _, hn = bfs(NEW, typed, 2)
    fu, fn = best(hu, target), best(hn, target)
    a = f"纠出 d={fu[1]}" if fu else "未纠出"
    b = f"纠出 d={fn[1]}" if fn else "未纠出"
    print(f"  {target:5s} {typed:6s} {a:18s} {b:18s} {note}")

print()
print("=== 扇出 ===")
print(f"  上游: {sum(len(v) for v in UP.values())/len(UP):.2f} (仅 {len(UP)} 键，含同行左右)")
print(f"  扩表: {sum(len(v) for v in NEW.values())/len(NEW):.2f} ({len(NEW)} 键，含斜向/垂直)")
