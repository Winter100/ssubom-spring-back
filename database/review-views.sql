-- PostgreSQL 17: 애플리케이션 배포 전에 적용합니다. 기존 게시글은 집계 행이 없으면 0입니다.
BEGIN;

CREATE TABLE IF NOT EXISTS review_view_counts (
    review_id BIGINT PRIMARY KEY REFERENCES reviews(id) ON DELETE CASCADE,
    view_count BIGINT NOT NULL
);

COMMIT;
