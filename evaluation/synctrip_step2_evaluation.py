# =====================================================================
# SyncTrip 알고리즘 Step 2 K-Means 성능 평가
# Silhouette Score: 결정론적 K-Means (제안) vs Round-Robin (베이스라인)
#
# 핵심: Java KMeansClustering.java와 동일한 결정론적 알고리즘을 Python으로 재현
#   - 초기화: 우선순위 최고 장소 → 그리디 최대거리 순서 (확률적 샘플링 없음)
#   - 거리:   유클리드 (Java와 동일)
#   - 수렴:   할당 변화 없음 OR max_iter=100 도달
#   - §2-7:   로드밸런싱 — 과밀 클러스터 → 부족 클러스터로 이동 (거리 제약 내)
#
# 베이스라인: Round-Robin (순차 배정)
#   우선순위 순서대로 날짜에 순환 배정 (지리적 고려 없음)
#   랜덤 배정보다 현실적인 naive 방식
#
# 평가 흐름:
#   K-Means raw → Silhouette Score 계산 (지리적 군집화 품질)
#   K-Means raw → §2-7 로드밸런싱 → 산점도 시각화
#
# 사용법: Google Colab에서 셀 구분선(# ── 셀 N ──) 기준으로 나눠 붙여넣기
# =====================================================================


# ── 셀 1: 한글 폰트 설치 및 라이브러리 임포트 ──────────────────────────────
import subprocess
subprocess.run(['apt-get', '-qq', 'install', 'fonts-nanum'], capture_output=True)

!pip install adjustText -q

import numpy as np
import pandas as pd
import matplotlib
import matplotlib.pyplot as plt
from matplotlib import font_manager
from sklearn.metrics import silhouette_score, silhouette_samples
import warnings
warnings.filterwarnings('ignore')

font_manager.fontManager.addfont('/usr/share/fonts/truetype/nanum/NanumGothic.ttf')
matplotlib.rc('font', family='NanumGothic')
matplotlib.rc('axes', unicode_minus=False)
plt.rcParams['figure.dpi'] = 120
plt.rcParams['savefig.bbox'] = 'tight'

print("✓ 설정 완료")


# ── 셀 2: 시나리오 데이터 ────────────────────────────────────────────────────
#
# 도쿄: TokyoTripScenarioTest.java 좌표 기준
#   - 팀랩 보더리스: 2024년 이전한 아자부다이 힐스 좌표로 수정
#   - 닛코 도쇼구(~130km): isOutlierCandidate 대상 — 평가에서 제외
# 오사카 / 제주: 실제 관광지 좌표

SCENARIOS = {
    "도쿄\n4명 3박4일": {
        "K": 4,
        "label": "도쿄 4명 3박4일",
        "places": [
            ("센소지",          35.7147, 139.7967),
            ("도쿄 스카이트리", 35.7101, 139.8107),
            ("시부야 스크램블", 35.6595, 139.7004),
            ("팀랩 보더리스",   35.6569, 139.7428),
            ("쓰키지 시장",     35.6654, 139.7707),
            ("신주쿠 교엔",     35.6852, 139.7100),
            ("메이지 신궁",     35.6763, 139.6993),
            ("아키하바라",      35.7023, 139.7745),
            ("하라주쿠",        35.6701, 139.7024),
            ("우에노 공원",     35.7141, 139.7741),
            ("도쿄 국립박물관", 35.7188, 139.7768),
            ("라멘 이치란",     35.6886, 139.6941),
            ("스시 긴자",       35.6717, 139.7669),
            ("도쿄 타워",       35.6586, 139.7454),
            ("오다이바",        35.6263, 139.7754),
            ("하마리큐 정원",   35.6600, 139.7648),
        ]
    },
    "오사카\n3명 2박3일": {
        "K": 3,
        "label": "오사카 3명 2박3일",
        "places": [
            ("도톤보리",          34.6687, 135.5021),
            ("오사카성",          34.6873, 135.5262),
            ("유니버설 스튜디오", 34.6654, 135.4323),
            ("신세카이",          34.6526, 135.5058),
            ("구로몬 시장",       34.6658, 135.5072),
            ("나카노시마 공원",   34.6920, 135.5018),
            ("아베노 하루카스",   34.6463, 135.5135),
            ("우메다 스카이빌딩", 34.7054, 135.4908),
            ("신사이바시",        34.6744, 135.5007),
            ("덴포잔 관람차",     34.6568, 135.4309),
            ("스미요시 대사",     34.6147, 135.4931),
            ("난바 파크스",       34.6649, 135.5013),
        ]
    },
    "제주\n5명 2박3일": {
        "K": 3,
        "label": "제주 5명 2박3일",
        "places": [
            ("성산 일출봉",     33.4580, 126.9425),
            ("만장굴",          33.5284, 126.7712),
            ("천지연 폭포",     33.2450, 126.5598),
            ("협재 해수욕장",   33.3940, 126.2399),
            ("제주 민속촌",     33.3136, 126.7890),
            ("에코랜드",        33.4418, 126.7381),
            ("오설록 티뮤지엄", 33.3058, 126.2876),
            ("섭지코지",        33.4288, 126.9300),
            ("용머리 해안",     33.2395, 126.3177),
            ("서귀포 올레시장", 33.2501, 126.5622),
            ("비자림",          33.4838, 126.8091),
            ("우도",            33.5021, 126.9514),
            ("카멜리아힐",      33.2975, 126.3369),
            ("한림공원",        33.4024, 126.2535),
            ("함덕 해수욕장",   33.5432, 126.6699),
        ]
    }
}

