package com.barbearia.agenda.dto;

public record AdminLoginRequest(
        String email,
        String senha
) {}
