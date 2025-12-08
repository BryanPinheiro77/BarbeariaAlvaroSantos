package com.barbearia.agenda.dto;

import java.time.LocalTime;

public record HorarioRequest(
        LocalTime horario
) {}
