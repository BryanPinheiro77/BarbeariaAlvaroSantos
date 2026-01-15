package com.barbearia.agenda.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AgendamentoCreateRequest(
        List<Long> servicosIds,
        LocalDate data,
        LocalTime horarioInicio,
        String formaPagamentoTipo,
        String formaPagamentoModo,
        Integer lembreteMinutos
) {}
