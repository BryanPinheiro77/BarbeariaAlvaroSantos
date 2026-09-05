package com.barbearia.agenda;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-only-key-with-at-least-thirty-two-bytes",
        "app.cors.allowed-origins=http://localhost:5173"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.barbearia.agenda.integration.WahaClient waha;

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    @Test
    void protectedRoutesReturn401ForMissingAndExpiredTokens() throws Exception {
        var client = java.net.http.HttpClient.newHttpClient();
        var url = java.net.URI.create("http://localhost:" + port + "/agendamentos/meus");
        var missing = java.net.http.HttpRequest.newBuilder(url).GET().build();
        org.junit.jupiter.api.Assertions.assertEquals(401, client.send(missing, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
        String expired = new com.barbearia.agenda.security.JwtService("test-only-key-with-at-least-thirty-two-bytes", -1000).gerarToken("test@example.com", "CLIENTE");
        var request = java.net.http.HttpRequest.newBuilder(url).header("Authorization", "Bearer " + expired).GET().build();
        org.junit.jupiter.api.Assertions.assertEquals(401, client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
    }

	@Test
	void contextLoads() {
	}

}
