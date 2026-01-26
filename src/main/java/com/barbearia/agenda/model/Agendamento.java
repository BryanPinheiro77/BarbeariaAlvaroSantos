package com.barbearia.agenda.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "agendamentos")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    private LocalDateTime expiraEm;

    // RELACIONAMENTOS
    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @OneToMany(mappedBy = "agendamento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private java.util.List<AgendamentoServico> servicos = new java.util.ArrayList<>();

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