print(f"✓ 시나리오 {len(SCENARIOS)}개 로드 완료")
for key, data in SCENARIOS.items():
    print(f"  - {data['label']}: K={data['K']}, 장소 {len(data['places'])}개")


# ── 셀 3: 결정론적 K-Means + §2-7 로드밸런싱 구현 및 평가 함수 ──────────────
#
# [구현 대응표]
#   _det_init_centroids() ← Java KMeansClustering.initCentroids()
#   _det_kmeans()         ← Java KMeansClustering.cluster()
#   _haversine_km()       ← Java KMeansClustering.haversine()
#   _load_balance()       ← Java KMeansClustering.rebalanceLoad() §2-7
#   _round_robin()        ← 베이스라인: 우선순위 순서 순환 배정 (지리 고려 없음)

def _haversine_km(lat1, lng1, lat2, lng2):
    """Java KMeansClustering.haversine() 동일 구현"""
    R = 6371.0
    dlat = np.radians(lat2 - lat1)
    dlng = np.radians(lng2 - lng1)
    a = np.sin(dlat / 2) ** 2 + np.cos(np.radians(lat1)) * np.cos(np.radians(lat2)) * np.sin(dlng / 2) ** 2
    return R * 2 * np.arctan2(np.sqrt(a), np.sqrt(1 - a))


def _det_init_centroids(coords, K):
    """Java KMeansClustering.initCentroids() 동일 구현.
    첫 센트로이드: coords[0] (Step1 우선순위 최고 장소)
    이후: 기존 센트로이드 집합과의 최소거리가 최대인 장소 선택 (그리디).
    """
    centroids = [coords[0].copy()]
    used = {0}
    for _ in range(1, K):
        max_min_dist, best_idx = -1.0, -1
        for i in range(len(coords)):
            if i in used:
                continue
            min_d = min(np.sqrt(((coords[i] - c) ** 2).sum()) for c in centroids)
            if min_d > max_min_dist:
                max_min_dist, best_idx = min_d, i
        centroids.append(coords[best_idx].copy())
        used.add(best_idx)
    return np.array(centroids)


def _det_kmeans(coords, K, max_iter=100):
    """Java KMeansClustering.cluster() 동일 구현.
    거리: 유클리드 / 빈 클러스터: 이전 센트로이드 유지 / 수렴 or max_iter
    반환: (labels, centroids)
    """
    centroids = _det_init_centroids(coords, K)

    def assign(c):
        dists = np.sqrt(
            ((coords[:, np.newaxis, :] - c[np.newaxis, :, :]) ** 2).sum(axis=2)
        )
        return np.argmin(dists, axis=1)

    labels = assign(centroids)
    for _ in range(max_iter - 1):
        new_c = np.zeros_like(centroids)
        for k in range(K):
            mask = labels == k
            new_c[k] = coords[mask].mean(axis=0) if mask.sum() > 0 else centroids[k]
        new_labels = assign(new_c)
        if np.array_equal(labels, new_labels):
            centroids = new_c
            break
        labels, centroids = new_labels, new_c

    return labels, centroids


