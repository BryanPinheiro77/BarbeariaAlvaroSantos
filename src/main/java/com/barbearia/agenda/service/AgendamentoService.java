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

        // 3) Verifica conflito usando duração total (ignorando CANCELADO)
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

        // 6) Salva no banco
        Agendamento salvo = agendamentoRepo.save(a);

        // 7) Envia WhatsApp após salvar (somente marca como enviado se realmente enviou)
        try {
            String mensagem = "Olá " + cliente.getNome() +
                    "! Seu horário na Barbearia Álvaro Santos foi confirmado para " +
                    req.data() + " às " + req.horarioInicio() + " ✂️";

            boolean enviado = wahaClient.sendText(cliente.getTelefone(), mensagem);
            if (enviado) {
                salvo.setEnviadoConfirmacao(true);
                agendamentoRepo.save(salvo);
            } else {
                System.err.println("WhatsApp NÃO enviado (WAHA retornou falha). Não marcando enviadoConfirmacao.");
            }


        } catch (Exception e) {
            System.err.println("Erro ao enviar WhatsApp:");
            e.printStackTrace();
            // Não interrompe o fluxo
        }

        // 8) Retorna o agendamento salvo
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
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Usuário autenticado inválido");
        }

        Cliente cliente = clienteRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        Agendamento ag = agendamentoRepo.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado"));

        // dono do agendamento
        if (!ag.getCliente().getId().equals(cliente.getId())) {
            throw new RuntimeException("Você não pode cancelar este agendamento");
        }

        // status
        if (ag.getStatus() == StatusAgendamento.CANCELADO) {
            return ag; // idempotente
        }
        if (ag.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RuntimeException("Agendamento concluído não pode ser cancelado");
        }
        if (ag.getStatus() != StatusAgendamento.AGENDADO) {
            throw new RuntimeException("Só é possível cancelar agendamentos com status AGENDADO");
        }

        // ✅ regra do cliente: só cancela até 2h antes
        LocalDateTime inicioAg = LocalDateTime.of(ag.getData(), ag.getHorarioInicio());
        LocalDateTime agora = LocalDateTime.now();

        long minutosAte = java.time.Duration.between(agora, inicioAg).toMinutes();
        if (minutosAte < 120) {
            throw new RuntimeException("Cancelamento permitido apenas até 2 horas antes do horário");
        }

        // cancela
        ag.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepo.save(ag);

        // WhatsApp
        try {
            String mensagem;

            if (ag.isPago()) {
                mensagem = "❌ Agendamento cancelado\n\n" +
                        "Olá, " + cliente.getNome() + "!\n" +
                        "Seu agendamento foi cancelado com sucesso.\n\n" +
                        "📅 Data: " + ag.getData() + "\n" +
                        "⏰ Horário: " + ag.getHorarioInicio() + "\n\n" +
                        "💳 Observação: seu agendamento estava pago.\n" +
                        "Entre em contato com o barbeiro para tratar o reembolso. 💈";
            } else {
                mensagem = "❌ Agendamento cancelado\n\n" +
                        "Olá, " + cliente.getNome() + "!\n" +
                        "Seu agendamento foi cancelado com sucesso.\n\n" +
                        "📅 Data: " + ag.getData() + "\n" +
                        "⏰ Horário: " + ag.getHorarioInicio() + "\n\n" +
                        "Se precisar, é só agendar novamente 💈";
            }

            wahaClient.sendText(cliente.getTelefone(), mensagem);
        } catch (Exception e) {
            System.err.println("Erro ao enviar WhatsApp de cancelamento: " + e.getMessage());
        }

        return salvo;
    }


}