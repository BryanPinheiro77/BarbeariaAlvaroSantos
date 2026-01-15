package com.barbearia.agenda.dto;

public record LoginResponse(
        String token,
        String nome,
        String tipo // ADMIN ou CLIENTE
) {}
