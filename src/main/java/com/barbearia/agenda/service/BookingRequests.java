package com.barbearia.agenda.service;

import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
public class BookingRequests {
    private final JdbcTemplate jdbc;
    private final AgendamentoRepository bookings;
    public BookingRequests(JdbcTemplate jdbc, AgendamentoRepository bookings) {
        this.jdbc = jdbc;
        this.bookings = bookings;
    }

    @Transactional
    public Agendamento execute(String scope, String key, LocalDate date, String payload, Supplier<Agendamento> create) {
        if (date == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a data");
        if (key != null && !key.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key inválida");
        }
        String fingerprint = hash(payload);
        if (key != null) {
            lock("request:" + scope + ":" + key);
            var existing = jdbc.query("SELECT payload_hash,agendamento_id FROM booking_requests WHERE scope=? AND request_key=?",
                    (rs, row) -> new Stored(rs.getString(1), rs.getLong(2)), scope, key);
            if (!existing.isEmpty()) {
                if (!fingerprint.equals(existing.get(0).hash())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Chave já usada com outro agendamento");
                return bookings.findById(existing.get(0).id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Agendamento original indisponível"));
            }
        }
        // Both admin and customer acquire the same transaction-scoped calendar lock.
        lock("calendar:" + date);
        Agendamento booking = create.get();
        bookings.flush();
        if (key != null) jdbc.update("INSERT INTO booking_requests(scope,request_key,payload_hash,agendamento_id) VALUES (?,?,?,?)",
                scope, key, fingerprint, booking.getId());
        return booking;
    }

    private record Stored(String hash, long id) {}
    private void lock(String value) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", rs -> { while (rs.next()) { /* acquire lock */ } }, value);
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
