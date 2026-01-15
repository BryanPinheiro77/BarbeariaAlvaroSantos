package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.ClienteCreateRequest;
import com.barbearia.agenda.dto.ClienteResponse;
import com.barbearia.agenda.model.Cliente;
import com.barbearia.agenda.repository.ClienteRepository;
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

    public ClienteController(ClienteRepository clienteRepository,
                             PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/registrar")
    public ResponseEntity<ClienteResponse> criarCliente(@RequestBody ClienteCreateRequest req){

        Cliente cliente = new Cliente();
        cliente.setNome(req.nome());
        cliente.setEmail(req.email());
        cliente.setTelefone(req.telefone());
        cliente.setCriadoEm(LocalDateTime.now());

        // Criptografa a senha aqui
        cliente.setSenhaHash(passwordEncoder.encode(req.senha()));

        Cliente salvo = clienteRepository.save(cliente);

        ClienteResponse resp = new ClienteResponse(
                salvo.getId(),
                salvo.getNome(),
                salvo.getEmail(),
                salvo.getTelefone(),
                salvo.getCriadoEm()
        );
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<List<ClienteResponse>> listarClientes() {
        var lista = clienteRepository.findAll()
                .stream()
                .map(c -> new ClienteResponse(
                        c.getId(),
                        c.getNome(),
                        c.getEmail(),
                        c.getTelefone(),
                        c.getCriadoEm()
                ))
                .toList();

        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable("id") long id){
        return clienteRepository.findById(id).map(c -> new ClienteResponse(
                        c.getId(),
                        c.getNome(),
                        c.getEmail(),
                        c.getTelefone(),
                        c.getCriadoEm()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> atualizarCliente(
            @PathVariable("id") long id,
            @RequestBody ClienteCreateRequest req){

        return clienteRepository.findById(id)
                .map(cliente -> {
                    cliente.setNome(req.nome());
                    cliente.setEmail(req.email());
                    cliente.setTelefone(req.telefone());

                    // Atualiza senha criptografada
                    cliente.setSenhaHash(passwordEncoder.encode(req.senha()));

                    Cliente atualizado = clienteRepository.save(cliente);

                    ClienteResponse resp = new ClienteResponse(
                            atualizado.getId(),
                            atualizado.getNome(),
                            atualizado.getEmail(),
                            atualizado.getTelefone(),
                            atualizado.getCriadoEm()
                    );
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ClienteResponse> deletarCliente(@PathVariable("id") long id){
        if(!clienteRepository.existsById(id)){
            return ResponseEntity.notFound().build();
        }

        clienteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
