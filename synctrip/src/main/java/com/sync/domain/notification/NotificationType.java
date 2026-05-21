package com.sync.domain.notification;

public enum NotificationType {
    MEMBER_READY("멤버 준비완료"),
    VOTE_STARTED("투표 시작"),
    SCHEDULE_UPDATED("일정 변경"),
    SETTLEMENT_REQUEST("정산 요청");

    private final String title;

    NotificationType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
