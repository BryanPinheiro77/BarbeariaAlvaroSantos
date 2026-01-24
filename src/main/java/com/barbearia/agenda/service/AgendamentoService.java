package com.barbearia.agenda.service;

import com.barbearia.agenda.dto.AgendamentoCreateRequest;
import com.barbearia.agenda.dto.AdminAgendamentoCreateRequest;
import com.barbearia.agenda.integration.WahaClient;
import com.barbearia.agenda.model.*;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.repository.ClienteRepository;
import com.barbearia.agenda.repository.ServicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    // ==========================================================
    // CLIENTE (APP) - já existia
    // ==========================================================
    @Transactional
    public Agendamento criar(AgendamentoCreateRequest req, String email) {

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Usuário autenticado inválido");
        }

        validarServicos(req.servicosIds());

        Cliente cliente = clienteRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        List<Servico> servicos = buscarServicosOuFalhar(req.servicosIds());

        LocalTime inicio = req.horarioInicio();
        LocalTime fim = calcularFim(inicio, servicos);

        validarConflito(req.data(), inicio, fim);

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

        vincularServicos(a, servicos);

        Agendamento salvo = agendamentoRepo.save(a);

        // WhatsApp (se tiver telefone)
        tentarEnviarConfirmacaoWhatsApp(salvo, "Olá " + cliente.getNome()
                + "! Seu horário na Barbearia Álvaro Santos foi confirmado para "
                + req.data() + " às " + req.horarioInicio() + " ✂️");

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

        if (!ag.getCliente().getId().equals(cliente.getId())) {
            throw new RuntimeException("Você não pode cancelar este agendamento");
        }

        if (ag.getStatus() == StatusAgendamento.CANCELADO) return ag;
        if (ag.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RuntimeException("Agendamento concluído não pode ser cancelado");
        }
        if (ag.getStatus() != StatusAgendamento.AGENDADO) {
            throw new RuntimeException("Só é possível cancelar agendamentos com status AGENDADO");
        }

        LocalDateTime inicioAg = LocalDateTime.of(ag.getData(), ag.getHorarioInicio());
        LocalDateTime agora = LocalDateTime.now();

        long minutosAte = java.time.Duration.between(agora, inicioAg).toMinutes();
        if (minutosAte < 120) {
            throw new RuntimeException("Cancelamento permitido apenas até 2 horas antes do horário");
        }

        ag.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepo.save(ag);

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

            if (cliente.getTelefone() != null && !cliente.getTelefone().isBlank()) {
                wahaClient.sendText(cliente.getTelefone(), mensagem);
            }
        } catch (Exception e) {
            System.err.println("Erro ao enviar WhatsApp de cancelamento: " + e.getMessage());
        }

        return salvo;
    }

    // ==========================================================
    // ADMIN / BARBEIRO - criar agendamento
    // Regras:
    // - precisa do NOME
    // - se existir cadastro, usa o Cliente cadastrado
    // - se não existir, cria um Cliente "na hora" (somente com nome; telefone opcional)
    // - admin decide se já está pago
    // ==========================================================
    @Transactional
    public Agendamento criarAdmin(AdminAgendamentoCreateRequest req) {

        if (req == null) throw new RuntimeException("Payload inválido");

        // nome obrigatório (mesmo se vier clienteId)
        String nomeReq = req.clienteNome();
        if ((nomeReq == null || nomeReq.isBlank()) && req.clienteId() == null) {
            throw new RuntimeException("Informe o nome do cliente");
        }

        validarServicos(req.servicosIds());

        if (req.data() == null) throw new RuntimeException("Informe a data");
        if (req.horarioInicio() == null) throw new RuntimeException("Informe o horário de início");

        Cliente cliente = resolverClienteAdmin(req);

        List<Servico> servicos = buscarServicosOuFalhar(req.servicosIds());

        LocalTime inicio = req.horarioInicio();
        LocalTime fim = calcularFim(inicio, servicos);

        validarConflito(req.data(), inicio, fim);

        Agendamento a = new Agendamento();
        a.setCliente(cliente);

        a.setData(req.data());
        a.setHorarioInicio(inicio);
        a.setHorarioFim(fim);

        a.setFormaPagamentoTipo(req.formaPagamentoTipo());
        a.setFormaPagamentoModo(req.formaPagamentoModo());

        // como o DTO do admin não tem lembrete, defina um padrão (ajuste como preferir)
        a.setLembreteMinutos(60);

        a.setStatus(StatusAgendamento.AGENDADO);

        boolean pago = Boolean.TRUE.equals(req.pago());
        a.setPago(pago);

        a.setEnviadoConfirmacao(false);
        a.setEnviadoLembrete(false);
        a.setCriadoEm(LocalDateTime.now());

        vincularServicos(a, servicos);

        Agendamento salvo = agendamentoRepo.save(a);

        // WhatsApp: só tenta se tiver telefone (cadastrado ou informado)
        // (se você quer SEMPRE exigir telefone no admin, valide antes)
        String tel = cliente.getTelefone();
        if (tel != null && !tel.isBlank()) {
            String msg = montarMensagemConfirmacaoAdmin(salvo);
            tentarEnviarConfirmacaoWhatsApp(salvo, msg);
        }

        return salvo;
    }

    // ==========================================================
    // Helpers
    // ==========================================================
    private void validarServicos(List<Long> servicosIds) {
        if (servicosIds == null || servicosIds.isEmpty()) {
            throw new RuntimeException("Selecione pelo menos 1 serviço");
        }
    }

    private List<Servico> buscarServicosOuFalhar(List<Long> ids) {
        List<Servico> servicos = servicoRepo.findAllById(ids);
        if (servicos.size() != ids.size()) {
            throw new RuntimeException("Um ou mais serviços são inválidos");
        }
        return servicos;
    }

    private LocalTime calcularFim(LocalTime inicio, List<Servico> servicos) {
        if (inicio == null) throw new RuntimeException("Horário de início inválido");

        int duracaoTotal = servicos.stream()
                .mapToInt(Servico::getDuracaoMinutos)
                .sum();

        return inicio.plusMinutes(duracaoTotal);
    }

    private void validarConflito(java.time.LocalDate data, LocalTime inicio, LocalTime fim) {
        boolean conflito = agendamentoRepo
                .existsByDataAndStatusNotAndHorarioInicioLessThanAndHorarioFimGreaterThan(
                        data,
                        StatusAgendamento.CANCELADO,
                        fim,
                        inicio
                );

        if (conflito) {
            throw new RuntimeException("Horário já reservado!");
        }
    }

    private void vincularServicos(Agendamento a, List<Servico> servicos) {
        for (Servico s : servicos) {
            AgendamentoServico link = new AgendamentoServico();
            link.setAgendamento(a);
            link.setServico(s);
            a.getServicos().add(link);
        }
    }

    /**
     * Estratégia:
     * 1) Se vier clienteId -> usa ele.
     * 2) Senão, tenta achar por telefone (se vier).
     * 3) Senão, tenta achar por nome (primeiro match).
     * 4) Se não achou -> cria Cliente "na hora" com nome (telefone opcional).
     *
     * Observação importante:
     * - Se no seu banco/entidade Cliente o telefone for NOT NULL/UNIQUE e você realmente quer permitir “só nome”,
     *   ajuste a coluna/validação (ou então torne telefone obrigatório no admin).
     */
    private Cliente resolverClienteAdmin(AdminAgendamentoCreateRequest req) {

        // 1) por id
        if (req.clienteId() != null) {
            return clienteRepo.findById(req.clienteId())
                    .orElseThrow(() -> new RuntimeException("ClienteId inválido"));
        }

        // 2) por telefone (se existir no DTO)
        String telReq = req.clienteTelefone();
        if (telReq != null && !telReq.isBlank()) {
            // precisa existir no seu ClienteRepository:
            // Optional<Cliente> findByTelefone(String telefone);
            Optional<Cliente> porTelefone = clienteRepo.findByTelefone(telReq);
            if (porTelefone.isPresent()) return porTelefone.get();
        }

        // 3) por nome
        String nomeReq = req.clienteNome();
        if (nomeReq != null && !nomeReq.isBlank()) {
            // opções de repository:
            // Optional<Cliente> findFirstByNomeIgnoreCase(String nome);
            // List<Cliente> findByNomeContainingIgnoreCase(String nome);
            Optional<Cliente> porNome = clienteRepo.findFirstByNomeIgnoreCase(nomeReq.trim());
            if (porNome.isPresent()) return porNome.get();
        } else {
            throw new RuntimeException("Informe o nome do cliente");
        }

        // 4) criar na hora
        Cliente novo = new Cliente();
        novo.setNome(nomeReq.trim());

        // telefone opcional (se vier, salva)
        if (telReq != null && !telReq.isBlank()) {
            novo.setTelefone(telReq);
        }

        // email não existe nesse fluxo
        // (se sua entidade exigir email NOT NULL, você vai precisar ajustar a entidade ou criar um placeholder controlado)
        novo.setEmail(null);

        return clienteRepo.save(novo);
    }

    private String montarMensagemConfirmacaoAdmin(Agendamento a) {
        String nome = a.getCliente() != null ? a.getCliente().getNome() : "cliente";

        String pagoTxt = a.isPago() ? "✅ Pagamento: CONFIRMADO" : "💳 Pagamento: PENDENTE";
        String modo = a.getFormaPagamentoModo() != null ? a.getFormaPagamentoModo() : "-";
        String tipo = a.getFormaPagamentoTipo() != null ? a.getFormaPagamentoTipo() : "-";

        return "✂️ Agendamento confirmado\n\n" +
                "Olá, " + nome + "!\n" +
                "Seu horário na Barbearia Álvaro Santos foi agendado.\n\n" +
                "📅 Data: " + a.getData() + "\n" +
                "⏰ Horário: " + a.getHorarioInicio() + "\n\n" +
                "💳 " + pagoTxt + "\n" +
                "Forma: " + tipo + " (" + modo + ")\n\n" +
                "Se precisar alterar/cancelar, fale com a barbearia. 💈";
    }

    private void tentarEnviarConfirmacaoWhatsApp(Agendamento agendamento, String mensagem) {
        try {
            String tel = agendamento.getCliente() != null ? agendamento.getCliente().getTelefone() : null;
            if (tel == null || tel.isBlank()) return;

            boolean enviado = wahaClient.sendText(tel, mensagem);
            if (enviado) {
                agendamento.setEnviadoConfirmacao(true);
                agendamentoRepo.save(agendamento);
            } else {
                System.err.println("WhatsApp NÃO enviado (WAHA retornou falha). Não marcando enviadoConfirmacao.");
            }
        } catch (Exception e) {
            System.err.println("Erro ao enviar WhatsApp:");
            e.printStackTrace();
        }
    }
}
