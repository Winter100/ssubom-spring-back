-- PostgreSQL: 기존 회원 데이터는 유지합니다. 애플리케이션 배포 전에 적용합니다.
BEGIN;
ALTER TABLE members ADD COLUMN IF NOT EXISTS withdrawal_requested_at timestamp with time zone;
ALTER TABLE members ADD COLUMN IF NOT EXISTS withdrawal_scheduled_at timestamp with time zone;
CREATE INDEX IF NOT EXISTS idx_members_withdrawal_scheduled ON members (withdrawal_scheduled_at);
COMMIT;
