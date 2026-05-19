package com.sync.controller;

import com.sync.dto.destination.DestinationResponse;
import com.sync.service.DestinationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/destinations")
public class DestinationController {

    private final DestinationService destinationService;

    public DestinationController(DestinationService destinationService) {
        this.destinationService = destinationService;
    }

    /**
     * 인기 여행지 큐레이션 목록
     * - region 필드로 국내/해외/지역별 그룹핑 가능
     */
    @GetMapping("/popular")
    public ResponseEntity<List<DestinationResponse>> getPopular() {
        return ResponseEntity.ok(destinationService.getPopular());
    }

    /**
     * 도시 검색 (카카오 키워드 검색 프록시)
     * - Android가 카카오 REST 키를 직접 노출하지 않아도 됨
     */
    @GetMapping("/search")
    public ResponseEntity<List<DestinationResponse>> search(
            @RequestParam String query
    ) {
        return ResponseEntity.ok(destinationService.search(query));
    }
}
