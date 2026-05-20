package com.sync.service;

import com.sync.config.KakaoProperties;
import com.sync.domain.place.PlaceCategory;
import com.sync.dto.kakao.KakaoLocalSearchResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoPlacesServiceTest {

    @Test
    void searchNearby_usesCategorySearchAndReturnsDocuments() {
        RestTemplate restTemplate = new RestTemplate();
        KakaoProperties properties = new KakaoProperties(
                "https://kauth.kakao.com/oauth/authorize",
                "https://kapi.kakao.com/oauth/token",
                "https://kapi.kakao.com/v2/user/me",
                "https://dapi.kakao.com/v2/local/search/category.json",
                "client-id",
                "client-secret",
                "http://localhost:8080/auth/kakao/callback",
                "account_email,profile_nickname,profile_image"
        );

        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://dapi.kakao.com/v2/local/search/category.json?category_group_code=PK6&x=126.978&y=37.5665&radius=5000&sort=distance&size=15"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "KakaoAK client-id"))
                .andRespond(withSuccess("""
                        {
                          "documents": [
                            {
                              "id": "kakao-1",
                              "place_name": "서울숲",
                              "category_name": "공원",
                              "category_group_code": "PK6",
                              "category_group_name": "공원",
                              "address_name": "서울 성동구 성수동1가",
                              "road_address_name": "서울 성동구 뚝섬로 273",
                              "x": "127.0421",
                              "y": "37.5446",
                              "phone": "02-123-4567",
                              "place_url": "https://place.map.kakao.com/1",
                              "distance": "320"
                            }
                          ],
                          "meta": {
                            "total_count": 1,
                            "is_end": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        KakaoPlacesService service = new KakaoPlacesService(restTemplate, properties);
        List<KakaoLocalSearchResponse.Document> documents = service.searchNearby(
                37.5665, 126.9780, 5000, PlaceCategory.NATURE);

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).id()).isEqualTo("kakao-1");
        assertThat(documents.get(0).placeName()).isEqualTo("서울숲");
        assertThat(documents.get(0).categoryGroupCode()).isEqualTo("PK6");

        server.verify();
    }
}

