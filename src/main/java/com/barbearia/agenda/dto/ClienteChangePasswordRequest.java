package com.barbearia.agenda.dto;

public record ClienteChangePasswordRequest(
        String senhaAtual,
        String novaSenha
) {}
