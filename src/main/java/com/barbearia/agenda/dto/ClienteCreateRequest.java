package com.barbearia.agenda.dto;

public record ClienteCreateRequest (
        String nome,
        String email,
        String senha,
        String telefone
) {}