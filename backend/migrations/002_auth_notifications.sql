ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified bool NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token text;
CREATE TABLE IF NOT EXISTS email_tokens(token_hash text PRIMARY KEY, user_id uuid NOT NULL REFERENCES users, purpose text NOT NULL CHECK(purpose IN('verify','reset')), expires_at timestamptz NOT NULL, used bool NOT NULL DEFAULT false);
CREATE TABLE IF NOT EXISTS notifications(id bigserial PRIMARY KEY, user_id uuid NOT NULL REFERENCES users, title text NOT NULL, body text, read bool NOT NULL DEFAULT false, created_at timestamptz DEFAULT now());
