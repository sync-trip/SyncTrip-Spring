# =====================================================================
# SyncTrip 알고리즘 동선 효율 평가 (방안 A) — 정적 막대그래프
#
# 비지도 학습이라 정답 라벨이 없으므로, "알고리즘이 목적을 달성했는가"를
# 이동거리로 직접 측정한다. 실루엣(내재적 지표)과 달리 목적 기반 비교 평가.
#
#   ① 군집 품질   : K-Means 날짜 배분 vs Round-Robin 배분의 총 이동거리
#                   → naive 베이스라인 대비 향상도 (단축률 %)
#   ② 라우팅 품질 : Nearest Neighbor TSP vs 완전탐색 최적해
#                   → 이론적 최적 대비 근사 비율 (격차 %)
#
# 데이터 출처: Java AlgorithmService 실제 출력
#   synctrip/src/test/.../AlgorithmVizExportTest.java 가 추출한
#   evaluation/synctrip_pipeline.json 의 travelDistanceMetrics 블록.
#   같은 폴더에 JSON이 있으면 자동으로 최신값을 읽고, 없으면 아래 임베드값 사용.
#
# 사용법: Google Colab에서 셀 구분선(# ── 셀 N ──) 기준으로 나눠 붙여넣기
# =====================================================================


# ── 셀 1: 한글 폰트 설치 및 라이브러리 임포트 ──────────────────────────────
import subprocess
try:
    # Colab 환경에서만 나눔폰트 설치 (로컬엔 apt-get이 없으므로 건너뜀)
    subprocess.run(['apt-get', '-qq', 'install', 'fonts-nanum'], capture_output=True)
except FileNotFoundError:
    pass

import os
import json
import numpy as np
import matplotlib
import matplotlib.pyplot as plt
from matplotlib import font_manager
import warnings
warnings.filterwarnings('ignore')

try:
    font_manager.fontManager.addfont('/usr/share/fonts/truetype/nanum/NanumGothic.ttf')
    matplotlib.rc('font', family='NanumGothic')
except Exception:
    # 로컬(Windows 등)에서는 시스템 한글 폰트로 대체
    matplotlib.rc('font', family='Malgun Gothic')
matplotlib.rc('axes', unicode_minus=False)
plt.rcParams['figure.dpi'] = 120
plt.rcParams['savefig.bbox'] = 'tight'

print("✓ 설정 완료")


# ── 셀 2: 데이터 로드 (JSON 우선, 없으면 임베드값) ──────────────────────────
#
# 임베드 기본값 = synctrip_pipeline.json(도쿄 4명 3박4일) 추출 시점의 수치.
# Java 테스트를 다시 돌려 JSON을 갱신하면, 같은 폴더에 두기만 하면 자동 반영된다.

EMBEDDED = {
    "clustering": {
        "perDay": [
            {"day": 1, "kmeansKm": 14.37, "roundRobinKm": 11.92},
            {"day": 2, "kmeansKm": 0.00,  "roundRobinKm": 12.56},
            {"day": 3, "kmeansKm": 3.74,  "roundRobinKm": 15.16},
            {"day": 4, "kmeansKm": 7.96,  "roundRobinKm": 128.18},
        ],
        "kmeansTotalKm": 26.07,
        "roundRobinTotalKm": 167.82,
        "savingPct": 84.5,
    },
    "routingQuality": {
        "perDay": [
            {"day": 1, "nnKm": 17.20, "optimalKm": 14.37},
            {"day": 2, "nnKm": 0.00,  "optimalKm": 0.00},
            {"day": 3, "nnKm": 3.74,  "optimalKm": 3.74},
            {"day": 4, "nnKm": 8.61,  "optimalKm": 7.96},
        ],
        "nnTotalKm": 29.56,
        "optimalTotalKm": 26.07,
        "gapToOptimalPct": 13.4,
    },
}

def load_metrics():
    """같은 폴더의 synctrip_pipeline.json 이 있으면 최신값을, 없으면 임베드값을 반환."""
    here = os.path.dirname(os.path.abspath(__file__)) if '__file__' in globals() else '.'
    path = os.path.join(here, 'synctrip_pipeline.json')
    if os.path.exists(path):
        with open(path, encoding='utf-8') as f:
            m = json.load(f)['travelDistanceMetrics']
        print(f"✓ JSON에서 최신 지표 로드: {path}")
        return m
    print("ℹ JSON 미발견 — 임베드 기본값 사용")
    return EMBEDDED

