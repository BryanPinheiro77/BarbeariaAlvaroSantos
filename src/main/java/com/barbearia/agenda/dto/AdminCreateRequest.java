package com.barbearia.agenda.dto;

public record AdminCreateRequest(
        String nome,
        String email,
        String senha
) {}
