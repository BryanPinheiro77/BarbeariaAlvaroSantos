-- Apply before deploying the refresh-token API. Existing tables are untouched.
CREATE TABLE IF NOT EXISTS refresh_sessions (
    token_hash varchar(64) PRIMARY KEY,
    email varchar(320) NOT NULL,
    tipo varchar(10) NOT NULL CHECK (tipo IN ('ADMIN', 'CLIENTE')),
    expires_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS refresh_sessions_expiry ON refresh_sessions(expires_at);
-- Supabase exposes public tables through its Data API. Only the JDBC owner may access sessions.
ALTER TABLE refresh_sessions ENABLE ROW LEVEL SECURITY;
-- Maintenance: DELETE FROM refresh_sessions WHERE expires_at < now();
