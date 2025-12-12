package com.barbearia.agenda.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record AgendamentoCreateRequest(
        Long clienteId,
        Long servicoId,
        LocalDate data,
        LocalTime horarioInicio,
        String formaPagamentoTipo,
        String formaPagamentoModo,
        Integer lembreteMinutos
) {}
