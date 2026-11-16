-- 기존 회원은 변경 기록이 없으므로 첫 닉네임 변경을 허용합니다.
ALTER TABLE members ADD COLUMN IF NOT EXISTS nickname_changed_at TIMESTAMP WITH TIME ZONE;