def _load_balance(labels, coords, K, centroids):
    """Java KMeansClustering.rebalanceLoad() 간략화 포팅 — §2-7.
    allowDist = maxDistKm × 0.3 / FIX-44: 거리 제약 미충족 시 강제 이동 없음.
    """
    labels = labels.copy()
    n = len(labels)
    avg_ceil  = int(np.ceil(n / K))
    avg_floor = n // K

    max_dist_km = 0.0
    for i in range(n):
        for j in range(i + 1, n):
            d = _haversine_km(coords[i, 0], coords[i, 1], coords[j, 0], coords[j, 1])
            if d > max_dist_km:
                max_dist_km = d
    allow_dist = max_dist_km * 0.3

    for over_k in range(K):
        over_indices = np.where(labels == over_k)[0]
        if len(over_indices) <= avg_ceil:
            continue

        dists_to_centroid = np.sqrt(((coords[over_indices] - centroids[over_k]) ** 2).sum(axis=1))
        over_indices = over_indices[np.argsort(-dists_to_centroid)]

        for idx in over_indices:
            if (labels == over_k).sum() <= avg_ceil:
                break
            for under_k in range(K):
                if under_k == over_k:
                    continue
                if (labels == under_k).sum() >= avg_floor:
                    continue
                dist = _haversine_km(coords[idx, 0], coords[idx, 1],
                                     centroids[under_k, 0], centroids[under_k, 1])
                if dist <= allow_dist:
                    labels[idx] = under_k
                    break

    return labels


def _round_robin(n, K):
    """Round-Robin 베이스라인 — 우선순위 순서대로 날짜에 순환 배정.
    지리적 고려 없이 장소 수만 균등 분배하는 naive 방식.
    place[0]→군집0, place[1]→군집1, ..., place[K]→군집0, ...
    """
    return np.array([i % K for i in range(n)])


def evaluate_scenario(places, K):
    """
    결정론적 K-Means (제안) vs Round-Robin (베이스라인) 실루엣 점수 비교.

    Returns:
        km_score     : K-Means raw 실루엣 점수
        rr_score     : Round-Robin 실루엣 점수
        raw_labels   : K-Means raw 레이블 (실루엣 다이어그램용)
        final_labels : 로드밸런싱 후 레이블 (산점도용)
    """
    coords = np.array([(lat, lng) for _, lat, lng in places])

    raw_labels, centroids = _det_kmeans(coords, K)
    km_score = silhouette_score(coords, raw_labels) if len(set(raw_labels)) >= 2 else 0.0

    final_labels = _load_balance(raw_labels, coords, K, centroids)

    rr_labels = _round_robin(len(coords), K)
    rr_score  = silhouette_score(coords, rr_labels) if len(set(rr_labels)) >= 2 else 0.0

    return km_score, rr_score, raw_labels, final_labels


def run_all(scenarios):
    rows, raw_labels_map, balanced_labels_map = [], {}, {}

    for key, data in scenarios.items():
        label = data["label"]
        K, places = data["K"], data["places"]

        km_score, rr_score, raw_labels, final_labels = evaluate_scenario(places, K)
        delta = km_score - rr_score

        rows.append({
            "시나리오":          label,
            "K (여행 일수)":     K,
            "장소 수":           len(places),
            "결정론적 K-Means":  round(km_score, 4),
            "Round-Robin":       round(rr_score, 4),
            "Δ (향상도)":        f"{delta:+.4f}",
        })
        raw_labels_map[key]      = raw_labels
        balanced_labels_map[key] = final_labels
        print(f"  ✓ {label}: K-Means={km_score:.4f}  Round-Robin={rr_score:.4f}  Δ={delta:+.4f}")

    return pd.DataFrame(rows), raw_labels_map, balanced_labels_map


print("평가 시작...\n")
df, raw_labels_map, balanced_labels_map = run_all(SCENARIOS)

print("\n" + "=" * 68)
print("  결정론적 K-Means 군집화 성능 평가 결과")
print("=" * 68)
print(df.to_string(index=False))
print("=" * 68)
print("* Silhouette Score: -1(최악) ~ +1(최고),  0.5 이상 양호")
print("* Round-Robin: 우선순위 순서대로 날짜에 순환 배정 (지리적 고려 없음)")

