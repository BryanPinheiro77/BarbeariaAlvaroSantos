package com.barbearia.agenda.dto;

public record ClienteUpdateRequest(
        String nome,
        String email,
        String telefone,
        String senhaAtual // obrigatória se trocar email/telefone
) {}
