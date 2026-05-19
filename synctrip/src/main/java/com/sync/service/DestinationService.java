package com.sync.service;

import com.sync.config.KakaoProperties;
import com.sync.dto.destination.DestinationResponse;
import com.sync.dto.kakao.KakaoLocalSearchResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class DestinationService {

    private static final Logger log = LoggerFactory.getLogger(DestinationService.class);

    // 한국 위도/경도 바운딩 박스
    private static final double KR_LAT_MIN = 33.0;
    private static final double KR_LAT_MAX = 38.9;
    private static final double KR_LNG_MIN = 124.0;
    private static final double KR_LNG_MAX = 132.0;

    private static final String KAKAO_KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    private static final String IMG = "https://images.unsplash.com/";

    // ────────────────────────────────────────────────────────────────────────
    // 큐레이션 인기 여행지 목록 (DB 없이 하드코딩)
    // ────────────────────────────────────────────────────────────────────────
    private static final List<DestinationResponse> POPULAR = List.of(

        // ── 일본 ──────────────────────────────────────────────────────────
        new DestinationResponse("도쿄", "일본", "JP",
                35.6762, 139.6503, true, "일본",
                "신주쿠, 시부야, 아키하바라",
                IMG + "photo-1540959179-fd27c7e4b24d?w=400&q=80"),
        new DestinationResponse("오사카", "일본", "JP",
                34.6937, 135.5023, true, "일본",
                "오사카, 교토, 고베, 나라",
                IMG + "photo-1528360983277-13d401cdc186?w=400&q=80"),
        new DestinationResponse("후쿠오카", "일본", "JP",
                33.5904, 130.4017, true, "일본",
                "후쿠오카, 유후인, 벳부",
                null),
        new DestinationResponse("삿포로", "일본", "JP",
                43.0618, 141.3545, true, "일본",
                "삿포로, 하코다테, 오타루",
                IMG + "photo-1536098561742-ca998e48cbcc?w=400&q=80"),
        new DestinationResponse("나고야", "일본", "JP",
                35.1815, 136.9066, true, "일본",
                "나고야, 다카야마, 시라카와고",
                null),

        // ── 동남아시아 ────────────────────────────────────────────────────
        new DestinationResponse("방콕", "태국", "TH",
                13.7563, 100.5018, true, "동남아시아",
                "방콕, 파타야, 아유타야",
                IMG + "photo-1563492065599-3520f775eeed?w=400&q=80"),
        new DestinationResponse("싱가포르", "싱가포르", "SG",
                1.3521, 103.8198, true, "동남아시아",
                "마리나베이, 센토사, 가든스바이더베이",
                IMG + "photo-1525625293386-3f8f99389edd?w=400&q=80"),
        new DestinationResponse("발리", "인도네시아", "ID",
                -8.3405, 115.0920, true, "동남아시아",
                "꾸따, 우붓, 스미냑",
                IMG + "photo-1537996194471-e657df975ab4?w=400&q=80"),
        new DestinationResponse("다낭", "베트남", "VN",
                16.0544, 108.2022, true, "동남아시아",
                "다낭, 호이안, 후에",
                null),
        new DestinationResponse("세부", "필리핀", "PH",
                10.3157, 123.8854, true, "동남아시아",
                "세부, 보홀, 막탄",
                null),

        // ── 유럽 ──────────────────────────────────────────────────────────
        new DestinationResponse("파리", "프랑스", "FR",
                48.8566, 2.3522, true, "유럽",
                "에펠탑, 루브르, 샹젤리제",
                IMG + "photo-1502602439-6c4e8291cf6b?w=400&q=80"),
        new DestinationResponse("런던", "영국", "GB",
                51.5074, -0.1278, true, "유럽",
                "빅벤, 타워브리지, 버킹엄궁전",
                IMG + "photo-1513635269975-59663e0ac1ad?w=400&q=80"),
        new DestinationResponse("바르셀로나", "스페인", "ES",
                41.3851, 2.1734, true, "유럽",
                "사그라다파밀리아, 람블라스, 가우디",
                IMG + "photo-1539037116277-4db20889f2d4?w=400&q=80"),
        new DestinationResponse("로마", "이탈리아", "IT",
                41.9028, 12.4964, true, "유럽",
                "콜로세움, 트레비분수, 바티칸",
                IMG + "photo-1552832230-c0197dd311b5?w=400&q=80"),
        new DestinationResponse("암스테르담", "네덜란드", "NL",
                52.3676, 4.9041, true, "유럽",
                "운하, 반고흐미술관, 안네의집",
                null),

        // ── 미주/오세아니아 ───────────────────────────────────────────────
        new DestinationResponse("뉴욕", "미국", "US",
                40.7128, -74.0060, true, "미주/오세아니아",
                "맨해튼, 센트럴파크, 자유의여신상",
                IMG + "photo-1538970272646-f61fabb3bcc2?w=400&q=80"),
        new DestinationResponse("하와이", "미국", "US",
                21.3069, -157.8583, true, "미주/오세아니아",
                "와이키키, 마우이, 빅아일랜드",
                IMG + "photo-1542259009477-d625272157b7?w=400&q=80"),
        new DestinationResponse("시드니", "호주", "AU",
                -33.8688, 151.2093, true, "미주/오세아니아",
                "오페라하우스, 본다이비치, 블루마운틴",
                IMG + "photo-1506973035872-a4ec16b8e8d9?w=400&q=80"),

        // ── 중화권 ────────────────────────────────────────────────────────
        new DestinationResponse("홍콩", "홍콩", "HK",
                22.3193, 114.1694, true, "중화권",
                "빅토리아피크, 침사추이, 란콰이펑",
                null),
        new DestinationResponse("타이베이", "대만", "TW",
                25.0330, 121.5654, true, "중화권",
                "지우펀, 101빌딩, 예류",
                null),

        // ── 국내 ──────────────────────────────────────────────────────────
        new DestinationResponse("서울", "대한민국", "KR",
                37.5665, 126.9780, false, "국내",
                "경복궁, 홍대, 명동, 한강",
                IMG + "photo-1546874177-9e664107314e?w=400&q=80"),
        new DestinationResponse("제주", "대한민국", "KR",
                33.4996, 126.5312, false, "국내",
                "성산일출봉, 한라산, 협재해수욕장",
                null),
        new DestinationResponse("부산", "대한민국", "KR",
                35.1796, 129.0756, false, "국내",
                "해운대, 광안리, 감천문화마을",
                null),
        new DestinationResponse("경주", "대한민국", "KR",
                35.8562, 129.2247, false, "국내",
                "불국사, 첨성대, 동궁과월지",
                null),
        new DestinationResponse("속초", "대한민국", "KR",
                38.2070, 128.5919, false, "국내",
                "설악산, 청초호, 속초해수욕장",
                null),
        new DestinationResponse("강릉", "대한민국", "KR",
                37.7519, 128.8760, false, "국내",
                "경포대, 정동진, 안목커피거리",
                null),
        new DestinationResponse("전주", "대한민국", "KR",
                35.8242, 127.1480, false, "국내",
                "전주한옥마을, 비빔밥, 막걸리골목",
                null),
        new DestinationResponse("여수", "대한민국", "KR",
                34.7604, 127.6622, false, "국내",
                "돌산도, 여수밤바다, 오동도",
                null)
    );

    private final RestTemplate restTemplate;
    private final KakaoProperties kakaoProperties;

    public DestinationService(RestTemplate restTemplate, KakaoProperties kakaoProperties) {
        this.restTemplate = restTemplate;
        this.kakaoProperties = kakaoProperties;
    }

    public List<DestinationResponse> getPopular() {
        return POPULAR;
    }

    public List<DestinationResponse> search(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(KAKAO_KEYWORD_URL)
                .queryParam("query", query)
                .queryParam("size", 5)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoProperties.clientId());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KakaoLocalSearchResponse> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, KakaoLocalSearchResponse.class);

            KakaoLocalSearchResponse body = response.getBody();
            if (body == null || body.documents() == null) return List.of();

            return body.documents().stream()
                    .filter(doc -> doc.x() != null && doc.y() != null
                            && !doc.x().isBlank() && !doc.y().isBlank())
                    .map(this::toDestination)
                    .toList();

        } catch (HttpStatusCodeException ex) {
            log.error("Kakao 검색 실패: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(BAD_GATEWAY, "장소 검색에 실패했습니다.", ex);
        }
    }

    private DestinationResponse toDestination(KakaoLocalSearchResponse.Document doc) {
        double lat = Double.parseDouble(doc.y());
        double lng = Double.parseDouble(doc.x());
        boolean overseas = !isKorea(lat, lng);
        String countryCode = overseas ? null : "KR";

        return new DestinationResponse(
                doc.placeName(),
                overseas ? "" : "대한민국",
                countryCode,
                lat,
                lng,
                overseas,
                overseas ? "해외" : "국내",
                doc.addressName(),
                null
        );
    }

    private boolean isKorea(double lat, double lng) {
        return lat >= KR_LAT_MIN && lat <= KR_LAT_MAX
                && lng >= KR_LNG_MIN && lng <= KR_LNG_MAX;
    }
}
