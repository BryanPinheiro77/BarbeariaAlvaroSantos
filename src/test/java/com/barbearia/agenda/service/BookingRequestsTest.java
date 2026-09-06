package com.barbearia.agenda.service;

import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_DB_URL", matches = ".+")
class BookingRequestsTest {
    JdbcTemplate jdbc, root;
    TransactionTemplate tx;
    BookingRequests requests;
    String schema;
    final LocalDate date = LocalDate.of(2026, 10, 10);

    @BeforeEach void setup() throws Exception {
        String url = System.getenv("BOOKING_TEST_DB_URL");
        String user = System.getenv("BOOKING_TEST_DB_USER"), password = System.getenv("BOOKING_TEST_DB_PASSWORD");
        root = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "booking_test_" + UUID.randomUUID().toString().replace("-", "");
        root.execute("CREATE SCHEMA " + schema);
        var ds = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password);
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE agendamentos(id bigserial primary key, data date, horario_inicio time, horario_fim time, status varchar(40))");
        // Production already contains overlapping legacy rows; migration must preserve them.
        jdbc.update("INSERT INTO agendamentos(data,horario_inicio,horario_fim,status) VALUES ('2026-01-22','18:00','18:30','CONCLUIDO'),('2026-01-22','18:00','18:30','CONCLUIDO')");
        String migration = Files.readString(Path.of("db/migrations/002_booking_requests.sql"))
                .replace("public.", schema + ".")
                .replace("search_path = public,", "search_path = " + schema + ",");
        jdbc.execute(migration);
        var repo = mock(AgendamentoRepository.class);
        when(repo.findById(anyLong())).thenAnswer(call -> {
            long id = call.getArgument(0);
            return jdbc.query("SELECT id FROM agendamentos WHERE id=?", (rs, n) -> booking(rs.getLong(1)), id).stream().findFirst();
        });
        requests = new BookingRequests(jdbc, repo);
    }
    @AfterEach void cleanup() { if (root != null && schema != null) root.execute("DROP SCHEMA " + schema + " CASCADE"); }
    Agendamento booking(long id) { var a = new Agendamento(); a.setId(id); return a; }
    Agendamento insert(String start, String end) {
        return booking(jdbc.queryForObject("INSERT INTO agendamentos(data,horario_inicio,horario_fim,status) VALUES (?,?::time,?::time,'AGENDADO') RETURNING id", Long.class, date, start, end));
    }
    Agendamento create(String key, String payload, String start, String end) {
        return tx.execute(s -> requests.execute("CLIENTE:test", key, date, payload, () -> insert(start, end)));
    }
    @Test void concurrentSameKeyReturnsSameIdAndDifferentPayloadIsConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Long> run = () -> { barrier.await(); return create(key, "payload", "10:00", "10:30").getId(); };
            var a = executor.submit(run); var b = executor.submit(run);
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM agendamentos WHERE data=?", Integer.class, date));
        var error = assertThrows(ResponseStatusException.class, () -> create(key, "different", "11:00", "11:30"));
        assertEquals(409, error.getStatusCode().value());
    }
    @Test void concurrentDifferentKeysCannotOverlapAndRollbackReleasesKey() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> run = () -> {
                barrier.await();
                try { create(UUID.randomUUID().toString(), "payload", "10:00", "10:30"); return true; }
                catch (DataIntegrityViolationException expected) { return false; }
            };
            var a = executor.submit(run); var b = executor.submit(run);
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM booking_requests", Integer.class));
        create(UUID.randomUUID().toString(), "adjacent", "10:30", "11:00");
        jdbc.update("UPDATE agendamentos SET status='CANCELADO'");
        create(UUID.randomUUID().toString(), "released", "10:00", "10:30");
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM agendamentos WHERE data=?", Integer.class, date));
    }
    @Test void missingKeyStillProtectsCalendarAndInvalidKeyIsRejected() {
        create(null, "payload", "10:00", "10:30");
        assertThrows(DataIntegrityViolationException.class, () -> create(null, "payload", "10:15", "10:45"));
        assertThrows(ResponseStatusException.class, () -> create("bad", "payload", "11:00", "11:30"));
        String retryKey = UUID.randomUUID().toString();
        assertThrows(DataIntegrityViolationException.class, () -> create(retryKey, "payload", "10:00", "10:30"));
        jdbc.update("UPDATE agendamentos SET status='CANCELADO'");
        assertNotNull(create(retryKey, "payload", "10:00", "10:30"));
    }
}
