package com.barbearia.agenda.dto;

import java.util.List;

public record HorariosDisponiveisRequest(
        String data,
        List<Long> servicosIds
) {}
