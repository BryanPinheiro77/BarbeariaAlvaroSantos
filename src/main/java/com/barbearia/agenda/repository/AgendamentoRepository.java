package com.barbearia.agenda.repository;

import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.model.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    List<Agendamento> findByData(LocalDate data);

    List<Agendamento> findByDataBetween(LocalDate inicio, LocalDate fim);

    List<Agendamento> findByStatus(StatusAgendamento status);

    List<Agendamento> findByClienteId(Long clienteId);

    // ✅ Combinações (pra seu filtro funcionar com período + status/cliente)
    List<Agendamento> findByDataBetweenAndStatus(LocalDate inicio, LocalDate fim, StatusAgendamento status);

    List<Agendamento> findByDataBetweenAndClienteId(LocalDate inicio, LocalDate fim, Long clienteId);

    List<Agendamento> findByDataBetweenAndStatusAndClienteId(
            LocalDate inicio,
            LocalDate fim,
            StatusAgendamento status,
            Long clienteId
    );

    // ✅ Combinações adicionais úteis
    List<Agendamento> findByDataAndStatus(LocalDate data, StatusAgendamento status);

    List<Agendamento> findByClienteIdAndStatus(Long clienteId, StatusAgendamento status);

    boolean existsByDataAndStatusNotAndHorarioInicioLessThanAndHorarioFimGreaterThan(
            LocalDate data,
            StatusAgendamento statusIgnorar,
            LocalTime fimNovo,
            LocalTime inicioNovo
    );
}
