-- MySQL 컨테이너가 처음 초기화될 때 자동 실행되는 예시 SQL
-- 주의: 이 스크립트는 /var/lib/mysql 데이터 디렉토리가 비어 있을 때만 1회 실행된다.

-- 예시 1) 간단한 사용자 메모 테이블
CREATE TABLE IF NOT EXISTS app_notes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    content TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 예시 2) 샘플 데이터 삽입
INSERT INTO app_notes (title, content)
VALUES
    ('첫 번째 메모', 'MySQL 초기화 스크립트로 자동 생성된 예시 데이터입니다.'),
    ('두 번째 메모', '여기에 원하는 CREATE / INSERT / UPDATE SQL을 추가하면 됩니다.');

-- 예시 3) MySQL 사용자 권한이 필요하면 직접 추가 가능
-- (compose.yml의 MYSQL_USER/MYSQL_PASSWORD가 이미 생성한 계정에 권한을 주므로,
--  보통은 추가 GRANT가 필요하지 않다. 필요 시 아래처럼 사용)
-- GRANT ALL PRIVILEGES ON synctripdb.* TO 'synctrip'@'%';
-- FLUSH PRIVILEGES;

