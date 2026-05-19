# SyncTrip 백엔드 장소 검색 기능 구현 현황

## 📋 프로젝트 개요
SyncTrip는 그룹 여행 의사결정 및 일정 최적화 플랫폼으로,  
국내/해외 여행에 맞는 자동 장소 검색 및 동선 최적화를 제공합니다.

---

## ✅ 현재 구현된 기능

### 1. 국내 장소 검색 (Kakao Local API)
**파일**: `KakaoPlacesService.java`

- **역할**: Kakao Local API를 통해 국내 주변 장소 검색
- **입력**: 좌표(lat, lng), 검색 반경(미터), 카테고리 필터
- **출력**: 거리순 정렬된 장소 목록 (중복 제거)
- **카테고리 매핑**:
  - FD6/CE7 → FOOD (음식점)
  - CT1/AT4 → CULTURE (문화/관광)
  - SC4 → SHOPPING (쇼핑)
  - PK6 → NATURE (공원/자연)
  - AT4 → ACTIVITY (관광지)

**핵심 로직**:
```
1. PlaceCategory → Kakao 그룹 코드 변환
2. 각 그룹 코드별로 API 호출 (예: FOOD면 FD6, CE7 두 번 호출)
3. 결과 합치기 (ID 기준 중복 제거)
4. 거리순 정렬해 반환
```

---

### 2. 해외 장소 검색 (Google Places API)
**파일**: `GooglePlacesService.java` (기존)

- **역할**: Google Places API를 통해 해외 주변 장소 검색
- **입력**: 좌표(lat, lng), 검색 반경(미터), 카테고리 필터
- **출력**: 거리순 정렬된 장소 목록 + 영업시간 정보
- **특징**:
  - Google Types → SyncTrip 카테고리 자동 매핑
  - 영업시간(opening_hours) JSON으로 저장

---

### 3. 통합 검색 라우팅
**파일**: `PlaceSearchService.java`

#### 엔드포인트
```
GET /api/bands/{bandId}/places/search
  ?category=FOOD&radiusMeters=5000
```

#### 자동 라우팅 로직
```
if (band.isOverseas == true)
  → Google Places API (해외)
else
  → Kakao Local API (국내)
```

#### 처리 과정
1. 사용자/밴드 권한 검증
2. API 호출 (국내/해외 자동 선택)
3. 결과를 `places` 테이블에 캐싱
   - `PlaceApiSource` enum으로 출처 구분 (KAKAO / GOOGLE)
   - 중복 저장 방지 (externalId 기준)
4. 사용자의 북마크 여부 조회 및 응답에 포함
5. `PlaceSearchResult` DTO로 변환 후 반환

#### 응답 형식
```json
{
  "placeId": 300,
  "apiSource": "KAKAO",
  "externalId": "kakao-123",
  "name": "경복궁",
  "category": "CULTURE",
  "latitude": 37.579617,
  "longitude": 126.977041,
  "address": "서울 종로구 사직로 161",
  "rating": 4.7,
  "thumbnailUrl": "...",
  "isBookmarked": false
}
```

---

### 4. 장소 캐싱
**목적**: 동일 장소를 재검색해도 DB에 중복 저장 방지

**구현**:
```
1. API 응답 수신
2. places 테이블에서 (apiSource, externalId) 조합으로 검색
3. 존재하면 → 메타데이터만 업데이트
4. 없으면 → 새로운 Place 엔티티 생성 후 저장
```

**국내/해외 차이**:
- **국내 (Kakao)**: opening_hours = NULL (저장 안 함)
- **해외 (Google)**: opening_hours = JSON (영업시간 정보 포함)

---

## 🔧 설정 및 환경변수

### application.yml
```yaml
kakao:
  local-search-uri: https://dapi.kakao.com/v2/local/search/category.json
  client-id: ${KAKAO_CLIENT_ID}
  client-secret: ${KAKAO_CLIENT_SECRET}

google:
  maps:
    api-key: ${GOOGLE_MAPS_API_KEY}
    places-base-url: https://places.googleapis.com
```

### .env (로컬 개발용)
```
KAKAO_CLIENT_ID=<카카오 앱 ID>
KAKAO_CLIENT_SECRET=<카카오 앱 시크릿>
KAKAO_LOCAL_SEARCH_URI=https://dapi.kakao.com/v2/local/search/category.json

GOOGLE_MAPS_API_KEY=<Google API Key>
GOOGLE_PLACES_BASE_URL=https://places.googleapis.com
```

