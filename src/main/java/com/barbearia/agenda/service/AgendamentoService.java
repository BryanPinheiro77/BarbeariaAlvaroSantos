package com.barbearia.agenda.service;

import com.barbearia.agenda.dto.AgendamentoCreateRequest;
import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.model.Cliente;
import com.barbearia.agenda.model.Servico;
import com.barbearia.agenda.model.StatusAgendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.repository.ClienteRepository;
import com.barbearia.agenda.repository.ServicoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;

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

    public Agendamento criar(AgendamentoCreateRequest req, Long clienteId) {

        if (clienteId == null) {
            throw new RuntimeException("Cliente autenticado inválido");
        }

        Cliente cliente = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        Servico servico = servicoRepo.findById(req.servicoId())
                .orElseThrow(() -> new RuntimeException("Serviço inválido"));

        LocalTime inicio = req.horarioInicio();
        LocalTime fim = inicio.plusMinutes(servico.getDuracaoMinutos());

        boolean conflito = agendamentoRepo
                .existsByDataAndHorarioInicioLessThanAndHorarioFimGreaterThan(
                        req.data(), fim, inicio
                );

        if (conflito) {
            throw new RuntimeException("Horário já reservado!");
        }

        Agendamento a = new Agendamento();
        a.setCliente(cliente);
        a.setServico(servico);
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

        return agendamentoRepo.save(a);
    }

}
