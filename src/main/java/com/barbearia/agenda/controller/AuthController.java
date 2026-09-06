package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.LoginRequest;
import com.barbearia.agenda.dto.LoginResponse;
import com.barbearia.agenda.model.Admin;
import com.barbearia.agenda.model.Cliente;
import com.barbearia.agenda.repository.AdminRepository;
import com.barbearia.agenda.repository.ClienteRepository;
import com.barbearia.agenda.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.barbearia.agenda.security.RefreshSessions;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminRepository adminRepository;
    private final ClienteRepository clienteRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessions sessions;
    @Value("${app.auth.cookie-secure:true}") private boolean cookieSecure;
    @Value("${app.cors.allowed-origins}") private String allowedOrigins;

    public AuthController(AdminRepository adminRepository,
                          ClienteRepository clienteRepository,
                          JwtService jwtService,
                          PasswordEncoder passwordEncoder, RefreshSessions sessions) {
        this.adminRepository = adminRepository;
        this.clienteRepository = clienteRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response,
                                  @CookieValue(name = "refresh_token", required = false) String oldToken) {
        checkOrigin(request);

        Admin admin = adminRepository.findByEmail(req.email());
        if (admin != null) {

            if (!passwordEncoder.matches(req.senha(), admin.getSenhaHash())) {
                return ResponseEntity.status(401).body("Senha incorreta");
            }

            String token = jwtService.gerarToken(admin.getEmail(), "ADMIN");
            sessions.revoke(oldToken);
            setCookie(response, sessions.create(admin.getEmail(), admin.getNome(), "ADMIN"));
            return ResponseEntity.ok(new LoginResponse(token, admin.getNome(), "ADMIN"));
        }

        Optional<Cliente> clienteOpt = clienteRepository.findByEmail(req.email());

        if (clienteOpt.isPresent()) {
            Cliente cliente = clienteOpt.get();

            if (!passwordEncoder.matches(req.senha(), cliente.getSenhaHash())) {
                return ResponseEntity.status(401).body("Senha incorreta");
            }

            String token = jwtService.gerarToken(cliente.getEmail(), "CLIENTE");
            sessions.revoke(oldToken);
            setCookie(response, sessions.create(cliente.getEmail(), cliente.getNome(), "CLIENTE"));
            return ResponseEntity.ok(new LoginResponse(token, cliente.getNome(), "CLIENTE"));
        }

        return ResponseEntity.status(404).body("Email não encontrado");
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@CookieValue(name = "refresh_token", required = false) String token,
            HttpServletRequest request, HttpServletResponse response) {
        checkOrigin(request);
        var session = sessions.rotate(token);
        setCookie(response, session);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new LoginResponse(
                jwtService.gerarToken(session.email(), session.tipo()), session.nome(), session.tipo()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String token,
            HttpServletRequest request, HttpServletResponse response) {
        checkOrigin(request);
        sessions.revoke(token);
        response.addHeader("Set-Cookie", cookie("", 0).toString());
        return ResponseEntity.noContent().build();
    }

    // Cookie-authenticated endpoints require an explicit trusted browser Origin (CSRF protection).
    private void checkOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || Arrays.stream(allowedOrigins.split(",")).map(String::trim).noneMatch(origin::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Origem não permitida");
        }
    }

    private void setCookie(HttpServletResponse response, RefreshSessions.Session session) {
        response.setHeader("Cache-Control", "no-store");
        response.addHeader("Set-Cookie", cookie(session.token(), Math.max(0,
                Duration.between(Instant.now(), session.expiresAt()).getSeconds())).toString());
    }

    private ResponseCookie cookie(String token, long seconds) {
        return ResponseCookie.from("refresh_token", token).httpOnly(true).secure(cookieSecure)
                .sameSite("Lax").path("/").maxAge(seconds).build();
    }
}
