package com.barbearia.agenda.dto;

import java.math.BigDecimal;

public record ServicoCreateRequest(
        String nome,
        BigDecimal preco,
        Integer duracaoMinutos
) {}
