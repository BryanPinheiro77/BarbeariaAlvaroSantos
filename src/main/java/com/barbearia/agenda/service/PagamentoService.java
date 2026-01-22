package com.barbearia.agenda.service;

import com.barbearia.agenda.dto.PagamentoCreateRequest;
import com.barbearia.agenda.dto.PagamentoCreateResponse;
import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.model.Pagamento;
import com.barbearia.agenda.model.StatusPagamento;
import com.barbearia.agenda.model.TipoPagamentoStrategy;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.repository.PagamentoRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PagamentoService {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${mp.access-token}")
    private String mpToken;

    @Value("${mp.notification-url}")
    private String notificationUrl;

    /**
     * Opcional: controle explícito de sandbox via env var.
     * No Railway, se quiser usar:
     * MP_SANDBOX=false (produção) / true (sandbox)
     *
     * Se você não criar, o default é false.
     */
    @Value("${mp.sandbox:false}")
    private boolean mpSandbox;

    private final AgendamentoRepository agendamentoRepo;
    private final PagamentoRepository pagamentoRepo;

    public PagamentoService(AgendamentoRepository agendamentoRepo,
                            PagamentoRepository pagamentoRepo) {
        this.agendamentoRepo = agendamentoRepo;
        this.pagamentoRepo = pagamentoRepo;
    }

    // =============================================================
    // 1) CRIAR PAGAMENTO
    // =============================================================
    public PagamentoCreateResponse criarPagamento(PagamentoCreateRequest req) {

        Agendamento agendamento = agendamentoRepo.findById(req.agendamentoId())
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado"));

        if (agendamento.getServicos() == null || agendamento.getServicos().isEmpty()) {
            throw new RuntimeException("Agendamento não possui serviços para calcular o total.");
        }

        Pagamento pagamento = new Pagamento();
        pagamento.setAgendamento(agendamento);
        pagamento.setValor(calcularTotalAgendamento(agendamento));
        pagamento.setMetodo(req.tipoPagamento()); // "PIX" ou "CARTAO"
        pagamento.setStatus(StatusPagamento.PENDENTE);
        pagamento.setCriadoEm(LocalDateTime.now());
        pagamento = pagamentoRepo.save(pagamento);

        TipoPagamentoStrategy estrategia = req.estrategia();
        if (estrategia == null) estrategia = TipoPagamentoStrategy.CHECKOUT_PRO;

        return estrategia == TipoPagamentoStrategy.PIX_DIRECT
                ? criarPixDirect(pagamento)
                : criarCheckoutPro(pagamento);
    }

    // =============================================================
    // 2) CHECKOUT PRO — preferência com external_reference (ID interno)
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

        // Map mutável para permitir campos extras (statement_descriptor etc.)
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", pagamento.getId().toString());
        body.put("notification_url", notificationUrl);
        body.put("back_urls", backUrls);
        body.put("auto_return", "approved");

        // aparece na fatura do cartão (evite acentos e caracteres especiais)
        body.put("statement_descriptor", "BARBEARIA ALVARO");

        // opcional: deixar DESLIGADO por enquanto para não reduzir aprovações por "análise"
        // body.put("binary_mode", true);

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

    /**
     * Decide se retorna link de produção ou sandbox.
     * Regra:
     * 1) Se mp.sandbox=true => sandbox_init_point
     * 2) Senão, se token aparenta ser de teste => sandbox_init_point
     * 3) Caso contrário => init_point (produção)
     */
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
    // 3) PIX DIRECT (real)
    // =============================================================
    private PagamentoCreateResponse criarPixDirect(Pagamento pagamento) {

        String url = "https://api.mercadopago.com/v1/payments";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mpToken);

        // MERCADO PAGO: obrigatório para /v1/payments (evita duplicidade em retries)
        // Opção mais segura (determinística): usar o id do pagamento interno
        // Assim, se o front repetir a request, o MP não cria outro pagamento duplicado.
        headers.add("X-Idempotency-Key", "pix-" + pagamento.getId());

        // Se você preferir sempre "novo pagamento" ao repetir, use UUID:
        // headers.add("X-Idempotency-Key", UUID.randomUUID().toString());

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
    // 4) PROCESSAR MERCHANT_ORDER (quando o webhook vier como merchant_order)
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

        System.out.println("🔎 merchant_order=" + merchantOrderId + " body=" + order);

        List<Map<String, Object>> payments = (List<Map<String, Object>>) order.get("payments");
        if (payments == null || payments.isEmpty()) {
            System.out.println("⚠ merchant_order SEM payments ainda");
            return;
        }

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

            System.out.println("💳 MP paymentId=" + paymentId
                    + " | status=" + status
                    + " | external_reference=" + externalRefObj
                    + " | preference_id=" + preferenceObj);

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
                } else {
                    System.out.println("⚠ Não achei pagamento por preference_id=" + preferenceId);
                }
                return;
            }

            Pagamento pagamento = pagamentoRepo.findByGatewayId(paymentId.toString());
            if (pagamento != null) {
                atualizarStatusPagamento(pagamento, status);
            } else {
                System.out.println("⚠ Não achei pagamento por gatewayId(paymentId)=" + paymentId);
            }

        } catch (HttpClientErrorException.NotFound e) {
            System.out.println("⚠ Payment " + paymentId + " não encontrado no MP.");
        } catch (HttpClientErrorException e) {
            System.out.println("❌ Erro ao consultar payment " + paymentId + ": " + e.getStatusCode()
                    + " body=" + e.getResponseBodyAsString());
        }
    }

    // =============================================================
    // 6) Atualizar status interno
    // =============================================================
    private void atualizarStatusPagamento(Pagamento pagamento, String mpStatus) {

        StatusPagamento novoStatus = switch (mpStatus.toLowerCase()) {
            case "approved", "authorized" -> StatusPagamento.PAGO;
            case "rejected" -> StatusPagamento.FALHOU;
            case "cancelled", "refunded" -> StatusPagamento.CANCELADO;
            case "pending", "in_process" -> StatusPagamento.PENDENTE;
            default -> StatusPagamento.PENDENTE;
        };

        pagamento.setStatus(novoStatus);
        pagamento.setCriadoEm(LocalDateTime.now()); // se quiser, crie recebidoEm separado
        pagamentoRepo.save(pagamento);

        System.out.println("✅ Pagamento atualizado | id=" + pagamento.getId()
                + " | status=" + pagamento.getStatus());

        if (novoStatus == StatusPagamento.PAGO) {
            Agendamento ag = pagamento.getAgendamento();
            ag.setPago(true);
            agendamentoRepo.save(ag);
            System.out.println("✅ AGENDAMENTO MARCADO COMO PAGO! agendamentoId=" + ag.getId());
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

    // =============================================================
    // 11) CONFIRMAR MANUALMENTE
    // =============================================================
    public Pagamento confirmarManual(Long id) {
        Pagamento pagamento = buscarPorId(id);

        if (pagamento.getStatus() == StatusPagamento.PAGO) {
            return pagamento;
        }

        pagamento.setStatus(StatusPagamento.PAGO);

        Agendamento ag = pagamento.getAgendamento();
        ag.setPago(true);
        agendamentoRepo.save(ag);

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
