package com.sync.dto.vote;

public record VoteRequest(Long placeId, int result) {
    // result: 1=LIKE, -1=DISLIKE (0은 서버가 본인 북마크 여부에 따라 자동 설정)
}
