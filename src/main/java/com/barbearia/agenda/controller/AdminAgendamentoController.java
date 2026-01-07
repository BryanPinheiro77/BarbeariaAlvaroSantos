package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.AgendamentoResponse;
import com.barbearia.agenda.repository.AgendamentoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/agendamentos")
public class AdminAgendamentoController {

    private final AgendamentoRepository agendamentoRepo;

    public AdminAgendamentoController(AgendamentoRepository agendamentoRepo) {
        this.agendamentoRepo = agendamentoRepo;
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorCliente(
            @PathVariable Long clienteId
    ) {
        List<AgendamentoResponse> lista = agendamentoRepo
                .findByClienteId(clienteId)
                .stream()
                .map(a -> new AgendamentoResponse(
                        a.getId(),
                        a.getCliente().getId(),
                        a.getCliente().getNome(),
                        a.getServico().getId(),
                        a.getServico().getNome(),
                        a.getData(),
                        a.getHorarioInicio(),
                        a.getHorarioFim(),
                        a.getFormaPagamentoTipo(),
                        a.getFormaPagamentoModo(),
                        a.getLembreteMinutos(),
                        a.getStatus()
                ))
                .toList();

        return ResponseEntity.ok(lista);
    }
}
