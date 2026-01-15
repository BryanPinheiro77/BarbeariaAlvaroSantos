package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.HorarioRequest;
import com.barbearia.agenda.dto.HorarioResponse;
import com.barbearia.agenda.model.HorarioBarbeiro;
import com.barbearia.agenda.repository.HorarioBarbeiroRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/admin/horarios")
public class HorarioBarbeiroController {

    private final HorarioBarbeiroRepository repository;

    public HorarioBarbeiroController(HorarioBarbeiroRepository repository) {
        this.repository = repository;
    }

    // -----------------------------------------------------------
    // 1) CRIAR HORÁRIO
    // -----------------------------------------------------------
    @PostMapping
    public ResponseEntity<?> criar(@RequestBody HorarioRequest req) {

        // impedir duplicidade do mesmo horário
        if (repository.existsByHorario(req.horario())) {
            return ResponseEntity.status(409).body("Horário já cadastrado");
        }

        HorarioBarbeiro h = new HorarioBarbeiro();
        h.setHorario(req.horario());
        h.setAtivo(true);

        HorarioBarbeiro salvo = repository.save(h);

        var resp = new HorarioResponse(salvo.getId(), salvo.getHorario(), salvo.isAtivo());

        return ResponseEntity
                .created(URI.create("/admin/horarios/" + salvo.getId()))
                .body(resp);
    }

    // -----------------------------------------------------------
    // 2) LISTAR TODOS OS HORÁRIOS (ordenado)
    // -----------------------------------------------------------
    @GetMapping
    public ResponseEntity<List<HorarioResponse>> listarTodos() {
        var lista = repository.findAllByOrderByHorarioAsc()
                .stream()
                .map(h -> new HorarioResponse(h.getId(), h.getHorario(), h.isAtivo()))
                .toList();

        return ResponseEntity.ok(lista);
    }

    // -----------------------------------------------------------
    // 3) LISTAR APENAS OS ATIVOS (ordenado)
    // -----------------------------------------------------------
    @GetMapping("/ativos")
    public ResponseEntity<List<HorarioResponse>> listarAtivos() {
        var lista = repository.findByAtivoTrueOrderByHorarioAsc()
                .stream()
                .map(h -> new HorarioResponse(h.getId(), h.getHorario(), h.isAtivo()))
                .toList();

        return ResponseEntity.ok(lista);
    }

    // -----------------------------------------------------------
    // 4) DESATIVAR UM HORÁRIO
    // -----------------------------------------------------------
    @PatchMapping("/{id}/desativar")
    public ResponseEntity<?> desativar(@PathVariable Long id) {
        return repository.findById(id)
                .map(h -> {
                    h.setAtivo(false);
                    repository.save(h);
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
                .map(h -> {
                    h.setAtivo(true);
                    repository.save(h);
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

    // -----------------------------------------------------------
    // 7) BUSCAR POR ID - útil no front admin
    // -----------------------------------------------------------
    @GetMapping("/{id}")
    public ResponseEntity<HorarioResponse> buscarPorId(@PathVariable Long id) {
        return repository.findById(id)
                .map(h -> new HorarioResponse(h.getId(), h.getHorario(), h.isAtivo()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -----------------------------------------------------------
// 8) EDITAR HORÁRIO
// -----------------------------------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<HorarioResponse> atualizar(
            @PathVariable Long id,
            @RequestBody HorarioRequest req
    ) {
        return repository.findById(id)
                .map(h -> {
                    h.setHorario(req.horario());
                    HorarioBarbeiro atualizado = repository.save(h);
                    return ResponseEntity.ok(new HorarioResponse(
                            atualizado.getId(),
                            atualizado.getHorario(),
                            atualizado.isAtivo()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

}
