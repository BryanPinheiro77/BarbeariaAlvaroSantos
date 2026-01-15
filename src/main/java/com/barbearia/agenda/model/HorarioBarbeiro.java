package com.barbearia.agenda.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalTime;

@Entity
@Data
@Table(name = "horarios_barbeiro", schema = "public")
public class HorarioBarbeiro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalTime horario;
    private boolean ativo = true;
}
