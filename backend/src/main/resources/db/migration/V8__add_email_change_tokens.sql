ALTER TABLE user_tokens ADD COLUMN target_email VARCHAR(255);
ALTER TABLE user_tokens DROP CONSTRAINT chk_user_tokens_type;
ALTER TABLE user_tokens ADD CONSTRAINT chk_user_tokens_type
    CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE'));
ALTER TABLE user_tokens ADD CONSTRAINT chk_user_tokens_target_email CHECK (
    (type = 'EMAIL_CHANGE' AND target_email IS NOT NULL AND BTRIM(target_email) <> '')
    OR (type <> 'EMAIL_CHANGE' AND target_email IS NULL)
);
