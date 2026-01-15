package com.barbearia.agenda.repository;

import com.barbearia.agenda.model.Pagamento;
import com.barbearia.agenda.model.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    Pagamento findByGatewayId(String gatewayId);

    List<Pagamento> findByStatus(StatusPagamento status);

    List<Pagamento> findByAgendamentoId(Long agendamentoId);

}
