package com.barbearia.agenda.repository;

import com.barbearia.agenda.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    Pagamento findByGatewayId(String gatewayId);
}
