package com.barbearia.agenda.dto;

import java.time.LocalTime;

public record HorarioResponse(Long id, LocalTime horario, boolean ativo) {}
