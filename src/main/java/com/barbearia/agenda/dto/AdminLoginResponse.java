package com.barbearia.agenda.dto;

public record AdminLoginResponse(
        String token,
        String nome
) {}
