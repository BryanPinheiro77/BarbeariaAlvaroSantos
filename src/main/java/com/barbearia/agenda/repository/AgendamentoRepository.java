package com.barbearia.agenda.repository;

import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.model.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    // ==============================
    // 🔍 CONSULTAS BÁSICAS
    // ==============================

    List<Agendamento> findByData(LocalDate data);

    List<Agendamento> findByDataBetween(LocalDate inicio, LocalDate fim);

    List<Agendamento> findByStatus(StatusAgendamento status);

    List<Agendamento> findByClienteId(Long clienteId);

    // ==============================
    // 🔍 FILTROS COMBINADOS
    // ==============================

    List<Agendamento> findByDataBetweenAndStatus(LocalDate inicio, LocalDate fim, StatusAgendamento status);

    List<Agendamento> findByDataBetweenAndClienteId(LocalDate inicio, LocalDate fim, Long clienteId);

    List<Agendamento> findByDataBetweenAndStatusAndClienteId(
            LocalDate inicio,
            LocalDate fim,
            StatusAgendamento status,
            Long clienteId
    );

    List<Agendamento> findByDataAndStatus(LocalDate data, StatusAgendamento status);

    List<Agendamento> findByClienteIdAndStatus(Long clienteId, StatusAgendamento status);

    // ==============================
    // ⛔ CONFLITO DE HORÁRIO
    // ==============================

    boolean existsByDataAndStatusNotAndHorarioInicioLessThanAndHorarioFimGreaterThan(
            LocalDate data,
            StatusAgendamento statusIgnorar,
            LocalTime fimNovo,
            LocalTime inicioNovo
    );

    // ==============================
    // 🔔 LEMBRETES (SCHEDULER)
    // ==============================

    @Query("""
        SELECT a FROM Agendamento a
        WHERE a.status = 'AGENDADO'
          AND a.enviadoLembrete = false
          AND a.lembreteMinutos IS NOT NULL
          AND a.lembreteMinutos > 0
    """)
    List<Agendamento> findPendentesParaLembrete();
}
