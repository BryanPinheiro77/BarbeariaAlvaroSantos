package com.barbearia.agenda.repository;

import com.barbearia.agenda.model.AgendamentoServico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgendamentoServicoRepository extends JpaRepository<AgendamentoServico, Long> {
    List<AgendamentoServico> findByAgendamentoId(Long agendamentoId);
}
