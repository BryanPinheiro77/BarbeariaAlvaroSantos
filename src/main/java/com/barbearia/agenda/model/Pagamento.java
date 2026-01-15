package com.barbearia.agenda.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamentos")
@Data
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relacionamento com Agendamento
    @ManyToOne
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    // valor total cobrado (serviços)
    @Column(nullable = false)
    private BigDecimal valor;

    // se usarmos "PIX", "CARTAO"
    @Column(name = "metodo", nullable = false)
    private String metodo;

    // status interno
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status;


    // ID da transação no Mercado Pago
    @Column(name = "transaction_id")
    private String gatewayId;

    @Column(name = "recebido_em")
    private LocalDateTime criadoEm;
}
