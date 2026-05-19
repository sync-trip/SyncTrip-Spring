# 장소 검색 기능 분석 및 구현 현황 보고서

본 문서는 `PlaceSearchService`를 중심으로 한 국내(Kakao) 및 해외(Google) 장소 검색 기능의 구현 현황과 문제점을 분석한 결과입니다.

## 1. 개요
SyncTrip 서비스는 사용자의 여행지 위치에 따라 최적화된 지도 API를 사용하도록 설계되었습니다.
- **국내 여행 (Korea):** Kakao Local API 사용 (정밀도 및 데이터 풍부성)
- **해외 여행 (Overseas):** Google Places API 사용 (글로벌 범용성)

## 2. 코드 분석 결과

### 2.1 `PlaceSearchService` 내 예외 처리의 의미
사용자가 지적한 코드는 다음과 같습니다:
```java
if (!band.isOverseas()) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "국내 장소 검색은 카카오맵을 사용합니다.");
}
```
- **현재 동작:** 국내 여행(`isOverseas == false`)인 경우, 검색을 진행하지 않고 즉시 예외를 발생시킵니다.
- **판단:** 이는 국내 검색 로직이 아직 **통합되지 않았음을 의미**합니다. 즉, 현재 상태로는 국내 장소 검색 기능이 **정상적으로 작동하지 않습니다.**

### 2.2 구현 현황 및 문제점
분석 결과, 시스템은 다음과 같이 "미완성" 상태인 것으로 확인되었습니다.

| 구분 | 현황 | 비고 |
|---|---|---|
| **Google 연동 (`GooglePlacesService`)** | 완료 | `PlaceSearchService`에 통합되어 해외 검색 정상 작동 |
| **Kakao 연동 (`KakaoPlacesService`)** | 완료 | API 호출 및 결과 파싱 로직은 구현되어 있음 |
| **서비스 통합 (`PlaceSearchService`)** | **미흡** | `KakaoPlacesService`를 주입받지 않고 있으며, 국내 검색 시 예외만 발생시킴 |
| **테스트 코드 (`PlaceSearchServiceTest`)** | **실패(컴파일 에러)** | 테스트 코드는 통합된 생성자를 기대하고 있으나, 실제 코드가 달라 빌드 오류 발생 |

## 3. 구현된 기능 목록

### 3.1 공통 기능
- **장소 캐싱:** 외부 API(Google/Kakao) 검색 결과를 `places` 테이블에 자동 저장 및 동기화 (External ID 기반 중복 방지).
- **북마크 연동:** 검색 결과 반환 시 현재 사용자의 북마크 여부(`isBookmarked`)를 포함.
- **카테고리 매핑:** 외부 API의 복잡한 타입을 서비스 표준 카테고리(음식, 문화, 활동, 쇼핑, 자연)로 변환.

### 3.2 Google Places (해외)
- **반경 검색:** 여행 목적지 중심 지정된 반경 내 검색.
- **영업 시간 변환:** Google의 복잡한 운영 시간 데이터를 JSON 구조로 변환하여 저장.
- **이미지 URL 빌드:** Google Photo Reference를 실제 접근 가능한 URL로 변환.

### 3.3 Kakao Local (국내)
- **카테고리 그룹 검색:** Kakao 전용 그룹 코드(FD6, CT1 등)를 활용한 검색.
- **좌표 체계 대응:** Google과 반대인 Kakao의 좌표 체계(x=경도, y=위도) 처리.
- **결과 중복 제거:** 여러 카테고리에 걸쳐 검색된 동일 장소에 대한 중복 제거.

## 4. 향후 조치 제안
현재 프로젝트는 **컴파일 에러 상태**이며 국내 검색이 차단되어 있습니다. 이를 해결하기 위해 다음 작업이 필요합니다:
1. `PlaceSearchService` 생성자에 `KakaoPlacesService` 주입.
2. `searchPlaces` 메서드 내에서 `isOverseas()` 여부에 따라 분기 로직 구현 (Kakao 호출 추가).
3. Kakao 검색 결과를 `Place` 엔티티로 변환하여 저장하는 로직 추가.

---
*작성일: 2026년 5월 19일*
*작성자: Gemini CLI*
