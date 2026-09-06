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
-- Keep legacy duplicates unchanged, but reject every new overlapping booking.
-- The advisory lock also serializes direct concurrent inserts outside the application.
CREATE OR REPLACE FUNCTION public.prevent_agendamento_overlap()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.data IS NULL
       OR NEW.horario_inicio IS NULL
       OR NEW.horario_fim IS NULL
       OR NEW.horario_fim <= NEW.horario_inicio THEN
        RAISE EXCEPTION 'Intervalo de agendamento inválido'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.status <> 'CANCELADO' THEN
        PERFORM pg_advisory_xact_lock(
            hashtextextended('calendar:' || NEW.data::text, 0)
        );

        IF EXISTS (
            SELECT 1
              FROM public.agendamentos existing
             WHERE existing.id IS DISTINCT FROM NEW.id
               AND existing.data = NEW.data
               AND existing.status <> 'CANCELADO'
               AND existing.horario_inicio < NEW.horario_fim
               AND existing.horario_fim > NEW.horario_inicio
        ) THEN
            RAISE EXCEPTION 'Horário já reservado'
                USING ERRCODE = '23P01';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS prevent_agendamento_overlap ON public.agendamentos;
CREATE TRIGGER prevent_agendamento_overlap
BEFORE INSERT OR UPDATE OF data, horario_inicio, horario_fim
ON public.agendamentos
FOR EACH ROW
EXECUTE FUNCTION public.prevent_agendamento_overlap();
COMMIT;
