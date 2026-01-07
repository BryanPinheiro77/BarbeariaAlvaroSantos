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

    boolean existsByDataAndHorarioInicioLessThanAndHorarioFimGreaterThan(
            LocalDate data,
            LocalTime fimNovo,
            LocalTime inicioNovo
    );
}