M = load_metrics()
CL = M["clustering"]
RQ = M["routingQuality"]

print(f"  군집:   K-Means {CL['kmeansTotalKm']}km vs Round-Robin {CL['roundRobinTotalKm']}km  → {CL['savingPct']}% 단축")
print(f"  라우팅: NN-TSP {RQ['nnTotalKm']}km vs 최적 {RQ['optimalTotalKm']}km  → +{RQ['gapToOptimalPct']}%")


# ── 셀 3: 그래프 1 — 군집 품질 (K-Means vs Round-Robin) ────────────────────
#
# 핵심 지표는 "총 이동거리"(왼쪽). 오른쪽은 묶음별 분포로,
# Round-Robin이 먼 장소(닛코 130km)를 한 날에 몰아 넣어 폭증하는 모습을 보여준다.

C_KMEANS = '#2ECC71'   # 제안 (초록)
C_RR     = '#E74C3C'   # 베이스라인 (빨강)

fig, (axL, axR) = plt.subplots(1, 2, figsize=(15, 6),
                               gridspec_kw={'width_ratios': [1, 1.5]})
fig.suptitle('방안 A-①  K-Means 군집 vs Round-Robin — 총 이동거리 비교',
             fontsize=15, fontweight='bold', y=1.0)

# (왼쪽) 총합 비교 — 핵심 지표
km_total = CL['kmeansTotalKm']
rr_total = CL['roundRobinTotalKm']
bars = axL.bar(['K-Means\n(제안)', 'Round-Robin\n(베이스라인)'],
               [km_total, rr_total],
               color=[C_KMEANS, C_RR], edgecolor='white', linewidth=2, zorder=3, width=0.6)
for b, v in zip(bars, [km_total, rr_total]):
    axL.text(b.get_x() + b.get_width() / 2, v + rr_total * 0.02, f'{v:.1f} km',
             ha='center', va='bottom', fontsize=13, fontweight='bold')

# 단축률 화살표 주석
axL.annotate('', xy=(0, km_total), xytext=(0, rr_total),
             arrowprops=dict(arrowstyle='<->', color='#2C3E50', lw=1.6))
axL.text(0.08, (km_total + rr_total) / 2,
         f"-{CL['savingPct']:.1f}%\n단축", ha='left', va='center',
         fontsize=14, fontweight='bold', color='#1a9b54')
axL.set_ylabel('총 이동거리 (km)', fontsize=12)
axL.set_ylim(0, rr_total * 1.18)
axL.set_title('전체 일정 합계 (핵심 지표)', fontsize=12, fontweight='bold')
axL.grid(True, axis='y', alpha=0.25, linestyle='--', zorder=0)
axL.spines['top'].set_visible(False)
axL.spines['right'].set_visible(False)

# (오른쪽) 묶음별 분포 — Round-Robin의 동선 폭증 시각화
days = [d['day'] for d in CL['perDay']]
km_vals = [d['kmeansKm'] for d in CL['perDay']]
rr_vals = [d['roundRobinKm'] for d in CL['perDay']]
x = np.arange(len(days))
w = 0.38

b1 = axR.bar(x - w / 2, km_vals, w, label='K-Means (제안)',
             color=C_KMEANS, edgecolor='white', linewidth=1.2, zorder=3)
b2 = axR.bar(x + w / 2, rr_vals, w, label='Round-Robin (베이스라인)',
             color=C_RR, edgecolor='white', linewidth=1.2, zorder=3)
for bars_, vals_ in [(b1, km_vals), (b2, rr_vals)]:
    for b, v in zip(bars_, vals_):
        axR.text(b.get_x() + b.get_width() / 2, v + max(rr_vals) * 0.015,
                 f'{v:.1f}', ha='center', va='bottom', fontsize=9)
axR.set_xticks(x)
axR.set_xticklabels([f'묶음 {d}\n(날짜)' for d in days], fontsize=10)
axR.set_ylabel('이동거리 (km)', fontsize=12)
axR.set_title('묶음별 분포 — Round-Robin은 먼 장소를 한 날에 몰아 폭증', fontsize=11, fontweight='bold')
axR.legend(fontsize=11, loc='upper left')
axR.grid(True, axis='y', alpha=0.25, linestyle='--', zorder=0)
axR.spines['top'].set_visible(False)
axR.spines['right'].set_visible(False)

