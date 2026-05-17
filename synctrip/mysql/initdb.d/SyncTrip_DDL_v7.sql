-- ════════════════════════════════════════
-- SyncTrip DDL v7
-- 작성일: 2026-05-18
-- 총 테이블: 17개 + 트리거 2개
-- ════════════════════════════════════════
-- v6 → v7 변경사항:
--   1. 테이블명 `groups` → `user_groups` 변경
--      → MySQL 예약어(GROUPS) 충돌 회피
--      → 모든 FK REFERENCES, 제약조건명, 인덱스명 동기 변경
--      → 코멘트 내 "FK → groups" 표기도 "FK → user_groups"로 변경
--   2. 소프트 삭제 컬럼 추가 (5개 테이블)
--      → user_groups, group_members, expenses, album_photos, passport_stamps
--      → `is_deleted BOOLEAN` + `deleted_at TIMESTAMP NULL` 쌍으로 추가
--      → 조회: WHERE deleted_at IS NULL (또는 is_deleted = FALSE)
--      → 휴지통 30일 자동삭제 등 향후 정책 대응 가능
--   3. users.deleted_at 추가 (기존 is_deleted와 페어링, 일관성 확보)
-- ════════════════════════════════════════
-- 소프트 삭제 정책 요약:
--   ✅ 적용: users, user_groups, group_members, expenses, album_photos, passport_stamps
--   ❌ 미적용 (하드삭제 유지):
--      - place_bookmarks, schedule_alts → 일시적 데이터, 재생성됨
--      - votes → UPDATE 금지 정책상 DELETE도 발생 안 함
--      - notifications → 일시적, 향후 TTL 정책 검토
--      - schedules → user_groups 소프트삭제로 자연 보존, 개별 슬롯 삭제는 의도된 동작
--      - places → 공유 캐시, 그룹 무관하게 누적
--      - group_vote_info, group_finance, group_exchange_rates → user_groups cascade
--      - expense_members → expenses cascade
-- ════════════════════════════════════════
-- 인덱스 권장사항 (구현 시 추가):
--   - 자주 조회되는 컬럼은 (deleted_at) 또는 (xxx_id, deleted_at) 복합 인덱스 검토
--   - 예: group_members (group_id, deleted_at), expenses (group_id, deleted_at)
-- ════════════════════════════════════════
-- v5 → v6 변경사항 (의사코드 v2.4 정합성):
--   1. group_members.joined_after_voting 컬럼 추가
--   2. places.opening_hours 코멘트 보강
--   3. schedule_alts.alt_rank 제거
-- ════════════════════════════════════════
-- v4 → v5 변경사항:
--   1. notifications.type: VARCHAR(50) → ENUM(4종) 전환
--   2. schedules.cluster_id 제거 (day_number와 1:1 중복)
--   3. votes.voted_at 코멘트 보강 (UPDATE 금지 정책 명시)
-- ════════════════════════════════════════


