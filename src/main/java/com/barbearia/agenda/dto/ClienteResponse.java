package com.barbearia.agenda.dto;

import java.time.LocalDateTime;

public record ClienteResponse (
      Long id,
      String nome,
      String email,
      String telefone,
      LocalDateTime criadoEm
) {}
