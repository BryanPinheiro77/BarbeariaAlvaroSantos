BEGIN;
CREATE TABLE IF NOT EXISTS booking_requests (
    scope varchar(340) NOT NULL,
    request_key varchar(128) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    agendamento_id bigint NOT NULL REFERENCES agendamentos(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, request_key)
);
-- No public Data API policies: only the JDBC owner can read/write idempotency records.
ALTER TABLE booking_requests ENABLE ROW LEVEL SECURITY;
-- Abort on existing overlaps; resolve them explicitly before applying. No records are deleted.
-- Current application has a single calendar. Multiple barbers require a resource dimension.
ALTER TABLE agendamentos ADD CONSTRAINT agendamentos_valid_interval
    CHECK (data IS NOT NULL AND horario_inicio IS NOT NULL AND horario_fim IS NOT NULL AND horario_fim > horario_inicio);
ALTER TABLE agendamentos ADD CONSTRAINT agendamentos_no_overlap
    EXCLUDE USING gist (tsrange(data + horario_inicio, data + horario_fim, '[)') WITH &&)
    WHERE (status <> 'CANCELADO');
COMMIT;
