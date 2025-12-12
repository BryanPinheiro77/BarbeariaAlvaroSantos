package com.barbearia.agenda.dto;

import com.barbearia.agenda.model.Pagamento;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PagamentoListDTO {

    private Long id;
    private String status;
    private String metodo;
    private BigDecimal valor;
    private LocalDateTime criadoEm;
    private Long agendamentoId;

    public PagamentoListDTO(Pagamento p) {
        this.id = p.getId();
        this.status = p.getStatus().name();
        this.metodo = p.getMetodo();
        this.valor = p.getValor();
        this.criadoEm = p.getCriadoEm();
        this.agendamentoId = p.getAgendamento().getId();
    }

}
