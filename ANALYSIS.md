# 📊 SyncTrip 백엔드 구현 현황 분석 및 우선순위
## 전체 플로우
Band 생성 → 북마크 → 준비완료 → VOTING 전환 → 투표 → 일정생성
→ 실행(Step1/2/3) → 저장 → 조회/수정
## 구현 상태 (85-95% 완료)
- ✅ 검색 (PlaceSearchService)
- ✅ 북마크 (PlacePickService)  
- ✅ 투표 (VoteService)
- ✅ 알고리즘 (AlgorithmService)
- ✅ 일정생성 (ScheduleService)
## 🔴 P0 - Critical (즉시)
### P0-1: 중복 검사 미흡
현재: external_id만 검사
문제: "Eiffel Tower" vs "에펠탑" → 중복 미인식
해결: Name 유사도 + Category 기반 3중 검사
필요작업:
- StringUtil: 유사도 계산 함수 (Levenshtein)
- PlaceRepository: findByApiSourceAndCategory() 추가
- PlacePickService: addPick()에 중복 검사 로직 추가
- 테스트: 5개 이상 케이스
### P0-2: 테스트 부족
현재: 기본 테스트만 있음
누락: VoteServiceTest, ScheduleService E2E, 중복 검사 테스트
필요작업:
- PlacePickServiceTest: 중복 검사 케이스
- VoteServiceTest: 신규 작성
- ScheduleServiceTest: E2E 플로우
## 🟡 P1 - High (다음주)
### P1-1: 경로 재최적화
현재: swapSchedulePlace()는 단순 교체만 함
문제: A→B→C→D에서 B를 B'로 교체 후 경로 순서가 비최적
해결: 해당 day의 TSP만 재계산
### P1-2: Plan B 강화
현재: 거리(1km) 기반만 필터링
추가 필요:
- 영업시간 체크
- 투표 점수 재평가
## 🟢 P2 - Medium
### P2-1: 영업시간 처리
국내는 opening_hours = NULL
Step3에서 영업시간 체크 필요
---
## 우선순위 표
| 순위 | 작업 | 심각도 | 시간 | 시점 |
|-----|------|--------|------|------|
| P0-1 | 중복검사 | 🔴 | 2h | 즉시 |
| P0-2 | 테스트 | 🔴 | 4h | 즉시 |
| P1-1 | 경로최적화 | 🟡 | 2-3h | 다음주 |
| P1-2 | Plan B | 🟡 | 2h | 다음주 |
| P2-1 | 영업시간 | 🟢 | 1-2h | 나중 |
---
## 🔧 P0-1 상세 구현 계획
1. PlaceUtils.java 생성
   `
   calculateSimilarity(str1, str2): double [0.0-1.0]
   `
2. PlaceRepository 메서드 추가
   `
   findByApiSourceAndCategory(apiSource, category)
   `
3. PlacePickService.addPick() 개선
   `
   기존 external_id 검사 후
   → 없으면 Name+Category 기반 후보 검색
   → 유사도 80% 이상이면 재사용
   `
4. 테스트케이스
   - "Eiffel Tower" vs "에펠탑"
   - "Starbucks" vs "Starbucks Coffee"
   - 다른 카테고리는 중복 아님
---
## 🔧 P0-2 상세 구현 계획
TestCase 필요:
1. PlacePickServiceTest
   - testAddPick_duplicateByNameSimilarity()
   - testAddPick_differentCategories()
   - testAddPick_maxCountExceeded()
   - 등 5-10개
2. VoteServiceTest (신규)
   - testCastVote_autoLikeOwnBookmark()
   - testCastVote_websocketBroadcast()
   - 등 5개
3. ScheduleServiceTest
   - E2E: 북마크→투표→일정생성
   - Swap 로직
   - Plan B 추천
---
## 예상 효과
P0 완료 후:
✅ 중복 항목 제거 (사용자 경험 향상)
✅ 버그 조기 발견 (테스트 커버리지)
P1 완료 후:
✅ 경로 최적화 (여행 만족도)
✅ 예측 추천 (Plan B)
P2 완료 후:
✅ 영업시간 준수 (정합성)
