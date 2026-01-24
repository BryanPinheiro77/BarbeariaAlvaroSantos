package com.barbearia.agenda.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AdminAgendamentoCreateRequest(
        Long clienteId,             // se já existir
        String clienteNome,         // se for cliente não cadastrado
        String clienteTelefone,     // se for cliente não cadastrado (obrigatório nesse caso)

        List<Long> servicosIds,
        LocalDate data,
        LocalTime horarioInicio,

        String formaPagamentoTipo,  // PIX / CARTAO / DINHEIRO
        String formaPagamentoModo,  // ONLINE / PAGAR_NA_HORA (ou só PAGAR_NA_HORA se preferir)

        Boolean pago                // opcional: se true, cria já como pago
) {}
