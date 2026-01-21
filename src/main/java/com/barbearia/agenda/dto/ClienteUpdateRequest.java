package com.barbearia.agenda.dto;

public record ClienteUpdateRequest (
        String nome,
        String telefone,
        String email

) {}