-- 1. users
-- [v7 수정] deleted_at 컬럼 추가 (기존 is_deleted와 페어링)
CREATE TABLE `users` (
                         `user_id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 고유 ID',
                         `email`                   VARCHAR(255) NULL UNIQUE             COMMENT '로그인 이메일 (소셜 제공자에 따라 NULL 가능)',
                         `name`                    VARCHAR(50)  NOT NULL                COMMENT '회원 이름',
                         `profile_image_url`       VARCHAR(500) NULL                    COMMENT '프로필 이미지 URL (소셜 로그인 제공)',
                         `oauth_provider`          VARCHAR(20)  NOT NULL                COMMENT '소셜 로그인 제공자 (KAKAO / GOOGLE)',
                         `oauth_id`                VARCHAR(100) NOT NULL                COMMENT '소셜 로그인 제공자 고유 ID',
                         `noti_vote_started`       BOOLEAN      NOT NULL DEFAULT TRUE   COMMENT '투표 시작 알림',
                         `noti_schedule_updated`   BOOLEAN      NOT NULL DEFAULT TRUE   COMMENT '일정 변경 알림',
                         `noti_settlement_request` BOOLEAN      NOT NULL DEFAULT TRUE   COMMENT '정산 요청 알림',
                         `noti_member_ready`       BOOLEAN      NOT NULL DEFAULT TRUE   COMMENT '멤버 Ready 알림',
                         `is_deleted`              BOOLEAN      NOT NULL DEFAULT FALSE  COMMENT '탈퇴 여부 (Soft Delete)',
                         `deleted_at`              TIMESTAMP    NULL                    COMMENT '탈퇴 시각 (NULL=정상회원, 휴지통/재가입 정책용)',
                         `created_at`              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입일자',
                         `updated_at`              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '정보 수정일자',
                         PRIMARY KEY (`user_id`),
                         UNIQUE KEY `uq_oauth` (`oauth_provider`, `oauth_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 2. user_groups (구 groups, MySQL 예약어 회피)
-- [v7 수정] 테이블명 변경 + 소프트 삭제 컬럼 추가
CREATE TABLE `user_groups` (
                               `group_id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '그룹 고유 ID',
                               `owner_id`               BIGINT       NOT NULL                COMMENT '방장 회원 ID (FK → users)',
                               `title`                  VARCHAR(100) NOT NULL                COMMENT '여행 제목',
                               `destination`            VARCHAR(100) NOT NULL                COMMENT '여행지명',
                               `destination_lat`        DOUBLE       NOT NULL                COMMENT '여행지 중심 위도 (K-Means centroid 기준점 / 숙소 미입력 시 TSP 출발점)',
                               `destination_lng`        DOUBLE       NOT NULL                COMMENT '여행지 중심 경도',
                               `country_code`           VARCHAR(5)   NOT NULL                COMMENT '국가 코드 (KR, JP 등 Nager.Date API용)',
                               `is_overseas`            BOOLEAN      NOT NULL DEFAULT FALSE  COMMENT '해외 여행 여부 (FALSE=카카오맵, TRUE=구글)',
                               `start_date`             DATE         NOT NULL                COMMENT '여행 시작일',
                               `end_date`               DATE         NOT NULL                COMMENT '여행 종료일',
                               `invite_code`            VARCHAR(20)  NOT NULL                COMMENT '그룹 초대 코드 (6자리)',
                               `invite_code_expired_at` TIMESTAMP    NOT NULL                COMMENT '초대 코드 만료 시각 (72시간)',
                               `max_members`            INT          NOT NULL DEFAULT 8      COMMENT '그룹 최대 인원 (최대 8명)',
                               `travel_style`           ENUM('RELAXED','PACKED') NOT NULL DEFAULT 'PACKED' COMMENT '여행 스타일 (RELAXED=여유롭게, PACKED=빡빡하게)',
                               `accommodation_name`     VARCHAR(100) NULL                    COMMENT '숙소명 (선택사항)',
                               `accommodation_lat`      DOUBLE       NULL                    COMMENT '숙소 위도 (NULL이면 destination_lat을 TSP 출발점으로 사용)',
                               `accommodation_lng`      DOUBLE       NULL                    COMMENT '숙소 경도 (NULL이면 destination_lng을 TSP 출발점으로 사용)',
                               `status`                 ENUM('PLANNING','VOTING','GENERATING','TRAVELLING','DONE') NOT NULL DEFAULT 'PLANNING' COMMENT '그룹 상태',
                               `closed_by`              VARCHAR(20)  NULL                    COMMENT '여행 종료 주체 (AUTO / OWNER)',
                               `is_deleted`              BOOLEAN     NOT NULL DEFAULT FALSE  COMMENT '그룹 삭제 여부 (Soft Delete) — USR-025 과거 여행 기록 보존',
                               `deleted_at`              TIMESTAMP   NULL                    COMMENT '삭제 시각 (NULL=활성, 휴지통/복구 정책용)',
                               `created_at`             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '그룹 생성일자',
                               `updated_at`             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '그룹 수정일자',
                               PRIMARY KEY (`group_id`),
                               UNIQUE KEY `uq_user_groups_invite_code` (`invite_code`),
                               CONSTRAINT `fk_user_groups_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 3. group_vote_info
CREATE TABLE `group_vote_info` (
                                   `group_vote_info_id` BIGINT    NOT NULL AUTO_INCREMENT COMMENT '투표 정보 고유 ID',
                                   `group_id`           BIGINT    NOT NULL UNIQUE         COMMENT '그룹 ID (FK → user_groups)',
                                   `vote_started_at`    TIMESTAMP NULL                    COMMENT '투표 시작 시각',
                                   `vote_ended_at`      TIMESTAMP NULL                    COMMENT '투표 종료 시각',
                                   `is_force_started`   BOOLEAN   NOT NULL DEFAULT FALSE  COMMENT '방장 강제 시작 여부 (FALSE=전원 Ready 자동시작 / TRUE=방장 수동시작)',
                                   PRIMARY KEY (`group_vote_info_id`),
                                   CONSTRAINT `fk_group_vote_info_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 4. group_finance
CREATE TABLE `group_finance` (
                                 `group_finance_id` BIGINT      NOT NULL AUTO_INCREMENT COMMENT '재정 정보 고유 ID',
                                 `group_id`         BIGINT      NOT NULL UNIQUE         COMMENT '그룹 ID (FK → user_groups)',
                                 `base_currency`    VARCHAR(10) NOT NULL DEFAULT 'KRW'  COMMENT '그룹 공통 기준 통화 (그룹 생성 시 국가코드 기반 자동 세팅)',
                                 PRIMARY KEY (`group_finance_id`),
                                 CONSTRAINT `fk_group_finance_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 5. group_exchange_rates
CREATE TABLE `group_exchange_rates` (
                                        `group_exchange_rate_id` BIGINT         NOT NULL AUTO_INCREMENT COMMENT '환율 고유 ID',
                                        `group_id`               BIGINT         NOT NULL                COMMENT '그룹 ID (FK → user_groups)',
                                        `currency`               VARCHAR(10)    NOT NULL                COMMENT '통화 코드 (USD, JPY 등)',
                                        `exchange_rate`          DECIMAL(10, 4) NOT NULL                COMMENT 'base_currency 기준 환율',
                                        `rate_updated_at`        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '환율 업데이트 시각',
                                        PRIMARY KEY (`group_exchange_rate_id`),
                                        UNIQUE KEY `uq_group_exchange_rates` (`group_id`, `currency`),
                                        CONSTRAINT `fk_group_exchange_rates_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 6. group_members
-- [v6 수정 1] joined_after_voting 컬럼 추가 (TRAVELLING/DONE 단계 가입자 권한 제한)
-- [v7 수정] 소프트 삭제 컬럼 추가 (탈퇴 멤버의 정산/앨범 기록 보존)
CREATE TABLE `group_members` (
                                 `group_member_id`      BIGINT                 NOT NULL AUTO_INCREMENT COMMENT '그룹 멤버 고유 ID',
                                 `group_id`             BIGINT                 NOT NULL                COMMENT '그룹 ID (FK → user_groups)',
                                 `user_id`              BIGINT                 NOT NULL                COMMENT '회원 ID (FK → users)',
                                 `role`                 ENUM('OWNER','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '역할',
                                 `is_ready`             BOOLEAN                NOT NULL DEFAULT FALSE  COMMENT 'Ready 상태 (한 번 TRUE로 설정 후 해제 불가 — 백엔드 방어)',
                                 `bookmark_count`       INT                    NOT NULL DEFAULT 0      COMMENT '현재 담기 개수 (1인당 최대 5개 제한 체크용 / place_bookmarks 트리거로 자동 동기화)',
                                 `joined_after_voting`  BOOLEAN                NOT NULL DEFAULT FALSE  COMMENT '투표 종료 후 가입 여부 (TRUE=권한 제한: 장바구니 추가/투표 불가, 일정 보기/가계부/앨범/Plan B만 가능)',
                                 `is_deleted`           BOOLEAN                NOT NULL DEFAULT FALSE  COMMENT '탈퇴 여부 (Soft Delete) — expense_members/album_photos FK 무결성 보존',
                                 `deleted_at`           TIMESTAMP              NULL                    COMMENT '탈퇴 시각 (NULL=정상 멤버)',
                                 `joined_at`            TIMESTAMP              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '그룹 참여일자',
                                 PRIMARY KEY (`group_member_id`),
                                 UNIQUE KEY `uq_group_members` (`group_id`, `user_id`),
                                 CONSTRAINT `fk_group_members_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`),
                                 CONSTRAINT `fk_group_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 7. places
CREATE TABLE `places` (
                          `place_id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '장소 고유 ID',
                          `api_source`         VARCHAR(20)  NOT NULL                COMMENT 'KAKAO / GOOGLE',
                          `external_id`        VARCHAR(100) NOT NULL                COMMENT '외부 API 장소 ID',
                          `name`               VARCHAR(100) NOT NULL                COMMENT '장소명',
                          `category`           ENUM('FOOD','CULTURE','ACTIVITY','SHOPPING','NATURE','ETC') NOT NULL COMMENT '카테고리',
                          `density_point`      INT          NOT NULL DEFAULT 1      COMMENT 'Density Point (ACTIVITY=3 / CULTURE,NATURE=2 / SHOPPING,ETC=1 / FOOD=0, FOOD는 쿼터로 별도 편입되어 합산 제외)',
                          `latitude`           DOUBLE       NOT NULL                COMMENT '위도',
                          `longitude`          DOUBLE       NOT NULL                COMMENT '경도',
                          `address`            VARCHAR(255) NULL                    COMMENT '주소',
                          `rating`             FLOAT        NULL                    COMMENT '평점',
                          `thumbnail_url`      VARCHAR(500) NULL                    COMMENT '장소 썸네일 이미지 URL',
                          `opening_hours`      JSON         NULL                    COMMENT '요일별 영업시간 (해외 전용 / 국내는 항상 NULL — 카카오 API 미제공). 스키마: {"MON":[{"open":"09:00","close":"22:00"}],...}',
                          `estimated_duration` INT          NOT NULL DEFAULT 60     COMMENT '예상 체류시간 (분, 카테고리 기본값: FOOD=60 / CULTURE=90 / ACTIVITY=120 / SHOPPING=60 / NATURE=90)',
                          PRIMARY KEY (`place_id`),
                          UNIQUE KEY `uq_places_source` (`api_source`, `external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 8. place_bookmarks
CREATE TABLE `place_bookmarks` (
                                   `place_bookmark_id` BIGINT    NOT NULL AUTO_INCREMENT COMMENT '장바구니 고유 ID',
                                   `group_id`          BIGINT    NOT NULL                COMMENT '그룹 ID (FK → user_groups)',
                                   `user_id`           BIGINT    NOT NULL                COMMENT '담은 회원 ID (FK → users)',
                                   `place_id`          BIGINT    NOT NULL                COMMENT '장소 ID (FK → places)',
                                   `created_at`        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '담은 일자',
                                   PRIMARY KEY (`place_bookmark_id`),
                                   UNIQUE KEY `uq_place_bookmarks` (`group_id`, `user_id`, `place_id`),
                                   CONSTRAINT `fk_place_bookmarks_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`),
                                   CONSTRAINT `fk_place_bookmarks_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
                                   CONSTRAINT `fk_place_bookmarks_place` FOREIGN KEY (`place_id`) REFERENCES `places` (`place_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 9. votes
-- [v5 수정 3] voted_at 코멘트 보강 (UPDATE 금지 정책 명시)
CREATE TABLE `votes` (
                         `vote_id`  BIGINT    NOT NULL AUTO_INCREMENT COMMENT '투표 고유 ID',
                         `group_id` BIGINT    NOT NULL                COMMENT '그룹 ID (FK → user_groups)',
                         `user_id`  BIGINT    NOT NULL                COMMENT '투표한 회원 ID (FK → users)',
                         `place_id` BIGINT    NOT NULL                COMMENT '투표 대상 장소 ID (FK → places)',
                         `result`   TINYINT   NOT NULL                COMMENT '1=LIKE / -1=DISLIKE / 0=자동LIKE(본인 장소)',
                         `voted_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '투표 일시 (UPDATE 금지 — 블라인드 투표 철학, 재투표는 uq_votes UNIQUE KEY로 DB 차단)',
                         PRIMARY KEY (`vote_id`),
                         UNIQUE KEY `uq_votes` (`group_id`, `user_id`, `place_id`),
                         CONSTRAINT `fk_votes_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`),
                         CONSTRAINT `fk_votes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
                         CONSTRAINT `fk_votes_place` FOREIGN KEY (`place_id`) REFERENCES `places` (`place_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 10. schedules
-- [v5 수정 2] cluster_id 제거 (day_number와 1:1 중복)
CREATE TABLE `schedules` (
                             `schedule_id`           BIGINT    NOT NULL AUTO_INCREMENT COMMENT '일정 고유 ID',
                             `group_id`              BIGINT    NOT NULL                COMMENT '그룹 ID (FK → user_groups)',
                             `day_number`            INT       NOT NULL                COMMENT '여행 일차 (1부터 시작, K-Means 클러스터 ID와 동일)',
                             `slot_order`            INT       NOT NULL                COMMENT '하루 내 방문 순서',
                             `place_id`              BIGINT    NULL                    COMMENT '배정된 장소 ID (NULL이면 자유시간 슬롯)',
                             `is_free_time`          BOOLEAN   NOT NULL DEFAULT FALSE  COMMENT '자유시간 여부 (TRUE이면 place_id NULL 강제)',
                             `start_time`            TIME      NULL                    COMMENT '방문 시작 시각',
                             `duration_minutes`      INT       NULL                    COMMENT '체류/자유시간 길이(분). 일반 장소는 places.estimated_duration 참조용 캐시, 자유시간 슬롯은 이 컬럼만 사용',
                             `travel_time_from_prev` INT       NULL                    COMMENT '이전 장소 이동 시간 (분)',
                             `updated_at`            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종 수정일시',
                             PRIMARY KEY (`schedule_id`),
                             UNIQUE KEY `uq_schedules_slot` (`group_id`, `day_number`, `slot_order`),
                             CONSTRAINT `fk_schedules_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`),
                             CONSTRAINT `fk_schedules_place` FOREIGN KEY (`place_id`) REFERENCES `places` (`place_id`),
                             CONSTRAINT `chk_schedules_free_time` CHECK (
                                 (is_free_time = TRUE  AND place_id IS NULL) OR
                                 (is_free_time = FALSE AND place_id IS NOT NULL)
                                 )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 11. schedule_alts
-- [v6 수정 3] alt_rank 컬럼 제거 (Plan B 폭포수 검색이 동적 정렬 사용 — 의사코드 v2.3 §7.3)
CREATE TABLE `schedule_alts` (
                                 `schedule_alt_id` BIGINT      NOT NULL AUTO_INCREMENT COMMENT '대안 장소 고유 ID',
                                 `group_id`        BIGINT      NOT NULL                COMMENT '그룹 ID (FK → user_groups)',
                                 `place_id`        BIGINT      NOT NULL                COMMENT '대안 장소 ID (FK → places)',
                                 `category`        ENUM('FOOD','CULTURE','ACTIVITY','SHOPPING','NATURE','ETC') NOT NULL COMMENT '카테고리',
                                 `density_point`   INT         NOT NULL                COMMENT 'Density Point (places.density_point 캐시)',
                                 `priority_score`  FLOAT       NOT NULL                COMMENT 'Weighted Cost 기반 우선순위 점수 (Plan B 폭포수 검색 시 정렬 키)',
                                 PRIMARY KEY (`schedule_alt_id`),
                                 UNIQUE KEY `uq_schedule_alts` (`group_id`, `place_id`),
                                 CONSTRAINT `fk_schedule_alts_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`),
                                 CONSTRAINT `fk_schedule_alts_place` FOREIGN KEY (`place_id`) REFERENCES `places` (`place_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 12. expenses
-- [v7 수정] 소프트 삭제 컬럼 추가 (정산 이력 보존 — 실수 복구 + 분쟁 추적)
CREATE TABLE `expenses` (
                            `expense_id`  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '지출 고유 ID',
                            `group_id`    BIGINT         NOT NULL                COMMENT '그룹 ID (FK → user_groups)',
                            `payer_id`    BIGINT         NOT NULL                COMMENT '결제자 회원 ID (FK → users)',
                            `item_name`   VARCHAR(100)   NOT NULL                COMMENT '지출 항목명',
                            `amount`      DECIMAL(12, 2) NOT NULL                COMMENT '결제 금액',
                            `currency`    VARCHAR(10)    NOT NULL DEFAULT 'KRW'  COMMENT '통화 코드',
                            `receipt_url` VARCHAR(500)   NULL                    COMMENT '영수증 이미지 URL',
                            `ocr_raw`     JSON           NULL                    COMMENT 'Vision AI 추출 원본',
                            `is_deleted`  BOOLEAN        NOT NULL DEFAULT FALSE  COMMENT '삭제 여부 (Soft Delete) — 정산 무결성 보존',
                            `deleted_at`  TIMESTAMP      NULL                    COMMENT '삭제 시각 (NULL=유효 지출)',
                            `paid_at`     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제 일시',
                            PRIMARY KEY (`expense_id`),
                            CONSTRAINT `fk_expenses_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`),
                            CONSTRAINT `fk_expenses_payer` FOREIGN KEY (`payer_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 13. expense_members
CREATE TABLE `expense_members` (
                                   `expense_member_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '분담 고유 ID',
                                   `expense_id`        BIGINT NOT NULL                COMMENT '지출 ID (FK → expenses)',
                                   `user_id`           BIGINT NOT NULL                COMMENT '분담 회원 ID (FK → users)',
                                   PRIMARY KEY (`expense_member_id`),
                                   UNIQUE KEY `uq_expense_members` (`expense_id`, `user_id`),
                                   CONSTRAINT `fk_expense_members_expense` FOREIGN KEY (`expense_id`) REFERENCES `expenses` (`expense_id`),
                                   CONSTRAINT `fk_expense_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 14. notifications
-- [v5 수정 1] type 컬럼 VARCHAR(50) → ENUM(4종) 전환
-- 알림 종류 추가 필요 시 ALTER TABLE ... MODIFY COLUMN으로 ENUM 확장
CREATE TABLE `notifications` (
                                 `notification_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '알림 고유 ID',
                                 `user_id`         BIGINT       NOT NULL                COMMENT '수신 회원 ID (FK → users)',
                                 `group_id`        BIGINT       NULL                    COMMENT '관련 그룹 ID (FK → user_groups)',
                                 `type`            ENUM(
                      'MEMBER_READY',
                      'VOTE_STARTED',
                      'SCHEDULE_UPDATED',
                      'SETTLEMENT_REQUEST'
                    ) NOT NULL COMMENT '알림 종류',
                                 `content`         VARCHAR(255) NOT NULL                COMMENT '알림 내용',
                                 `is_read`         BOOLEAN      NOT NULL DEFAULT FALSE  COMMENT '읽음 여부',
                                 `created_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '알림 생성일시',
                                 PRIMARY KEY (`notification_id`),
                                 CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
                                 CONSTRAINT `fk_notifications_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 15. album_photos
-- [v7 수정] 소프트 삭제 컬럼 추가 (휴지통 패턴 — 실수 복구)
CREATE TABLE `album_photos` (
                                `album_photo_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '앨범 사진 고유 ID',
                                `group_id`       BIGINT       NOT NULL                COMMENT '그룹 ID (FK → user_groups)',
                                `uploader_id`    BIGINT       NOT NULL                COMMENT '업로드한 회원 ID (FK → users)',
                                `photo_url`      VARCHAR(500) NOT NULL                COMMENT '사진 저장 URL',
                                `taken_at`       TIMESTAMP    NULL                    COMMENT '촬영 시각',
                                `is_deleted`     BOOLEAN      NOT NULL DEFAULT FALSE  COMMENT '삭제 여부 (Soft Delete) — 휴지통 30일 보관 후 자동삭제 정책 대응',
                                `deleted_at`     TIMESTAMP    NULL                    COMMENT '삭제 시각 (NULL=활성 사진)',
                                `uploaded_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업로드 일시',
                                PRIMARY KEY (`album_photo_id`),
                                CONSTRAINT `fk_album_photos_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`),
                                CONSTRAINT `fk_album_photos_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 16. passport_stamps
-- [v7 수정] 소프트 삭제 컬럼 추가 (여행 기록 영구 보존, 실수 삭제 방지)
CREATE TABLE `passport_stamps` (
                                   `passport_stamp_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '여권 스탬프 고유 ID',
                                   `user_id`           BIGINT       NOT NULL                COMMENT '회원 ID (FK → users)',
                                   `group_id`          BIGINT       NULL                    COMMENT '여행 그룹 ID (FK → user_groups)',
                                   `city`              VARCHAR(100) NOT NULL                COMMENT '다녀온 도시명',
                                   `country`           VARCHAR(100) NULL                    COMMENT '다녀온 국가명',
                                   `country_code`      VARCHAR(5)   NULL                    COMMENT '국가 코드 (국기 이미지 표시용)',
                                   `is_deleted`        BOOLEAN      NOT NULL DEFAULT FALSE  COMMENT '삭제 여부 (Soft Delete) — 여행 기록 보존',
                                   `deleted_at`        TIMESTAMP    NULL                    COMMENT '삭제 시각 (NULL=활성 스탬프)',
                                   `stamped_at`        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '스탬프 생성 일시',
                                   PRIMARY KEY (`passport_stamp_id`),
                                   CONSTRAINT `fk_passport_stamps_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
                                   CONSTRAINT `fk_passport_stamps_group` FOREIGN KEY (`group_id`) REFERENCES `user_groups` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ════════════════════════════════════════
-- 트리거: place_bookmarks ↔ group_members.bookmark_count 동기화
-- 백엔드 누락으로 인한 정합성 깨짐 차단 (v4에서 도입)
-- ════════════════════════════════════════

DELIMITER $$

CREATE TRIGGER `trg_bookmark_insert`
    AFTER INSERT ON `place_bookmarks`
    FOR EACH ROW
BEGIN
    UPDATE `group_members`
    SET `bookmark_count` = `bookmark_count` + 1
    WHERE `group_id` = NEW.`group_id`
      AND `user_id`  = NEW.`user_id`;
    END$$

    CREATE TRIGGER `trg_bookmark_delete`
        AFTER DELETE ON `place_bookmarks`
        FOR EACH ROW
    BEGIN
        UPDATE `group_members`
        SET `bookmark_count` = `bookmark_count` - 1
        WHERE `group_id` = OLD.`group_id`
          AND `user_id`  = OLD.`user_id`;
        END$$

        DELIMITER ;