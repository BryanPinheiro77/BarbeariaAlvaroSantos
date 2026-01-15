package com.barbearia.agenda.dto;

import com.barbearia.agenda.model.StatusAgendamento;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AgendamentoResponse(
        Long id,
        Long clienteId,
        String clienteNome,

        List<AgendamentoServico> servicos,

        LocalDate data,
        LocalTime horarioInicio,
        LocalTime horarioFim,

        String formaPagamentoTipo,
        String formaPagamentoModo,
        Integer lembreteMinutos,
        StatusAgendamento status,

        boolean pago
) {}
