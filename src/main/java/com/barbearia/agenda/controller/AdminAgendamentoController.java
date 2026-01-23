package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.AgendamentoResponse;
import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.model.StatusAgendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/admin/agendamentos")
public class AdminAgendamentoController {

    private final AgendamentoRepository agendamentoRepo;

    public AdminAgendamentoController(AgendamentoRepository agendamentoRepo) {
        this.agendamentoRepo = agendamentoRepo;
    }

    // ==========================================================
    // 1️⃣ LISTAR / FILTRAR AGENDAMENTOS (ADMIN) — COMBINA FILTROS
    // ==========================================================
    @GetMapping
    public ResponseEntity<List<AgendamentoResponse>> listar(
            @RequestParam(required = false) String data,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim
    ) {
        // Base: decide um conjunto inicial (preferência: intervalo > dia específico > tudo)
        List<Agendamento> base;

        if (inicio != null && fim != null) {
            base = agendamentoRepo.findByDataBetween(LocalDate.parse(inicio), LocalDate.parse(fim));
        } else if (data != null) {
            base = agendamentoRepo.findByData(LocalDate.parse(data));
        } else {
            base = agendamentoRepo.findAll();
        }

        Stream<Agendamento> st = base.stream();

        // Refina por cliente (se vier)
        if (clienteId != null) {
            st = st.filter(a -> a.getCliente() != null && clienteId.equals(a.getCliente().getId()));
        }

        // Refina por status (se vier)
        if (status != null && !status.isBlank()) {
            StatusAgendamento stReq = StatusAgendamento.valueOf(status.toUpperCase());
            st = st.filter(a -> a.getStatus() == stReq);
        }

        // Se o front mandar "data" junto, também refina (só pra ficar robusto)
        if (data != null && !data.isBlank()) {
            LocalDate d = LocalDate.parse(data);
            st = st.filter(a -> d.equals(a.getData()));
        }

        List<AgendamentoResponse> resposta = st
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(resposta);
    }

    // ==========================================================
    // 2️⃣ LISTAR AGENDAMENTOS POR CLIENTE (ADMIN)
    // ==========================================================
    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorCliente(
            @PathVariable Long clienteId
    ) {

        List<AgendamentoResponse> lista = agendamentoRepo
                .findByClienteId(clienteId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(lista);
    }

    // ==========================================================
    // 3️⃣ CONCLUIR AGENDAMENTO (ADMIN)
    // ==========================================================
    @PatchMapping("/{id}/concluir")
    public ResponseEntity<?> concluir(@PathVariable Long id) {

        return agendamentoRepo.findById(id)
                .map(a -> {
                    if (a.getStatus() == StatusAgendamento.CANCELADO) {
                        return ResponseEntity.badRequest()
                                .body("Agendamento cancelado não pode ser concluído");
                    }

                    // ✅ regra do pagamento
                    String modo = a.getFormaPagamentoModo(); // ONLINE ou PAGAR_NA_HORA

                    if (!a.isPago()) {
                        if ("ONLINE".equalsIgnoreCase(modo)) {
                            return ResponseEntity.badRequest()
                                    .body("Pagamento online ainda não foi confirmado");
                        }

                        // PAGAR_NA_HORA: concluiu => pagou
                        a.setPago(true);
                    }

                    a.setStatus(StatusAgendamento.CONCLUIDO);
                    agendamentoRepo.save(a);

                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==========================================================
    // 4️⃣ CANCELAR AGENDAMENTO (ADMIN)
    // ==========================================================
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id) {

        return agendamentoRepo.findById(id)
                .map(a -> {
                    if (a.getStatus() == StatusAgendamento.CONCLUIDO) {
                        return ResponseEntity
                                .badRequest()
                                .body("Agendamento concluído não pode ser cancelado");
                    }

                    a.setStatus(StatusAgendamento.CANCELADO);
                    agendamentoRepo.save(a);

                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==========================================================
    // 🔧 MÉTODO AUXILIAR (DTO)
    // ==========================================================
    private AgendamentoResponse toResponse(Agendamento a) {

        var servicosDto = a.getServicos().stream()
                .map(link -> new com.barbearia.agenda.dto.AgendamentoServico(
                        link.getServico().getId(),
                        link.getServico().getNome(),
                        link.getServico().getDuracaoMinutos(),
                        link.getServico().getPreco()
                ))
                .toList();

        return new AgendamentoResponse(
                a.getId(),
                a.getCliente().getId(),
                a.getCliente().getNome(),
                servicosDto,
                a.getData(),
                a.getHorarioInicio(),
                a.getHorarioFim(),
                a.getFormaPagamentoTipo(),
                a.getFormaPagamentoModo(),
                a.getLembreteMinutos(),
                a.getStatus(),
                a.isPago()
        );
    }
}
