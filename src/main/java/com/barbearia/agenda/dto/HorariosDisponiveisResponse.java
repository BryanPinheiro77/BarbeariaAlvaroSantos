package com.barbearia.agenda.dto;

import java.util.List;

public record HorariosDisponiveisResponse(
        String data,
        List<String> horarios
) {}
