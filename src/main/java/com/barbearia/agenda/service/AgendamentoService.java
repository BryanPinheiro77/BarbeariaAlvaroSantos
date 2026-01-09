package com.barbearia.agenda.service;

import com.barbearia.agenda.dto.AgendamentoCreateRequest;
import com.barbearia.agenda.model.*;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.repository.ClienteRepository;
import com.barbearia.agenda.repository.ServicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepo;
    private final ClienteRepository clienteRepo;
    private final ServicoRepository servicoRepo;

    public AgendamentoService(
            AgendamentoRepository agendamentoRepo,
            ClienteRepository clienteRepo,
            ServicoRepository servicoRepo
    ) {
        this.agendamentoRepo = agendamentoRepo;
        this.clienteRepo = clienteRepo;
        this.servicoRepo = servicoRepo;
    }

    @Transactional
    public Agendamento criar(AgendamentoCreateRequest req, String email) {

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Usuário autenticado inválido");
        }

        if (req.servicosIds() == null || req.servicosIds().isEmpty()) {
            throw new RuntimeException("Selecione pelo menos 1 serviço");
        }

        Cliente cliente = clienteRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        // 1) Busca todos os serviços
        List<Servico> servicos = servicoRepo.findAllById(req.servicosIds());
        if (servicos.size() != req.servicosIds().size()) {
            throw new RuntimeException("Um ou mais serviços são inválidos");
        }

        // 2) Soma duração total
        int duracaoTotal = servicos.stream()
                .mapToInt(Servico::getDuracaoMinutos)
                .sum();

        LocalTime inicio = req.horarioInicio();
        LocalTime fim = inicio.plusMinutes(duracaoTotal);

        // 3) Verifica conflito usando duração total
        boolean conflito = agendamentoRepo
                .existsByDataAndHorarioInicioLessThanAndHorarioFimGreaterThan(
                        req.data(), fim, inicio
                );

        if (conflito) {
            throw new RuntimeException("Horário já reservado!");
        }

        // 4) Cria agendamento
        Agendamento a = new Agendamento();
        a.setCliente(cliente);
        a.setData(req.data());
        a.setHorarioInicio(inicio);
        a.setHorarioFim(fim);

        a.setFormaPagamentoTipo(req.formaPagamentoTipo());
        a.setFormaPagamentoModo(req.formaPagamentoModo());
        a.setLembreteMinutos(req.lembreteMinutos());

        a.setStatus(StatusAgendamento.AGENDADO);
        a.setPago(false);
        a.setEnviadoConfirmacao(false);
        a.setEnviadoLembrete(false);
        a.setCriadoEm(LocalDateTime.now());

        // 5) Cria vínculos AgendamentoServico (join table)
        for (Servico s : servicos) {
            AgendamentoServico link = new AgendamentoServico();
            link.setAgendamento(a);
            link.setServico(s);
            a.getServicos().add(link);
        }

        return agendamentoRepo.save(a);
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarMeus(String email) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Usuário autenticado inválido");
        }

        Cliente cliente = clienteRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        return agendamentoRepo.findByClienteId(cliente.getId());
    }
}
