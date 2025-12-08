package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.LoginRequest;
import com.barbearia.agenda.dto.LoginResponse;
import com.barbearia.agenda.model.Admin;
import com.barbearia.agenda.model.Cliente;
import com.barbearia.agenda.repository.AdminRepository;
import com.barbearia.agenda.repository.ClienteRepository;
import com.barbearia.agenda.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminRepository adminRepository;
    private final ClienteRepository clienteRepository;
    private final JwtService jwtService;

    public AuthController(AdminRepository adminRepository,
                          ClienteRepository clienteRepository,
                          JwtService jwtService) {
        this.adminRepository = adminRepository;
        this.clienteRepository = clienteRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {

        // 1. Tentativa como ADMIN
        Admin admin = adminRepository.findByEmail(req.email());
        if(admin != null) {

            if (!admin.getSenhaHash().equals(req.senha())) {
                return ResponseEntity.status(401).body("Senha incorreta");
            }

            String token = jwtService.gerarToken(admin.getEmail());

            return ResponseEntity.ok(
                    new LoginResponse(token, admin.getNome(), "ADMIN")
            );
        }

        // 2. Tentativa como CLIENTE
        Cliente cliente = clienteRepository.findByEmail(req.email());
        if(cliente != null) {

            if (!cliente.getSenhaHash().equals(req.senha())) {
                return ResponseEntity.status(401).body("Senha incorreta");
            }

            String token = jwtService.gerarToken(cliente.getEmail());

            return ResponseEntity.ok(
                    new LoginResponse(token, cliente.getNome(), "CLIENTE")
            );
        }

        return ResponseEntity.status(404).body("Email não encontrado");
    }
}
