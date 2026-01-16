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
import java.util.List;
import java.util.Map;

@Service
public class PagamentoService {

    @Value("${mp.access-token}")
    private String mpToken;

    // DICA: se quiser, extraia isso para application.properties no futuro
    private static final String NOTIFICATION_URL =
            "https://botchiest-unpenuriously-zenobia.ngrok-free.dev/pagamentos/webhook";

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

        // IMPORTANTE:
        // external_reference = ID do seu Pagamento, para mapear webhook -> pagamento interno SEM depender de preference_id
        Map<String, Object> body = Map.of(
                "items", List.of(item),
                "external_reference", pagamento.getId().toString(),
                "notification_url", NOTIFICATION_URL
        );

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

        String preferenceId = responseBody.get("id").toString();

        // Atenção: no MP Brasil, token de teste pode vir como APP_USR.
        // Então o melhor é escolher init_point baseado na presença de sandbox_init_point.
        String initPoint = responseBody.get("init_point").toString();
        if (responseBody.get("sandbox_init_point") != null) {
            // se existir, melhor para testes
            initPoint = responseBody.get("sandbox_init_point").toString();
        }

        // Salva gatewayId como preferenceId (continua útil como fallback)
        pagamento.setGatewayId(preferenceId);
        pagamentoRepo.save(pagamento);

        System.out.println("✅ Checkout Pro criado | pagamentoId=" + pagamento.getId()
                + " | preferenceId=" + preferenceId
                + " | initPoint=" + initPoint);

        return new PagamentoCreateResponse(
                pagamento.getId(),
                pagamento.getMetodo(),
                pagamento.getStatus().name(),
                null,
                null,
                initPoint
        );
    }

    // =============================================================
    // 3) PIX DIRECT (teste/real) — cria payment e salva gatewayId = paymentId
    // =============================================================
    private PagamentoCreateResponse criarPixDirect(Pagamento pagamento) {

        String url = "https://api.mercadopago.com/v1/payments";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mpToken);

        // IMPORTANTE:
        // Para PIX direct em ambiente de teste, o email ideal é de um TEST USER criado no painel.
        // Se usar email fake, pode dar 401/erro.
        Map<String, Object> payer = Map.of(
                "email", "test_user_123@testuser.com"
        );

        Map<String, Object> body = Map.of(
                "transaction_amount", pagamento.getValor().doubleValue(),
                "description", "Pagamento #" + pagamento.getId(),
                "payment_method_id", "pix",
                "payer", payer,
                // também ajuda mapear retorno do MP pro seu pagamento interno
                "external_reference", pagamento.getId().toString()
        );

        RestTemplate client = new RestTemplate();

        try {
            ResponseEntity<Map> resp =
                    client.postForEntity(url, new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> payment = resp.getBody();
            System.out.println("🔁 MP /v1/payments status=" + resp.getStatusCode().value() + " body=" + payment);

            if (payment == null) {
                throw new RuntimeException("Erro ao criar pagamento PIX: resposta vazia");
            }

            Long paymentId = Long.valueOf(payment.get("id").toString());

            // Para PIX direct, o gatewayId é o paymentId
            pagamento.setGatewayId(paymentId.toString());
            pagamentoRepo.save(pagamento);

            Object poiObj = payment.get("point_of_interaction");
            if (!(poiObj instanceof Map)) {
                throw new RuntimeException("Resposta MP não contém point_of_interaction: " + payment);
            }
            Map<String, Object> poi = (Map<String, Object>) poiObj;

            Object txObj = poi.get("transaction_data");
            if (!(txObj instanceof Map)) {
                throw new RuntimeException("Resposta MP não contém transaction_data: " + poi);
            }
            Map<String, Object> txData = (Map<String, Object>) txObj;

            String qrBase64 = String.valueOf(txData.get("qr_code_base64"));
            String copiaCola = String.valueOf(txData.get("qr_code"));

            System.out.println("⚡ PIX criado | pagamentoIdInterno=" + pagamento.getId() + " | paymentIdMP=" + paymentId);

            return new PagamentoCreateResponse(
                    pagamento.getId(),
                    pagamento.getMetodo(),
                    pagamento.getStatus().name(),
                    qrBase64,
                    copiaCola,
                    null
            );

        } catch (HttpClientErrorException e) {
            String respBody = e.getResponseBodyAsString();
            System.out.println("❌ Erro HTTP ao criar PIX: " + e.getStatusCode() + " - " + respBody);
            throw new RuntimeException("Erro ao criar pagamento PIX: " + respBody, e);
        } catch (Exception e) {
            System.out.println("❌ Erro inesperado ao criar PIX: " + e.getMessage());
            throw new RuntimeException("Erro ao criar pagamento PIX: " + e.getMessage(), e);
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

        // pega o primeiro payment associado
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

            // (A) Melhor caminho: achar pelo external_reference (ID do seu Pagamento)
            if (externalRefObj != null) {
                Long pagamentoIdInterno = Long.valueOf(externalRefObj.toString());
                Pagamento pagamento = pagamentoRepo.findById(pagamentoIdInterno).orElse(null);

                if (pagamento != null) {
                    atualizarStatusPagamento(pagamento, status);
                    return;
                }
            }

            // (B) Fallback: Checkout Pro usando preference_id (quando vier)
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

            // (C) Fallback: Pix Direct (gatewayId = paymentId)
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
