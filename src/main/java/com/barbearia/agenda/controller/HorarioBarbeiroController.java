package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.HorarioRequest;
import com.barbearia.agenda.dto.HorarioResponse;
import com.barbearia.agenda.model.HorarioBarbeiro;
import com.barbearia.agenda.repository.HorarioBarbeiroRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/horarios")
public class HorarioBarbeiroController {

    private final HorarioBarbeiroRepository repository;

    public HorarioBarbeiroController(HorarioBarbeiroRepository repository) {
        this.repository = repository;
    }

    // -----------------------------------------------------------
    // 1) CRIAR HORÁRIO
    // -----------------------------------------------------------
    @PostMapping
    public ResponseEntity<HorarioResponse> criar(@RequestBody HorarioRequest req) {

        HorarioBarbeiro h = new HorarioBarbeiro();
        h.setHorario(req.horario());
        h.setAtivo(true);

        HorarioBarbeiro salvo = repository.save(h);

        return ResponseEntity.ok(
                new HorarioResponse(
                        salvo.getId(),
                        salvo.getHorario(),
                        salvo.isAtivo()
                )
        );
    }

    // -----------------------------------------------------------
    // 2) LISTAR TODOS OS HORÁRIOS
    // -----------------------------------------------------------
    @GetMapping
    public ResponseEntity<List<HorarioResponse>> listarTodos() {
        var lista = repository.findAll()
                .stream()
                .map(h -> new HorarioResponse(
                        h.getId(),
                        h.getHorario(),
                        h.isAtivo()
                ))
                .toList();

        return ResponseEntity.ok(lista);
    }

    // -----------------------------------------------------------
    // 3) LISTAR APENAS OS ATIVOS
    // -----------------------------------------------------------
    @GetMapping("/ativos")
    public ResponseEntity<List<HorarioResponse>> listarAtivos() {
        var lista = repository.findAll()
                .stream()
                .filter(HorarioBarbeiro::isAtivo)
                .map(h -> new HorarioResponse(
                        h.getId(),
                        h.getHorario(),
                        h.isAtivo()
                ))
                .toList();

        return ResponseEntity.ok(lista);
    }

    // -----------------------------------------------------------
    // 4) DESATIVAR UM HORÁRIO
    // -----------------------------------------------------------
    @PatchMapping("/{id}/desativar")
    public ResponseEntity<?> desativar(@PathVariable Long id) {

        return repository.findById(id)
                .map(horario -> {
                    horario.setAtivo(false);
                    repository.save(horario);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // -----------------------------------------------------------
    // 5) ATIVAR UM HORÁRIO
    // -----------------------------------------------------------
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<?> ativar(@PathVariable Long id) {

        return repository.findById(id)
                .map(horario -> {
                    horario.setAtivo(true);
                    repository.save(horario);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // -----------------------------------------------------------
    // 6) EXCLUIR HORÁRIO (DELETE REAL)
    // -----------------------------------------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {

        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
