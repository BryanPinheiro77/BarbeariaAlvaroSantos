package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.ClienteChangePasswordRequest;
import com.barbearia.agenda.dto.ClienteCreateRequest;
import com.barbearia.agenda.dto.ClienteResponse;
import com.barbearia.agenda.dto.ClienteUpdateRequest;
import com.barbearia.agenda.model.Cliente;
import com.barbearia.agenda.repository.ClienteRepository;
import com.barbearia.agenda.security.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;

    public ClienteController(
            ClienteRepository clienteRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // =============================================================
    // PÚBLICO
    // =============================================================
    @PostMapping("/registrar")
    public ResponseEntity<ClienteResponse> criarCliente(@RequestBody ClienteCreateRequest req) {

        Cliente cliente = new Cliente();
        cliente.setNome(req.nome());
        cliente.setEmail(req.email());
        cliente.setTelefone(req.telefone());
        cliente.setCriadoEm(LocalDateTime.now());

        cliente.setSenhaHash(passwordEncoder.encode(req.senha()));

        Cliente salvo = clienteRepository.save(cliente);
        return ResponseEntity.ok(toResponse(salvo));
    }

    // =============================================================
    // CLIENTE (MINHA CONTA)
    // =============================================================

    @GetMapping("/me")
    public ResponseEntity<ClienteResponse> me() {
        String email = AuthUtils.getAuthenticatedEmail();

        Cliente cliente = clienteRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Cliente autenticado não encontrado"));

        return ResponseEntity.ok(toResponse(cliente));
    }

    @PatchMapping("/me")
    public ResponseEntity<ClienteResponse> atualizarMe(@RequestBody ClienteUpdateRequest req) {
        String email = AuthUtils.getAuthenticatedEmail();

        Cliente cliente = clienteRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Cliente autenticado não encontrado"));

        if (req.nome() != null && !req.nome().isBlank()) {
            cliente.setNome(req.nome());
        }
        if (req.email() != null && !req.email().isBlank()) {
            cliente.setEmail(req.email());
        }
        if (req.telefone() != null && !req.telefone().isBlank()) {
            cliente.setTelefone(req.telefone());
        }

        Cliente atualizado = clienteRepository.save(cliente);
        return ResponseEntity.ok(toResponse(atualizado));
    }

    @PatchMapping("/me/senha")
    public ResponseEntity<Void> trocarSenha(@RequestBody ClienteChangePasswordRequest req) {
        String email = AuthUtils.getAuthenticatedEmail();

        Cliente cliente = clienteRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Cliente autenticado não encontrado"));

        if (req.senhaAtual() == null || req.senhaAtual().isBlank()
                || req.novaSenha() == null || req.novaSenha().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        boolean senhaAtualOk = passwordEncoder.matches(req.senhaAtual(), cliente.getSenhaHash());
        if (!senhaAtualOk) {
            return ResponseEntity.status(401).build();
        }

        cliente.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        clienteRepository.save(cliente);

        return ResponseEntity.noContent().build();
    }

    // =============================================================
    // ADMIN (CRUD POR ID)
    // =============================================================

    @GetMapping
    public ResponseEntity<List<ClienteResponse>> listarClientes() {
        var lista = clienteRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable("id") long id) {
        return clienteRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> atualizarCliente(
            @PathVariable("id") long id,
            @RequestBody ClienteCreateRequest req
    ) {
        return clienteRepository.findById(id)
                .map(cliente -> {
                    cliente.setNome(req.nome());
                    cliente.setEmail(req.email());
                    cliente.setTelefone(req.telefone());
                    cliente.setSenhaHash(passwordEncoder.encode(req.senha()));

                    Cliente atualizado = clienteRepository.save(cliente);
                    return ResponseEntity.ok(toResponse(atualizado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarCliente(@PathVariable("id") long id) {
        if (!clienteRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        clienteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // =============================================================
    // Helpers
    // =============================================================
    private ClienteResponse toResponse(Cliente c) {
        return new ClienteResponse(
                c.getId(),
                c.getNome(),
                c.getEmail(),
                c.getTelefone(),
                c.getCriadoEm()
        );
    }
}
