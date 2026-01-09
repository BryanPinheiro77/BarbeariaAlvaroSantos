package com.barbearia.agenda.dto;

import java.math.BigDecimal;

public record AgendamentoServico(
        Long id,
        String nome,
        Integer duracaoMinutos,
        BigDecimal preco
) {}
