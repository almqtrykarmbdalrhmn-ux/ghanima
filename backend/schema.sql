CREATE TABLE users(id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email text UNIQUE NOT NULL, pass_hash text NOT NULL,
 role text NOT NULL DEFAULT 'user' CHECK(role IN('user','admin')), balance bigint NOT NULL DEFAULT 0 CHECK(balance>=0), created_at timestamptz DEFAULT now());
CREATE TABLE packages(id serial PRIMARY KEY, game text NOT NULL CHECK(game IN('pubg','freefire')), amount int NOT NULL, price_points int NOT NULL CHECK(price_points>0), active bool DEFAULT true);
CREATE TABLE ledger(id bigserial PRIMARY KEY, user_id uuid NOT NULL REFERENCES users, kind text NOT NULL CHECK(kind IN('ad_reward','reserve','release','admin_adjust')),
 amount bigint NOT NULL, ref text UNIQUE, created_at timestamptz DEFAULT now());
CREATE TABLE orders(id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users, package_id int NOT NULL REFERENCES packages,
 player_id text NOT NULL, server text, cost bigint NOT NULL, status text NOT NULL DEFAULT 'pending' CHECK(status IN('pending','under_review','processing','completed','rejected')), created_at timestamptz DEFAULT now());
CREATE TABLE audit_log(id bigserial PRIMARY KEY, actor uuid, action text, detail jsonb, at timestamptz DEFAULT now());
CREATE TABLE settings(key text PRIMARY KEY, value text);
INSERT INTO settings VALUES('daily_ad_limit','200'),('ads_enabled','false'),('ad_interval_seconds','60');
-- ledger is append-only; balance is updated only by this trigger, so it always equals SUM(ledger)
CREATE FUNCTION ledger_guard() RETURNS trigger AS $$ BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'ledger is immutable'; END IF;
 UPDATE users SET balance=balance+NEW.amount WHERE id=NEW.user_id; RETURN NEW; END $$ LANGUAGE plpgsql;
CREATE TRIGGER ledger_ins AFTER INSERT ON ledger FOR EACH ROW EXECUTE FUNCTION ledger_guard();
CREATE TRIGGER ledger_ro BEFORE UPDATE OR DELETE ON ledger FOR EACH ROW EXECUTE FUNCTION ledger_guard();
INSERT INTO packages(game,amount,price_points) VALUES('pubg',60,3000),('pubg',325,8000),('pubg',600,14000),('pubg',1000,22000),('pubg',1800,38000),('pubg',3850,75000),('pubg',8000,145000),
('freefire',100,2000),('freefire',210,4000),('freefire',310,6000),('freefire',520,10000),('freefire',1060,19000),('freefire',2180,38000),('freefire',5600,90000);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified bool NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token text;
CREATE TABLE IF NOT EXISTS email_tokens(token_hash text PRIMARY KEY, user_id uuid NOT NULL REFERENCES users, purpose text NOT NULL CHECK(purpose IN('verify','reset')), expires_at timestamptz NOT NULL, used bool NOT NULL DEFAULT false);
CREATE TABLE IF NOT EXISTS notifications(id bigserial PRIMARY KEY, user_id uuid NOT NULL REFERENCES users, title text NOT NULL, body text, read bool NOT NULL DEFAULT false, created_at timestamptz DEFAULT now());
