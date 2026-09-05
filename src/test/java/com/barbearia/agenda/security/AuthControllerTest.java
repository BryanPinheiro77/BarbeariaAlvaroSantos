package com.barbearia.agenda.security;

import com.barbearia.agenda.controller.AuthController;
import com.barbearia.agenda.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    @Test void refreshRequiresTrustedOriginAndWritesHttpOnlyCookie() {
        var sessions = mock(RefreshSessions.class);
        var jwt = mock(JwtService.class);
        var controller = new AuthController(mock(AdminRepository.class), mock(ClienteRepository.class), jwt, mock(PasswordEncoder.class), sessions);
        ReflectionTestUtils.setField(controller, "allowedOrigins", "https://app.example.com");
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        assertThrows(ResponseStatusException.class, () -> controller.refresh("old", request, response));
        request.addHeader("Origin", "https://attacker.example.com");
        assertThrows(ResponseStatusException.class, () -> controller.refresh("old", request, response));
        verifyNoInteractions(sessions);
        request.removeHeader("Origin");
        request.addHeader("Origin", "https://app.example.com");
        when(sessions.rotate("old")).thenReturn(new RefreshSessions.Session("new", "test@example.com", "Teste", "CLIENTE", Instant.now().plusSeconds(100)));
        when(jwt.gerarToken("test@example.com", "CLIENTE")).thenReturn("access");
        assertEquals("access", controller.refresh("old", request, response).getBody().token());
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        controller.logout("new", request, new MockHttpServletResponse());
        verify(sessions).revoke("new");
    }
}
