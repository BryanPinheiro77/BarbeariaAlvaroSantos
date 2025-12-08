package com.barbearia.agenda.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin")
@Data
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

}