---

## 📊 아키텍처 흐름도

```
사용자 요청
  ↓
PlaceController.searchPlaces()
  ↓
PlaceSearchService.searchPlaces()
  ↓
  ├─ band.isOverseas() == true
  │   └─ GooglePlacesService.searchNearby()
  │       ├─ Google Places API 호출
  │       └─ 결과 → places 테이블 캐싱
  │
  └─ band.isOverseas() == false
      └─ KakaoPlacesService.searchNearby()
          ├─ Kakao Local API 호출 (다중 category code)
          └─ 결과 → places 테이블 캐싱
  ↓
PlaceSearchResult[] 변환
  ├─ Place 엔티티 → DTO
  ├─ isBookmarked 플래그 추가
  └─ JSON 응답
  ↓
응답 (200 OK)
```

---

## 🧪 테스트

### 단위 테스트
- `KakaoPlacesServiceTest`: Kakao API 호출 및 중복 제거 검증
- `PlaceSearchServiceTest`: 국내 검색 라우팅 및 캐싱 검증

**실행**:
```bash
./gradlew test --tests com.sync.service.KakaoPlacesServiceTest
./gradlew test --tests com.sync.service.PlaceSearchServiceTest
```

---

## 📝 미구현 / 보완 필요 사항

### 1. 지도 SDK (프론트 담당)
- Google Maps SDK (안드로이드 앱에서 지도 표시)
- Kakao Map SDK (안드로이드 앱에서 지도 표시)
- **백엔드는 장소 데이터만 제공, 지도 렌더링은 프론트 책임**

### 2. 영업시간 고급 처리 (선택)
- 현재: 요일별 첫 번째 period만 사용
- 개선: 여러 period(예: 11:00-14:00, 17:00-21:00) 모두 처리
- 개선: 자정 넘는 운영 시간(23:00-02:00) 처리

### 3. 고급 검색 기능
- 가격대(가성비) 필터
- 평점 필터링
- 영업 상태(영업중/폐업) 확인
- 사용자 리뷰 통합

### 4. 성능 최적화 (선택)
- API 응답 캐싱 (Redis 등)
- 배치 검색 (여러 카테고리 동시 호출)
- 비동기 처리 (CompletableFuture)

---

## 🚀 다음 작업 순서 추천

### 우선순위 1: 프론트 연동 검증
1. Postman/OpenAPI로 API 테스트
2. 안드로이드 앱과 통합 테스트
3. 응답 포맷 확인 및 조정

### 우선순위 2: 영업시간 고급 처리
1. 요일별 다중 period 지원
2. 자정 넘는 시간대 처리
3. 스케줄 생성 시 영업시간 검증

### 우선순위 3: 추가 필터링
1. 평점 기반 정렬/필터
2. 가격대 표시 (Google Places rating만 현재)

---

## 📚 관련 파일 목록

| 파일 | 역할 | 상태 |
|---|---|---|
| `KakaoPlacesService.java` | 국내 장소 검색 | ✅ 완료 |
| `GooglePlacesService.java` | 해외 장소 검색 | ✅ 완료 (기존) |
| `PlaceSearchService.java` | 통합 검색 라우팅 | ✅ 완료 |
| `PlaceController.java` | REST 엔드포인트 | ✅ 완료 |
| `KakaoProperties.java` | Kakao 설정 | ✅ 완료 |
| `GoogleMapsProperties.java` | Google 설정 | ✅ 완료 |
| `.env` | 환경변수 (로컬) | ✅ 완료 |
| `.env.example` | 환경변수 템플릿 | ✅ 완료 |
| `application.yml` | Spring 설정 | ✅ 완료 |

---

## 💡 핵심 설계 원칙

1. **국내/해외 자동 분기**
   - 밴드의 `isOverseas` 플래그로 결정
   - 서비스 레이어에서 자동 라우팅

2. **순수 함수 기반 알고리즘**
   - 검색 로직은 DB 독립적
   - 캐싱은 서비스 레이어에서만

3. **출처별 메타데이터 관리**
   - PlaceApiSource enum으로 구분
   - externalId로 중복 저장 방지

4. **트랜잭션 안전성**
   - @Transactional로 DB 일관성 보장
   - API 호출과 캐싱을 한 트랜잭션으로 처리

---

**작성 일시**: 2026-05-19  
**프로젝트**: SyncTrip Backend v1.0

