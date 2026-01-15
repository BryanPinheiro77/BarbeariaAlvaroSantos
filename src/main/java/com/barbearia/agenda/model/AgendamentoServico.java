package com.barbearia.agenda.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "agendamento_servicos")
public class AgendamentoServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "agendamento_id")
    private Agendamento agendamento;

    @ManyToOne(optional = false)
    @JoinColumn(name = "servico_id")
    private Servico servico;
}
