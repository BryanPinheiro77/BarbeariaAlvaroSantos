package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.*;
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

    /**
     * Atualiza nome/email/telefone do cliente logado.
     * Regra: se trocar email OU telefone, exige senhaAtual válida.
     */
    @PatchMapping("/me")
    public ResponseEntity<ClienteResponse> atualizarMe(@RequestBody ClienteUpdateRequest req) {
        String emailAuth = AuthUtils.getAuthenticatedEmail();

        Cliente cliente = clienteRepository.findByEmail(emailAuth)
                .orElseThrow(() -> new RuntimeException("Cliente autenticado não encontrado"));

        boolean querTrocarEmail = req.email() != null
                && !req.email().isBlank()
                && !req.email().equalsIgnoreCase(cliente.getEmail());

        boolean querTrocarTelefone = req.telefone() != null
                && !req.telefone().isBlank()
                && (cliente.getTelefone() == null || !req.telefone().equals(cliente.getTelefone()));

        // Se for trocar email/telefone, exige senha atual
        if (querTrocarEmail || querTrocarTelefone) {
            if (req.senhaAtual() == null || req.senhaAtual().isBlank()) {
                // melhor do que 500: diz que faltou dado
                return ResponseEntity.badRequest().build();
            }

            boolean senhaAtualOk = passwordEncoder.matches(req.senhaAtual(), cliente.getSenhaHash());
            if (!senhaAtualOk) {
                return ResponseEntity.status(401).build();
            }
        }

        // Atualiza nome (sem exigir senha)
        if (req.nome() != null && !req.nome().isBlank()) {
            cliente.setNome(req.nome());
        }

        // Atualiza email (com verificação de duplicidade)
        if (querTrocarEmail) {
            var existente = clienteRepository.findByEmail(req.email());
            if (existente.isPresent() && existente.get().getId() != cliente.getId()) {
                return ResponseEntity.status(409).build(); // email já em uso
            }
            cliente.setEmail(req.email());
        }

        // Atualiza telefone
        if (querTrocarTelefone) {
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

    @PatchMapping("/{id}")
    public ResponseEntity<ClienteResponse> adminAtualizarNomeTelefone(
            @PathVariable("id") long id,
            @RequestBody AdminClienteUpdateRequest req
    ) {
        return clienteRepository.findById(id)
                .map(cliente -> {
                    if (req.nome() != null && !req.nome().isBlank()) {
                        cliente.setNome(req.nome());
                    }
                    // telefone pode ser null para "limpar"
                    if (req.telefone() == null || req.telefone().isBlank()) {
                        cliente.setTelefone(null);
                    } else {
                        cliente.setTelefone(req.telefone());
                    }

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
