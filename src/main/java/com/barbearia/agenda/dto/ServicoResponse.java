package com.barbearia.agenda.dto;

import java.math.BigDecimal;

public record ServicoResponse(
        Long id,
        String nome,
        BigDecimal preco,
        Integer duracaoMinutos,
        boolean ativo
) {}
