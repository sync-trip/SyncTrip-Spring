package com.sync.domain.notification;

/**
 * 알림 종류 정의
 * - title: 푸시 알림 상단에 표시되는 제목 (FCM 알림 타이틀로 사용)
 */
public enum NotificationType {
    MEMBER_READY("멤버 준비완료"),    // 밴드원 한 명이 준비 완료 버튼을 눌렀을 때
    MEMBER_JOINED("새 멤버 합류"),    // 초대 코드로 새 멤버가 밴드에 합류했을 때
    VOTE_STARTED("투표 시작"),        // 모든 멤버가 준비 완료되어 투표 단계로 전환됐을 때
    SCHEDULE_UPDATED("일정 변경"),    // 일정이 자동 생성되거나 Plan B로 수동 교체됐을 때
    SETTLEMENT_REQUEST("정산 요청"),  // 밴드 멤버가 정산 요청 버튼을 눌렀을 때
    TRIP_ENDED("여행 종료"),          // TRAVELLING → DONE 전환 시
    HOLIDAY_WARNING("현지 공휴일 안내"); // 여행 기간 중 현지 공휴일이 있을 때

    private final String title;

    NotificationType(String title) {
        this.title = title;
    }

    // FCM 푸시 알림의 타이틀로 사용
    public String getTitle() {
        return title;
    }
}
