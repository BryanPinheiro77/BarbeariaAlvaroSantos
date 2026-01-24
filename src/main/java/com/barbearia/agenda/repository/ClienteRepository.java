package com.barbearia.agenda.repository;

import com.barbearia.agenda.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    boolean existsByEmail(String email);

    Optional<Cliente> findByTelefone(String telefone);

    Optional<Cliente> findFirstByNomeIgnoreCase(String nome);

    Optional<Cliente> findByEmail(String email);
}
