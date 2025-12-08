package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.ServicoCreateRequest;
import com.barbearia.agenda.dto.ServicoResponse;
import com.barbearia.agenda.model.Servico;
import com.barbearia.agenda.repository.ServicoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/servicos")
public class ServicoController {

    private final ServicoRepository servicoRepository;

    public ServicoController(ServicoRepository servicoRepository) {
        this.servicoRepository = servicoRepository;
    }

    // -------------- CRIAR SERVIÇO --------------
    @PostMapping
    public ResponseEntity<ServicoResponse> criar(@RequestBody ServicoCreateRequest req) {
        Servico servico = new Servico();
        servico.setNome(req.nome());
        servico.setPreco(req.preco());
        servico.setDuracaoMinutos(req.duracaoMinutos());
        servico.setAtivo(true);

        Servico salvo = servicoRepository.save(servico);
        return ResponseEntity.ok(toResponse(salvo));
    }

    // -------------- LISTAR TODOS (ADMIN) --------------
    @GetMapping
    public ResponseEntity<List<ServicoResponse>> listar() {
        var lista = servicoRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(lista);
    }

    // -------------- LISTAR APENAS ATIVOS (CLIENTE) --------------
    @GetMapping("/ativos")
    public ResponseEntity<List<ServicoResponse>> listarAtivos() {
        var lista = servicoRepository.findAll()
                .stream()
                .filter(Servico::isAtivo)
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(lista);
    }

    // -------------- BUSCAR POR ID --------------
    @GetMapping("/{id}")
    public ResponseEntity<ServicoResponse> buscarPorId(@PathVariable Long id) {
        return servicoRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------- ATUALIZAR SERVIÇO --------------
    @PutMapping("/{id}")
    public ResponseEntity<ServicoResponse> atualizar(
            @PathVariable Long id,
            @RequestBody ServicoCreateRequest req
    ) {
        return servicoRepository.findById(id)
                .map(servico -> {
                    servico.setNome(req.nome());
                    servico.setPreco(req.preco());
                    servico.setDuracaoMinutos(req.duracaoMinutos());

                    Servico atualizado = servicoRepository.save(servico);
                    return ResponseEntity.ok(toResponse(atualizado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------- DESATIVAR SERVIÇO (SOFT DELETE) --------------
    @PatchMapping("/{id}/desativar")
    public ResponseEntity<?> desativar(@PathVariable Long id) {
        return servicoRepository.findById(id)
                .map(servico -> {
                    servico.setAtivo(false);
                    servicoRepository.save(servico);
                    return ResponseEntity.noContent().build(); // 204
                })
                .orElseGet(() -> ResponseEntity.notFound().build()); // 404
    }

    // -------------- ATIVAR SERVIÇO  --------------
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<?> ativar(@PathVariable Long id) {
        return servicoRepository.findById(id)
                .map(servico -> {
                    servico.setAtivo(true);
                    servicoRepository.save(servico);
                    return ResponseEntity.noContent().build(); // 204
                })
                .orElseGet(() -> ResponseEntity.notFound().build()); // 404
    }

    // -------------- DELETAR SERVIÇO (DELETE REAL) --------------
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        if (!servicoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        servicoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // -------------- MÉTODO AUXILIAR DTO --------------
    private ServicoResponse toResponse(Servico s) {
        return new ServicoResponse(
                s.getId(),
                s.getNome(),
                s.getPreco(),
                s.getDuracaoMinutos(),
                s.isAtivo()
        );
    }
}