plt.tight_layout()
plt.savefig('distance1_clustering.png', dpi=150)
plt.show()
print("✓ distance1_clustering.png 저장 완료")


# ── 셀 4: 그래프 2 — 라우팅 품질 (NN-TSP vs 완전탐색 최적해) ────────────────
#
# 날짜별로 동일한 장소 집합에 대해 방문 순서만 비교 (apples-to-apples).
# Nearest Neighbor 휴리스틱이 이론적 최적해에 얼마나 근접하는지 → 근사 비율.

C_NN  = '#3498DB'   # 우리 (파랑)
C_OPT = '#F39C12'   # 최적 (주황)

fig, ax = plt.subplots(figsize=(11, 6.5))

days = [d['day'] for d in RQ['perDay']]
nn_vals  = [d['nnKm'] for d in RQ['perDay']]
opt_vals = [d['optimalKm'] for d in RQ['perDay']]
# 합계 막대도 함께 표시
days_lbl = [f'Day {d}' for d in days] + ['전체 합계']
nn_vals_all  = nn_vals + [RQ['nnTotalKm']]
opt_vals_all = opt_vals + [RQ['optimalTotalKm']]

x = np.arange(len(days_lbl))
w = 0.38
b1 = ax.bar(x - w / 2, nn_vals_all, w, label='NN-TSP (우리 알고리즘)',
            color=C_NN, edgecolor='white', linewidth=1.4, zorder=3)
b2 = ax.bar(x + w / 2, opt_vals_all, w, label='완전탐색 최적해 (이론적 하한)',
            color=C_OPT, edgecolor='white', linewidth=1.4, zorder=3)

mx = max(nn_vals_all)
for bars_, vals_ in [(b1, nn_vals_all), (b2, opt_vals_all)]:
    for b, v in zip(bars_, vals_):
        ax.text(b.get_x() + b.get_width() / 2, v + mx * 0.012,
                f'{v:.1f}', ha='center', va='bottom', fontsize=9.5, fontweight='bold')

# 전체 합계 막대 위에 근사 격차 주석
ax.annotate(f"최적 대비 +{RQ['gapToOptimalPct']:.1f}%",
            xy=(x[-1], RQ['nnTotalKm']),
            xytext=(x[-1], mx * 1.12),
            ha='center', fontsize=12, fontweight='bold', color='#1A6FA8',
            arrowprops=dict(arrowstyle='->', color='#1A6FA8', lw=1.4))

# 전체 합계 구분선
ax.axvline(x=len(days) - 0.5, color='#BDC3C7', linestyle='--', linewidth=1.2, zorder=1)

ax.set_xticks(x)
ax.set_xticklabels(days_lbl, fontsize=11)
ax.set_ylabel('이동거리 (km)', fontsize=12)
ax.set_title('방안 A-②  Nearest Neighbor TSP vs 완전탐색 최적해 — 라우팅 근사 품질',
             fontsize=13, fontweight='bold')
ax.set_ylim(0, mx * 1.25)
ax.legend(fontsize=11, loc='upper left')
ax.grid(True, axis='y', alpha=0.25, linestyle='--', zorder=0)
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)

plt.tight_layout()
plt.savefig('distance2_routing.png', dpi=150)
plt.show()
print("✓ distance2_routing.png 저장 완료")


# ── 셀 5: 요약 출력 ─────────────────────────────────────────────────────────
print("\n" + "=" * 64)
print("  방안 A — 동선 효율 평가 요약 (도쿄 4명 3박4일)")
print("=" * 64)
print(f"① 군집 품질   : K-Means {CL['kmeansTotalKm']:>6.1f} km")
print(f"                Round-Robin {CL['roundRobinTotalKm']:>6.1f} km")
print(f"                → 이동거리 {CL['savingPct']:.1f}% 단축 (vs naive 베이스라인)")
print(f"② 라우팅 품질 : NN-TSP {RQ['nnTotalKm']:>6.1f} km")
print(f"                완전탐색 최적 {RQ['optimalTotalKm']:>6.1f} km")
print(f"                → 이론적 최적 대비 +{RQ['gapToOptimalPct']:.1f}% (근사 비율)")
print("=" * 64)
print("* 실루엣과 달리 '목적 달성도'를 km 단위로 직접 측정한 평가 지표")
print("  - ①은 baseline 대비 향상도, ②는 정답(최적해) 대비 근사 비율")
