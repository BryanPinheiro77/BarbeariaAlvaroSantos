package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.AgendamentoResponse;
import com.barbearia.agenda.dto.AdminAgendamentoCreateRequest;
import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.model.StatusAgendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.service.AgendamentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/agendamentos")
public class AdminAgendamentoController {

    private final AgendamentoRepository agendamentoRepo;
    private final AgendamentoService agendamentoService;

    public AdminAgendamentoController(
            AgendamentoRepository agendamentoRepo,
            AgendamentoService agendamentoService
    ) {
        this.agendamentoRepo = agendamentoRepo;
        this.agendamentoService = agendamentoService;
    }

    // ==========================================================
    // 0️⃣ CRIAR AGENDAMENTO (ADMIN)
    // ==========================================================
    @PostMapping
    public ResponseEntity<AgendamentoResponse> criar(@RequestBody AdminAgendamentoCreateRequest req) {
        Agendamento a = agendamentoService.criarAdmin(req);
        return ResponseEntity.ok(toResponse(a));
    }

    // ==========================================================
    // 1️⃣ LISTAR / FILTRAR AGENDAMENTOS (ADMIN)
    // ==========================================================
    @GetMapping
    public ResponseEntity<List<AgendamentoResponse>> listar(
            @RequestParam(required = false) String data,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim
    ) {

        StatusAgendamento st = null;
        if (status != null && !status.isBlank()) {
            st = StatusAgendamento.valueOf(status.toUpperCase());
        }

        List<Agendamento> agendamentos;

        // 1) Período (inicio/fim) tem prioridade e combina com status/cliente
        if (inicio != null && fim != null && !inicio.isBlank() && !fim.isBlank()) {
            LocalDate ini = LocalDate.parse(inicio);
            LocalDate end = LocalDate.parse(fim);

            if (st != null && clienteId != null) {
                agendamentos = agendamentoRepo.findByDataBetweenAndStatusAndClienteId(ini, end, st, clienteId);
            } else if (st != null) {
                agendamentos = agendamentoRepo.findByDataBetweenAndStatus(ini, end, st);
            } else if (clienteId != null) {
                agendamentos = agendamentoRepo.findByDataBetweenAndClienteId(ini, end, clienteId);
            } else {
                agendamentos = agendamentoRepo.findByDataBetween(ini, end);
            }

            // 2) Data única
        } else if (data != null && !data.isBlank()) {
            LocalDate d = LocalDate.parse(data);

            if (st != null) {
                agendamentos = agendamentoRepo.findByDataAndStatus(d, st);
            } else {
                agendamentos = agendamentoRepo.findByData(d);
            }

            // 3) Cliente (com possível status)
        } else if (clienteId != null) {
            if (st != null) {
                agendamentos = agendamentoRepo.findByClienteIdAndStatus(clienteId, st);
            } else {
                agendamentos = agendamentoRepo.findByClienteId(clienteId);
            }

            // 4) Só status
        } else if (st != null) {
            agendamentos = agendamentoRepo.findByStatus(st);

            // 5) Sem filtros
        } else {
            agendamentos = agendamentoRepo.findAll();
        }

        List<AgendamentoResponse> resposta = agendamentos.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(resposta);
    }

    // ==========================================================
    // 2️⃣ LISTAR AGENDAMENTOS POR CLIENTE (ADMIN)
    // ==========================================================
    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorCliente(@PathVariable Long clienteId) {

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

                    // Regra do pagamento
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
    // 4️⃣ CANCELAR AGENDAMENTO (ADMIN / BARBEIRO)
    // - Cancelamento do admin não tem regra de 2h
    // - Envia WhatsApp porque passa pelo service
    // ==========================================================
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id) {
        try {
            agendamentoService.cancelarBarbeiro(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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
