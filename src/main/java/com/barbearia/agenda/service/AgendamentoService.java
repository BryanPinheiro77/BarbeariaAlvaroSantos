package com.barbearia.agenda.service;

import com.barbearia.agenda.integration.WahaClient;
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

    private static final int EXPIRACAO_MINUTOS = 15;

    private final WahaClient wahaClient;
    private final AgendamentoRepository agendamentoRepo;
    private final ClienteRepository clienteRepo;
    private final ServicoRepository servicoRepo;

    public AgendamentoService(
            AgendamentoRepository agendamentoRepo,
            ClienteRepository clienteRepo,
            ServicoRepository servicoRepo,
            WahaClient wahaClient
    ) {
        this.agendamentoRepo = agendamentoRepo;
        this.clienteRepo = clienteRepo;
        this.servicoRepo = servicoRepo;
        this.wahaClient = wahaClient;
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

        List<Servico> servicos = servicoRepo.findAllById(req.servicosIds());
        if (servicos.size() != req.servicosIds().size()) {
            throw new RuntimeException("Um ou mais serviços são inválidos");
        }

        int duracaoTotal = servicos.stream()
                .mapToInt(Servico::getDuracaoMinutos)
                .sum();

        LocalTime inicio = req.horarioInicio();
        LocalTime fim = inicio.plusMinutes(duracaoTotal);

        boolean conflito = agendamentoRepo
                .existsByDataAndStatusNotAndHorarioInicioLessThanAndHorarioFimGreaterThan(
                        req.data(),
                        StatusAgendamento.CANCELADO,
                        fim,
                        inicio
                );

        if (conflito) {
            throw new RuntimeException("Horário já reservado!");
        }

        Agendamento a = new Agendamento();
        a.setCliente(cliente);
        a.setData(req.data());
        a.setHorarioInicio(inicio);
        a.setHorarioFim(fim);

        a.setFormaPagamentoTipo(req.formaPagamentoTipo());
        a.setFormaPagamentoModo(req.formaPagamentoModo());
        a.setLembreteMinutos(req.lembreteMinutos());

        a.setPago(false);
        a.setEnviadoConfirmacao(false);
        a.setEnviadoLembrete(false);
        a.setCriadoEm(LocalDateTime.now());

        boolean online = "ONLINE".equalsIgnoreCase(req.formaPagamentoModo());

        if (online) {
            a.setStatus(StatusAgendamento.PAGAMENTO_PENDENTE);
            a.setExpiraEm(LocalDateTime.now().plusMinutes(EXPIRACAO_MINUTOS));
        } else {
            a.setStatus(StatusAgendamento.AGENDADO);
            a.setExpiraEm(null);
        }

        for (Servico s : servicos) {
            AgendamentoServico link = new AgendamentoServico();
            link.setAgendamento(a);
            link.setServico(s);
            a.getServicos().add(link);
        }

        Agendamento salvo = agendamentoRepo.save(a);

        // ✅ Só envia confirmação imediata se NÃO for online (porque online confirma após pagar)
        if (!online) {
            try {
                String mensagem = "Olá " + cliente.getNome() +
                        "! Seu horário na Barbearia Álvaro Santos foi confirmado para " +
                        req.data() + " às " + req.horarioInicio() + " ✂️";

                boolean enviado = wahaClient.sendText(cliente.getTelefone(), mensagem);
                if (enviado) {
                    salvo.setEnviadoConfirmacao(true);
                    agendamentoRepo.save(salvo);
                }
            } catch (Exception e) {
                System.err.println("Erro ao enviar WhatsApp:");
                e.printStackTrace();
            }
        }

        return salvo;
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

    @Transactional
    public Agendamento cancelarCliente(Long agendamentoId, String email) {
        Cliente cliente = clienteRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        Agendamento ag = agendamentoRepo.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado"));

        if (!ag.getCliente().getId().equals(cliente.getId())) {
            throw new RuntimeException("Você não pode cancelar este agendamento");
        }

        if (ag.isPago()) {
            throw new RuntimeException("Não é possível cancelar um agendamento pago");
        }

        ag.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepo.save(ag);

        try {
            String mensagem = "❌ Agendamento cancelado\n\n" +
                    "Olá, " + cliente.getNome() + "!\n" +
                    "Seu agendamento foi cancelado com sucesso.\n\n" +
                    "📅 Data: " + ag.getData() + "\n" +
                    "⏰ Horário: " + ag.getHorarioInicio() + "\n\n" +
                    "Se precisar, é só agendar novamente 💈";

            wahaClient.sendText(cliente.getTelefone(), mensagem);
        } catch (Exception e) {
            System.err.println("Erro ao enviar WhatsApp de cancelamento: " + e.getMessage());
        }

        return salvo;
    }
}
