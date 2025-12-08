package com.barbearia.agenda.repository;

import com.barbearia.agenda.model.HorarioBarbeiro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HorarioBarbeiroRepository extends JpaRepository<HorarioBarbeiro, Long> {
    List<HorarioBarbeiro> findByAtivoTrue();

}
