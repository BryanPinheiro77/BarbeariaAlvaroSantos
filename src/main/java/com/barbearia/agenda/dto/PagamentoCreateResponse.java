package com.barbearia.agenda.dto;

public record PagamentoCreateResponse(
        Long pagamentoId,
        String tipoPagamento,   // PIX ou CARTAO
        String status,          // PENDENTE / PAGO
        String qrCodeBase64,    // se vier do Mercado Pago
        String copiaCola,
        String checkoutUrl      // para cartão / pagamento web
) {}
