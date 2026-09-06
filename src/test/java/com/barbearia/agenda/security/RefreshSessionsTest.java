package com.barbearia.agenda.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import com.barbearia.agenda.repository.AdminRepository;
import com.barbearia.agenda.repository.ClienteRepository;
import com.barbearia.agenda.model.Cliente;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RefreshSessionsTest {
    JdbcTemplate jdbc;
    RefreshSessions sessions;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE refresh_sessions(token_hash varchar(64) primary key,email varchar(320),tipo varchar(10),expires_at timestamp with time zone)");
        var clientes = mock(ClienteRepository.class);
        var cliente = new Cliente(); cliente.setNome("Teste");
        when(clientes.findByEmail("test@example.com")).thenReturn(Optional.of(cliente));
        sessions = new RefreshSessions(jdbc, mock(AdminRepository.class), clientes);
    }
    @Test void rotatesWithoutExtendingSevenDaysAndRejectsReplay() {
        var first = sessions.create("test@example.com", "Teste", "CLIENTE");
        assertTrue(first.expiresAt().isBefore(Instant.now().plusSeconds(RefreshSessions.TTL_SECONDS + 1)));
        assertTrue(first.expiresAt().isAfter(Instant.now().plusSeconds(RefreshSessions.TTL_SECONDS - 2)));
        assertNotEquals(first.token(), jdbc.queryForObject("SELECT token_hash FROM refresh_sessions", String.class));
        var next = sessions.rotate(first.token());
        assertNotEquals(first.token(), next.token());
        assertEquals(first.expiresAt().getEpochSecond(), next.expiresAt().getEpochSecond());
        assertThrows(ResponseStatusException.class, () -> sessions.rotate(first.token()));
        sessions.revoke(next.token());
        assertThrows(ResponseStatusException.class, () -> sessions.rotate(next.token()));
    }
    @Test void rejectsExpiredMissingAndMalformedTokens() {
        var first = sessions.create("test@example.com", "Teste", "CLIENTE");
        jdbc.update("UPDATE refresh_sessions SET expires_at=?", Timestamp.from(Instant.now().minusSeconds(1)));
        assertThrows(ResponseStatusException.class, () -> sessions.rotate(first.token()));
        assertThrows(ResponseStatusException.class, () -> sessions.rotate(null));
        assertThrows(ResponseStatusException.class, () -> sessions.rotate("invalid"));
    }
}
