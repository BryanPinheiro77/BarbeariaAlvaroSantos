package com.barbearia.agenda.service;

import com.barbearia.agenda.dto.PagamentoCreateRequest;
import com.barbearia.agenda.dto.PagamentoCreateResponse;
import com.barbearia.agenda.integration.WahaClient;
import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.model.Pagamento;
import com.barbearia.agenda.model.StatusAgendamento;
import com.barbearia.agenda.model.StatusPagamento;
import com.barbearia.agenda.model.TipoPagamentoStrategy;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.repository.PagamentoRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PagamentoService {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${mp.access-token}")
    private String mpToken;

    @Value("${mp.notification-url}")
    private String notificationUrl;

    @Value("${mp.sandbox:false}")
    private boolean mpSandbox;

    private static final int EXPIRACAO_MINUTOS = 15;

    private final AgendamentoRepository agendamentoRepo;
    private final PagamentoRepository pagamentoRepo;
    private final WahaClient wahaClient;

    public PagamentoService(
            AgendamentoRepository agendamentoRepo,
            PagamentoRepository pagamentoRepo,
            WahaClient wahaClient
    ) {
        this.agendamentoRepo = agendamentoRepo;
        this.pagamentoRepo = pagamentoRepo;
        this.wahaClient = wahaClient;
    }

    // =============================================================
    // 1) CRIAR PAGAMENTO (marca expiraEm em 15min)
    // =============================================================
    @Transactional
    public PagamentoCreateResponse criarPagamento(PagamentoCreateRequest req) {

        Agendamento agendamento = agendamentoRepo.findById(req.agendamentoId())
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado"));

        if (agendamento.getServicos() == null || agendamento.getServicos().isEmpty()) {
            throw new RuntimeException("Agendamento não possui serviços para calcular o total.");
        }

        // Se já expirou/cancelou, não gera pagamento
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
            throw new RuntimeException("Agendamento cancelado. Não é possível gerar pagamento.");
        }

        Pagamento pagamento = new Pagamento();
        pagamento.setAgendamento(agendamento);
        pagamento.setValor(calcularTotalAgendamento(agendamento));
        pagamento.setMetodo(req.tipoPagamento()); // "PIX" ou "CARTAO"
        pagamento.setStatus(StatusPagamento.PENDENTE);
        pagamento.setCriadoEm(LocalDateTime.now());
        pagamento.setExpiraEm(LocalDateTime.now().plusMinutes(EXPIRACAO_MINUTOS));
        pagamento = pagamentoRepo.save(pagamento);

        TipoPagamentoStrategy estrategia = req.estrategia();
        if (estrategia == null) estrategia = TipoPagamentoStrategy.CHECKOUT_PRO;

        return estrategia == TipoPagamentoStrategy.PIX_DIRECT
                ? criarPixDirect(pagamento)
                : criarCheckoutPro(pagamento);
    }

    // =============================================================
    // 2) CHECKOUT PRO
    // =============================================================
    private PagamentoCreateResponse criarCheckoutPro(Pagamento pagamento) {

        String url = "https://api.mercadopago.com/checkout/preferences";

        Map<String, Object> item = Map.of(
                "title", "Pagamento #" + pagamento.getId(),
                "currency_id", "BRL",
                "quantity", 1,
                "unit_price", pagamento.getValor().doubleValue()
        );

        String retorno = frontendUrl + "/pagamento/retorno";

        Map<String, Object> backUrls = Map.of(
                "success", retorno,
                "failure", retorno,
                "pending", retorno
        );

        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", pagamento.getId().toString());
        body.put("notification_url", notificationUrl);
        body.put("back_urls", backUrls);
        body.put("auto_return", "approved");
        body.put("statement_descriptor", "BARBEARIA ALVARO");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mpToken);

        RestTemplate client = new RestTemplate();
        ResponseEntity<Map> resp =
                client.postForEntity(url, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> responseBody = resp.getBody();
        if (responseBody == null) {
            throw new RuntimeException("Mercado Pago retornou body vazio ao criar preferência.");
        }

        String preferenceId = String.valueOf(responseBody.get("id"));
        String checkoutUrl = escolherCheckoutUrl(responseBody);

        pagamento.setGatewayId(preferenceId);
        pagamentoRepo.save(pagamento);

        return new PagamentoCreateResponse(
                pagamento.getId(),
                pagamento.getMetodo(),
                pagamento.getStatus().name(),
                null,
                null,
                checkoutUrl
        );
    }

    private String escolherCheckoutUrl(Map<String, Object> responseBody) {

        boolean tokenPareceTeste = mpToken != null && mpToken.startsWith("TEST-");
        boolean usarSandbox = mpSandbox || tokenPareceTeste;

        if (usarSandbox) {
            Object sandbox = responseBody.get("sandbox_init_point");
            if (sandbox == null) {
                throw new RuntimeException("Preferência retornou sem sandbox_init_point, mas o ambiente está em sandbox.");
            }
            return String.valueOf(sandbox);
        }

        Object initPoint = responseBody.get("init_point");
        if (initPoint == null) {
            throw new RuntimeException("Preferência retornou sem init_point (produção).");
        }
        return String.valueOf(initPoint);
    }

    // =============================================================
    // 3) PIX DIRECT
    // =============================================================
    private PagamentoCreateResponse criarPixDirect(Pagamento pagamento) {

        String url = "https://api.mercadopago.com/v1/payments";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mpToken);

        headers.add("X-Idempotency-Key", "pix-" + pagamento.getId());

        String emailCliente = pagamento.getAgendamento().getCliente().getEmail();
        if (emailCliente == null || emailCliente.isBlank()) {
            throw new RuntimeException("Cliente sem email cadastrado. Não é possível gerar PIX.");
        }

        Map<String, Object> payer = Map.of("email", emailCliente);

        Map<String, Object> body = Map.of(
                "transaction_amount", pagamento.getValor().doubleValue(),
                "description", "Pagamento #" + pagamento.getId(),
                "payment_method_id", "pix",
                "payer", payer,
                "external_reference", pagamento.getId().toString(),
                "notification_url", notificationUrl
        );

        RestTemplate client = new RestTemplate();

        try {
            ResponseEntity<Map> resp =
                    client.postForEntity(url, new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> payment = resp.getBody();
            if (payment == null) {
                throw new RuntimeException("Erro ao criar pagamento PIX: resposta vazia");
            }

            Long paymentId = Long.valueOf(String.valueOf(payment.get("id")));

            pagamento.setGatewayId(paymentId.toString());
            pagamentoRepo.save(pagamento);

            Map<String, Object> poi = (Map<String, Object>) payment.get("point_of_interaction");
            Map<String, Object> txData = poi == null ? null : (Map<String, Object>) poi.get("transaction_data");

            if (txData == null) {
                throw new RuntimeException("Pagamento PIX criado, mas sem transaction_data (QR). Resposta: " + payment);
            }

            String qrBase64 = String.valueOf(txData.get("qr_code_base64"));
            String copiaCola = String.valueOf(txData.get("qr_code"));

            return new PagamentoCreateResponse(
                    pagamento.getId(),
                    pagamento.getMetodo(),
                    pagamento.getStatus().name(),
                    qrBase64,
                    copiaCola,
                    null
            );

        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Erro ao criar pagamento PIX: " + e.getResponseBodyAsString(), e);
        }
    }

    // =============================================================
    // 4) PROCESSAR MERCHANT_ORDER
    // =============================================================
    public void processarWebhook(Long merchantOrderId) {

        String urlOrder = "https://api.mercadopago.com/merchant_orders/" + merchantOrderId;

        RestTemplate rest = new RestTemplate();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(mpToken);

        ResponseEntity<Map> resp = rest.exchange(
                urlOrder, HttpMethod.GET, new HttpEntity<>(h), Map.class);

        Map<String, Object> order = resp.getBody();
        if (order == null) return;

        List<Map<String, Object>> payments = (List<Map<String, Object>>) order.get("payments");
        if (payments == null || payments.isEmpty()) return;

        Long paymentId = Long.valueOf(payments.get(0).get("id").toString());
        processarPagamentoDireto(paymentId);
    }

    // =============================================================
    // 5) PROCESSAR PAYMENT (Checkout Pro + Pix Direct)
    // =============================================================
    public void processarPagamentoDireto(Long paymentId) {

        String url = "https://api.mercadopago.com/v1/payments/" + paymentId;

        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mpToken);

        try {
            ResponseEntity<Map> resp =
                    rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<String, Object> pay = resp.getBody();
            if (pay == null) return;

            String status = String.valueOf(pay.get("status"));
            Object externalRefObj = pay.get("external_reference");
            Object preferenceObj = pay.get("preference_id");

            if (externalRefObj != null) {
                Long pagamentoIdInterno = Long.valueOf(externalRefObj.toString());
                Pagamento pagamento = pagamentoRepo.findById(pagamentoIdInterno).orElse(null);
                if (pagamento != null) {
                    atualizarStatusPagamento(pagamento, status);
                    return;
                }
            }

            if (preferenceObj != null) {
                String preferenceId = preferenceObj.toString();
                Pagamento pagamento = pagamentoRepo.findByGatewayId(preferenceId);
                if (pagamento != null) {
                    atualizarStatusPagamento(pagamento, status);
                }
                return;
            }

            Pagamento pagamento = pagamentoRepo.findByGatewayId(paymentId.toString());
            if (pagamento != null) {
                atualizarStatusPagamento(pagamento, status);
            }

        } catch (HttpClientErrorException.NotFound e) {
            System.out.println("⚠ Payment " + paymentId + " não encontrado no MP.");
        } catch (HttpClientErrorException e) {
            System.out.println("❌ Erro ao consultar payment " + paymentId + ": " + e.getStatusCode()
                    + " body=" + e.getResponseBodyAsString());
        }
    }

    // =============================================================
    // 6) Atualizar status interno + confirmar agendamento se dentro do prazo
    // =============================================================
    @Transactional
    protected void atualizarStatusPagamento(Pagamento pagamento, String mpStatus) {

        StatusPagamento novoStatus = switch (mpStatus.toLowerCase()) {
            case "approved", "authorized" -> StatusPagamento.PAGO;
            case "rejected" -> StatusPagamento.FALHOU;
            case "cancelled", "refunded" -> StatusPagamento.CANCELADO;
            case "pending", "in_process" -> StatusPagamento.PENDENTE;
            default -> StatusPagamento.PENDENTE;
        };

        // Se já expirou internamente, não “revive” (evita confirmar fora do prazo)
        if (pagamento.getStatus() == StatusPagamento.EXPIRADO) {
            System.out.println("⚠ Pagamento já estava EXPIRADO internamente. Ignorando atualização para " + novoStatus);
            return;
        }

        pagamento.setStatus(novoStatus);
        pagamentoRepo.save(pagamento);

        if (novoStatus == StatusPagamento.PAGO) {
            confirmarAgendamentoSeDentroDoPrazo(pagamento);
        }
    }

    private void confirmarAgendamentoSeDentroDoPrazo(Pagamento pagamento) {
        Agendamento ag = pagamento.getAgendamento();

        // Se já cancelou por expiração, não confirma
        if (ag.getStatus() == StatusAgendamento.CANCELADO) {
            System.out.println("⚠ Pagamento aprovado, mas agendamento já está CANCELADO (provável expiração).");
            return;
        }

        // Se tinha expiração e passou, não confirma (evita “pago atrasado” confirmar)
        LocalDateTime expAg = ag.getExpiraEm();
        if (expAg != null && LocalDateTime.now().isAfter(expAg)) {
            System.out.println("⚠ Pagamento aprovado fora do prazo. Mantendo agendamento como está.");
            return;
        }

        ag.setPago(true);

        if (ag.getStatus() == StatusAgendamento.PAGAMENTO_PENDENTE) {
            ag.setStatus(StatusAgendamento.AGENDADO);
            ag.setExpiraEm(null);
        }

        agendamentoRepo.save(ag);

        // WhatsApp de confirmação (só quando efetivamente confirmou)
        try {
            var cliente = ag.getCliente();
            String mensagem = "Olá " + cliente.getNome() +
                    "! Seu horário na Barbearia Álvaro Santos foi confirmado para " +
                    ag.getData() + " às " + ag.getHorarioInicio() + " ✂️";
            wahaClient.sendText(cliente.getTelefone(), mensagem);
        } catch (Exception e) {
            System.err.println("Erro ao enviar WhatsApp de confirmação pós-pagamento:");
            e.printStackTrace();
        }
    }

    // =============================================================
    // 7) BUSCAR POR ID
    // =============================================================
    public Pagamento buscarPorId(Long id) {
        return pagamentoRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado"));
    }

    // =============================================================
    // 8) LISTAR POR STATUS
    // =============================================================
    public List<Pagamento> listarPorStatus(String status) {

        if (status == null || status.isBlank()) {
            return pagamentoRepo.findAll();
        }

        StatusPagamento st = StatusPagamento.valueOf(status.toUpperCase());
        return pagamentoRepo.findByStatus(st);
    }

    // =============================================================
    // 9) LISTAR POR AGENDAMENTO
    // =============================================================
    public List<Pagamento> listarPorAgendamento(Long agendamentoId) {
        return pagamentoRepo.findByAgendamentoId(agendamentoId);
    }

    // =============================================================
    // 10) CANCELAR MANUALMENTE
    // =============================================================
    public Pagamento cancelar(Long id) {
        Pagamento pagamento = buscarPorId(id);

        if (pagamento.getStatus() == StatusPagamento.PAGO) {
            throw new RuntimeException("Não é possível cancelar um pagamento já aprovado");
        }

        pagamento.setStatus(StatusPagamento.CANCELADO);
        pagamentoRepo.save(pagamento);

        return pagamento;
    }

    public void mockStatus(Long id, String status) {
        Pagamento pagamento = buscarPorId(id);
        atualizarStatusPagamento(pagamento, status);
    }

    private BigDecimal calcularTotalAgendamento(Agendamento ag) {
        return ag.getServicos().stream()
                .map(link -> link.getServico().getPreco())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
