package com.barbearia.agenda.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agendamentos")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RELACIONAMENTOS
    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "servico_id")
    private Servico servico;

    // DATA E HORÁRIOS
    private LocalDate data;

    @Column(name = "horario_inicio")
    private LocalTime horarioInicio;

    @Column(name = "horario_fim")
    private LocalTime horarioFim;

    // PAGAMENTO
    @Column(name = "forma_pagamento_tipo")
    private String formaPagamentoTipo;

    @Column(name = "forma_pagamento_metodo")
    private String formaPagamentoModo;

    // LEMBRETE
    @Column(name = "lembrete_minutos")
    private Integer lembreteMinutos;

    @Column(name = "enviado_confirmacao")
    private boolean enviadoConfirmacao;

    @Column(name = "enviado_lembrete")
    private boolean enviadoLembrete;

    // STATUS DO AGENDAMENTO
    @Enumerated(EnumType.STRING)
    private StatusAgendamento status;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    // STATUS DO PAGAMENTO
    @Column(nullable = false)
    private boolean pago = false;

}