print("\n[로드밸런싱 §2-7 적용 전후 군집 크기]")
for key, data in SCENARIOS.items():
    K = data["K"]
    raw   = raw_labels_map[key]
    final = balanced_labels_map[key]
    print(f"  {data['label']}: raw={[int((raw==k).sum()) for k in range(K)]}  "
          f"→ 밸런싱 후={[int((final==k).sum()) for k in range(K)]}")


# ── 셀 4: 그래프 1 — 클러스터 산점도 ────────────────────────────────────────

from adjustText import adjust_text

CLUSTER_COLORS = ['#E74C3C', '#3498DB', '#2ECC71', '#F39C12']
CLUSTER_LABELS = ['군집 1', '군집 2', '군집 3', '군집 4']
MARKER_SIZE    = 140

fig, axes = plt.subplots(1, 3, figsize=(22, 7))
fig.suptitle('Step 2  결정론적 K-Means 지리적 군집화 결과',
             fontsize=15, fontweight='bold', y=1.02)

for ax, (key, data) in zip(axes, SCENARIOS.items()):
    K, places = data["K"], data["places"]
    labels    = balanced_labels_map[key]
    coords    = np.array([(lat, lng) for _, lat, lng in places])
    names     = [p[0] for p in places]
    score     = df[df["시나리오"] == data["label"]]["결정론적 K-Means"].values[0]

    texts = []
    for k in range(K):
        mask = labels == k
        ax.scatter(coords[mask, 1], coords[mask, 0],
                   c=CLUSTER_COLORS[k], s=MARKER_SIZE, zorder=3,
                   edgecolors='white', linewidths=1.5, label=CLUSTER_LABELS[k])
        for idx in np.where(mask)[0]:
            texts.append(ax.text(
                coords[idx, 1], coords[idx, 0], names[idx],
                fontsize=8, color='#2C3E50'))

    adjust_text(texts, ax=ax,
                expand_points=(1.2, 1.2), expand_text=(1.2, 1.2),
                force_text=(0.3, 0.5), force_points=(0.3, 0.5))

    xl, yl = ax.get_xlim(), ax.get_ylim()
    for t in texts:
        tx, ty = t.get_position()
        t.set_position((
            max(xl[0] + 0.002, min(xl[1] - 0.002, tx)),
            max(yl[0] + 0.002, min(yl[1] - 0.003, ty))
        ))

    cluster_dist = [int((labels == k).sum()) for k in range(K)]
    ax.set_title(f'{data["label"]}\nSilhouette Score = {score:.4f}'
                 f'\n군집 크기: {cluster_dist}',
                 fontsize=10, fontweight='bold', pad=10)
    ax.set_xlabel('경도', fontsize=9)
    ax.set_ylabel('위도', fontsize=9)
    ax.legend(fontsize=9, loc='best', framealpha=0.85)
    ax.grid(True, alpha=0.25, linestyle='--')
    ax.tick_params(labelsize=8)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

plt.tight_layout()
plt.savefig('graph1_cluster_scatter.png', dpi=150)
plt.show()
print("✓ graph1_cluster_scatter.png 저장 완료")


# ── 셀 5: 그래프 2 — 막대그래프 비교 ────────────────────────────────────────

labels_x = [data["label"] for data in SCENARIOS.values()]
km_vals  = df["결정론적 K-Means"].tolist()
rr_vals  = df["Round-Robin"].tolist()
x        = np.arange(len(labels_x))
width    = 0.32

fig, ax = plt.subplots(figsize=(11, 7))

bars1 = ax.bar(x - width / 2, km_vals, width,
               label='결정론적 K-Means (제안)', color='#3498DB',
               edgecolor='white', linewidth=1.5, zorder=3)
bars2 = ax.bar(x + width / 2, rr_vals, width,
               label='Round-Robin (순차 배정)', color='#BDC3C7',
               edgecolor='white', linewidth=1.5, zorder=3)

for bar, val in zip(bars1, km_vals):
    ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.012,
            f'{val:.4f}', ha='center', va='bottom',
            fontsize=11, fontweight='bold', color='#1A6FA8')

