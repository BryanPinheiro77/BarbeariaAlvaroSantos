package com.barbearia.agenda.dto;

import com.barbearia.agenda.model.TipoPagamentoStrategy;

import java.util.List;

public record PagamentoCreateRequest(
        Long agendamentoId,
        String tipoPagamento,           // "PIX" ou "CARTAO"
        List<Long> servicosIds,        // opcional: caso queira calcular pelo backend
        TipoPagamentoStrategy estrategia // CHECKOUT_PRO ou PIX_DIRECT
) {}
