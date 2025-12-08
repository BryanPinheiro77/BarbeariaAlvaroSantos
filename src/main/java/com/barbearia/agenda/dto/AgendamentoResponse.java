package com.barbearia.agenda.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import com.barbearia.agenda.model.StatusAgendamento;

public record AgendamentoResponse(
        Long id,
        Long clienteId,
        String clienteNome,
        Long servicoId,
        String servicoNome,
        LocalDate data,
        LocalTime horarioInicio,
        LocalTime horarioFim,
        String formaPagamentoTipo,
        String formaPagamentoModo,
        Integer lembreteMinutos,
        StatusAgendamento status
) {}