for bar, val in zip(bars2, rr_vals):
    ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.012,
            f'{val:.4f}', ha='center', va='bottom',
            fontsize=10, color='#7F8C8D')

for i, (km, rr) in enumerate(zip(km_vals, rr_vals)):
    delta = km - rr
    ax.annotate(f'Δ={delta:+.4f}',
                xy=(x[i] - width / 2, km + 0.06),
                ha='center', fontsize=10,
                color='#E74C3C', fontweight='bold')

ax.axhline(y=0.5, color='#E74C3C', linestyle=':', linewidth=1.8,
           label='양호 기준선 (0.5)', zorder=2)
ax.axhline(y=0,   color='#2C3E50', linestyle='-',  linewidth=1.2, alpha=0.4, zorder=1)
ax.set_ylim(-0.1, max(km_vals) * 1.6)
ax.set_xticks(x)
ax.set_xticklabels(labels_x, fontsize=11)
ax.set_xlabel('시나리오', fontsize=12)
ax.set_ylabel('Silhouette Score', fontsize=12)
ax.set_title('Step 2  결정론적 K-Means vs Round-Robin 군집화 품질 비교',
             fontsize=13, fontweight='bold')
ax.legend(fontsize=11, loc='upper right')
ax.grid(True, axis='y', alpha=0.25, linestyle='--', zorder=0)
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)

plt.tight_layout()
plt.savefig('graph2_bar_comparison.png', dpi=150)
plt.show()
print("✓ graph2_bar_comparison.png 저장 완료")


# ── 셀 6: 그래프 3 — 실루엣 다이어그램 ──────────────────────────────────────

fig, axes = plt.subplots(1, 3, figsize=(20, 7))
fig.suptitle('Step 2  장소별 Silhouette 계수 분포 (K-Means 군집 기준)',
             fontsize=15, fontweight='bold')

for ax, (key, data) in zip(axes, SCENARIOS.items()):
    K, places = data["K"], data["places"]
    labels    = raw_labels_map[key]
    coords    = np.array([(lat, lng) for _, lat, lng in places])
    names     = [p[0] for p in places]

    sample_sil = silhouette_samples(coords, labels)
    avg        = silhouette_score(coords, labels)

    y_lower = 8
    for k in range(K):
        vals    = np.sort(sample_sil[labels == k])
        y_upper = y_lower + len(vals)

        ax.fill_betweenx(np.arange(y_lower, y_upper), 0, vals,
                         facecolor=CLUSTER_COLORS[k], alpha=0.85, edgecolor='none')
        ax.text(-0.08, (y_lower + y_upper) / 2, CLUSTER_LABELS[k],
                ha='right', va='center', fontsize=9,
                color=CLUSTER_COLORS[k], fontweight='bold')

        for j, idx in enumerate(np.argsort(sample_sil[labels == k])):
            real_idx = np.where(labels == k)[0][idx]
            ax.text(vals[j] + 0.015, y_lower + j,
                    names[real_idx], fontsize=6.5, va='center', color='#555')

        y_lower = y_upper + 6

    ax.axvline(x=avg, color='#E74C3C', linestyle='--', linewidth=2,
               label=f'알고리즘 점수 {avg:.4f}')
    ax.axvline(x=0.5, color='#F39C12', linestyle=':', linewidth=1.5,
               label='양호 기준 0.5')
    ax.set_xlim(-0.35, 1.05)
    ax.set_title(f'{data["label"]}', fontsize=11, fontweight='bold')
    ax.set_xlabel('Silhouette 계수', fontsize=9)
    ax.set_ylabel('장소 (군집별 정렬)', fontsize=9)
    ax.set_yticks([])
    ax.legend(fontsize=9, loc='lower right')
    ax.grid(True, axis='x', alpha=0.25, linestyle='--')

plt.tight_layout()
plt.savefig('graph3_silhouette_diagram.png', dpi=150)
plt.show()
print("✓ graph3_silhouette_diagram.png 저장 완료")

print("\n" + "=" * 65)
print("  전체 실행 완료 — 저장된 파일 목록")
print("=" * 65)
print("  graph1_cluster_scatter.png    — 클러스터 산점도")
print("  graph2_bar_comparison.png     — K-Means vs Round-Robin 비교")
print("  graph3_silhouette_diagram.png — 실루엣 다이어그램")
