package com.barbearia.agenda.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.barbearia.agenda.repository.AdminRepository;
import com.barbearia.agenda.repository.ClienteRepository;

@Service
public class RefreshSessions {
    public static final long TTL_SECONDS = 7 * 24 * 60 * 60;
    private final JdbcTemplate jdbc;
    private final AdminRepository admins;
    private final ClienteRepository clientes;
    private final SecureRandom random = new SecureRandom();

    public RefreshSessions(JdbcTemplate jdbc, AdminRepository admins, ClienteRepository clientes) {
        this.jdbc = jdbc;
        this.admins = admins;
        this.clientes = clientes;
    }

    public record Session(String token, String email, String nome, String tipo, Instant expiresAt) {}

    @Transactional
    public Session create(String email, String nome, String tipo) {
        return insert(email, nome, tipo, Instant.now().plusSeconds(TTL_SECONDS));
    }

    private Session insert(String email, String nome, String tipo, Instant expiresAt) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO refresh_sessions(token_hash,email,tipo,expires_at) VALUES (?,?,?,?)",
                hash(token), email, tipo, Timestamp.from(expiresAt));
        return new Session(token, email, nome, tipo, expiresAt);
    }

    @Transactional
    public Session rotate(String token) {
        if (token == null || token.length() != 43) throw unauthorized();
        var rows = jdbc.query("SELECT email,tipo,expires_at FROM refresh_sessions WHERE token_hash=? FOR UPDATE",
                (rs, n) -> new Session(null, rs.getString(1), null, rs.getString(2), rs.getTimestamp(3).toInstant()), hash(token));
        if (rows.isEmpty() || !rows.get(0).expiresAt().isAfter(Instant.now())) throw unauthorized();
        Session old = rows.get(0);
        String nome;
        if ("ADMIN".equals(old.tipo())) {
            var admin = admins.findByEmail(old.email());
            if (admin == null) throw unauthorized();
            nome = admin.getNome();
        } else {
            nome = clientes.findByEmail(old.email()).orElseThrow(RefreshSessions::unauthorized).getNome();
        }
        jdbc.update("DELETE FROM refresh_sessions WHERE token_hash=?", hash(token));
        // Rotation does not extend the absolute seven-day session lifetime.
        return insert(old.email(), nome, old.tipo(), old.expiresAt());
    }

    public void revoke(String token) {
        if (token != null) jdbc.update("DELETE FROM refresh_sessions WHERE token_hash=?", hash(token));
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Faça login novamente.");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
