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

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminRepository adminRepository;
    private final ClienteRepository clienteRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AdminRepository adminRepository,
                          ClienteRepository clienteRepository,
                          JwtService jwtService,
                          PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.clienteRepository = clienteRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {

        // Tentativa como admin
        Admin admin = adminRepository.findByEmail(req.email());
        if(admin != null) {

            if (!passwordEncoder.matches(req.senha(), admin.getSenhaHash())) {
                return ResponseEntity.status(401).body("Senha incorreta");
            }

            String token = jwtService.gerarToken(admin.getEmail());
            return ResponseEntity.ok(new LoginResponse(token, admin.getNome(), "ADMIN"));
        }

        // Tentativa como cliente
        Optional<Cliente> clienteOpt = clienteRepository.findByEmail(req.email());

        if (clienteOpt.isPresent()) {
            Cliente cliente = clienteOpt.get();

            if (!passwordEncoder.matches(req.senha(), cliente.getSenhaHash())) {
                return ResponseEntity.status(401).body("Senha incorreta");
            }

            String token = jwtService.gerarToken(cliente.getEmail());
            return ResponseEntity.ok(new LoginResponse(token, cliente.getNome(), "CLIENTE"));
        }

        return ResponseEntity.status(404).body("Email não encontrado");
    }
}
