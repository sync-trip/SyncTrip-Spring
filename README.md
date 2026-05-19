# SyncTrip - 개인별 장바구니(Pick)

## 추가된 기능
- 밴드별 개인 장바구니(Pick) API
- 카카오맵/구글맵 검색 결과를 공통 `Place` 엔티티로 저장
- 사용자별 5개 제한
- 중복 저장 방지
- 장바구니 목록 조회 / 삭제

## API
- `POST /api/bands/{bandId}/picks` : 장소 추가
- `GET /api/bands/{bandId}/picks` : 내 장바구니 목록 조회
- `DELETE /api/bands/{bandId}/picks/{placeId}` : 장소 삭제

## 요청 예시
```json
{
  "apiSource": "KAKAO",
  "externalId": "kakao-123",
  "name": "경복궁",
  "category": "CULTURE",
  "latitude": 37.579617,
  "longitude": 126.977041,
  "address": "서울 종로구 사직로 161",
  "rating": 4.7,
  "thumbnailUrl": "https://image.example/gyeongbokgung.jpg",
  "openingHoursJson": null,
  "estimatedDuration": 90
}
```

## 참고
- DB의 `places`, `place_bookmarks`, `group_members.bookmark_count` 트리거와 연동된다.
- 투표 시작 후 합류한 멤버는 장바구니/투표 권한이 제한된다.

