# -*- coding: utf-8 -*-
"""
84.5% 절감 수치 정밀 검증 + Round-Robin 비교군 적합성 분석.
synctrip_pipeline.json 의 실제 좌표로 haversine 거리를 독립 재계산한다.
"""
import json, math, itertools, random
from pathlib import Path

J = json.loads(Path("C:/SyncTrip-Spring/evaluation/synctrip_pipeline.json").read_text(encoding="utf-8"))

R = 6371.0
def hav(a, b):
    la1, lo1, la2, lo2 = map(math.radians, [a[0], a[1], b[0], b[1]])
    dla, dlo = la2-la1, lo2-lo1
    h = math.sin(dla/2)**2 + math.cos(la1)*math.cos(la2)*math.sin(dlo/2)**2
    return 2*R*math.asin(math.sqrt(h))

# 좌표 맵 (mainPool 만 — Step2/3 대상)
coord = {p["id"]: (p["lat"], p["lng"]) for p in J["step1"]["mainPool"]}
prio  = {p["id"]: p["priorityScore"] for p in J["step1"]["mainPool"]}
name  = {p["id"]: p["name"] for p in J["step1"]["mainPool"]}
ids = list(coord.keys())
K = J["K"]

def optimal_path_km(group):
    """그룹 내 최단 경로(완전탐색). 9곳 초과 시 nearest-neighbor 근사."""
    if len(group) < 2:
        return 0.0
    if len(group) <= 8:
        best = float("inf")
        for perm in itertools.permutations(group):
            d = sum(hav(coord[perm[i]], coord[perm[i+1]]) for i in range(len(perm)-1))
            best = min(best, d)
        return best
    # NN 근사
    rem = group[:]; cur = rem.pop(0); tot = 0.0
    while rem:
        nxt = min(rem, key=lambda x: hav(coord[cur], coord[x]))
        tot += hav(coord[cur], coord[nxt]); rem.remove(nxt); cur = nxt
    return tot

def total_km(day_groups):
    return sum(optimal_path_km(g) for g in day_groups.values())

# ── 1) K-Means 배정 (step2 결과 그대로) ───────────────────────────
kmeans = {}
for dg in J["step2"]["dayGroups"]:
    kmeans[dg["day"]] = [p["id"] for p in dg["places"]]
km_total = total_km(kmeans)

# ── 2) Round-Robin 배정 (우선순위 내림차순, i%K) ──────────────────
rr_order = sorted(ids, key=lambda i: -prio[i])
rr = {d: [] for d in range(1, K+1)}
for i, pid in enumerate(rr_order):
    rr[(i % K) + 1].append(pid)
rr_total = total_km(rr)

print("="*64)
print("1) 84.5% 절감 수치 재검증 (독립 haversine 계산)")
print("="*64)
print(f"  K-Means 총 이동거리   : {km_total:7.2f} km   (JSON: {J['travelDistanceMetrics']['clustering']['kmeansTotalKm']})")
print(f"  Round-Robin 총 이동거리: {rr_total:7.2f} km   (JSON: {J['travelDistanceMetrics']['clustering']['roundRobinTotalKm']})")
saving = (1 - km_total/rr_total) * 100
print(f"  → 절감률: {saving:.1f}%   (JSON: {J['travelDistanceMetrics']['clustering']['savingPct']}%)")

print()
print("  [Round-Robin Day별 분해 — 어디서 거리가 폭발하는가]")
for d in range(1, K+1):
    g = rr[d]
    nm = ", ".join(name[i] for i in g)
    has_nikko = " ★닛코포함" if 19 in g else ""
    print(f"    Day {d}: {optimal_path_km(g):7.2f} km{has_nikko}  [{nm}]")

# ── 3) 닛코(outlier) 의존도 분석 ──────────────────────────────────
print()
print("="*64)
print("2) 닛코 outlier 의존도 — 닛코 제외 후 순수 도심 군집화 효과")
print("="*64)
NIKKO = 19
ids_no = [i for i in ids if i != NIKKO]

# 닛코를 양쪽 다 단독 Day로 빼고, 나머지를 K-1개 Day로 재배정해 공정 비교
# K-Means: step2에서 닛코 빼고 나머지 그대로
km_no = {d: [i for i in g if i != NIKKO] for d, g in kmeans.items()}
km_no_total = total_km(km_no)
# Round-Robin: 닛코 뺀 우선순위순 i%K
rr_order_no = sorted(ids_no, key=lambda i: -prio[i])
rr_no = {d: [] for d in range(1, K+1)}
for i, pid in enumerate(rr_order_no):
    rr_no[(i % K) + 1].append(pid)
rr_no_total = total_km(rr_no)
saving_no = (1 - km_no_total/rr_no_total) * 100
print(f"  K-Means (닛코 제외)    : {km_no_total:7.2f} km")
print(f"  Round-Robin (닛코 제외): {rr_no_total:7.2f} km")
print(f"  → 닛코 제외 절감률: {saving_no:.1f}%")
print(f"  → 84.5% 중 닛코 단일 outlier 기여분: {saving - saving_no:.1f}%p")

# ── 4) Round-Robin vs 랜덤 배정 (더 공정한 베이스라인) ────────────
print()
print("="*64)
print("3) Round-Robin 비교군 적합성 — 랜덤 배정 1000회 분포와 대조")
print("="*64)
random.seed(42)
rand_totals = []
for _ in range(1000):
    sh = ids[:]; random.shuffle(sh)
    g = {d: [] for d in range(1, K+1)}
    for i, pid in enumerate(sh):
        g[(i % K) + 1].append(pid)
    rand_totals.append(total_km(g))
rand_totals.sort()
import statistics
mean = statistics.mean(rand_totals)
med  = statistics.median(rand_totals)
p10, p90 = rand_totals[100], rand_totals[900]
mn, mx = rand_totals[0], rand_totals[-1]
print(f"  랜덤 배정 1000회: 평균 {mean:.1f} / 중앙 {med:.1f} / 최소 {mn:.1f} / 최대 {mx:.1f} km")
print(f"                   10~90 분위: {p10:.1f} ~ {p90:.1f} km")
print(f"  K-Means        : {km_total:.1f} km")
print(f"  Round-Robin    : {rr_total:.1f} km  ← 랜덤 평균 대비 {'위(나쁨)' if rr_total>mean else '아래(좋음)'}")
# K-Means가 랜덤 분포에서 몇 퍼센타일인지
better = sum(1 for t in rand_totals if t < km_total)
print(f"  → K-Means보다 거리가 짧은 랜덤 배정 비율: {better/10:.1f}%  (낮을수록 K-Means가 우수)")
print(f"  → K-Means vs 랜덤평균 절감률: {(1-km_total/mean)*100:.1f}%  (Round-Robin 대신 이 수치가 더 공정)")
