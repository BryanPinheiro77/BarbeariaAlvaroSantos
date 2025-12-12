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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PagamentoService {

    @Value("${mp.access-token}")
    private String mpToken;

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

        Pagamento pagamento = new Pagamento();
        pagamento.setAgendamento(agendamento);
        pagamento.setValor(agendamento.getServico().getPreco());
        pagamento.setMetodo(req.tipoPagamento());
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
    // 2) CHECKOUT PRO — preferência REAL sem external_reference
    // =============================================================
    private PagamentoCreateResponse criarCheckoutPro(Pagamento pagamento) {

        String url = "https://api.mercadopago.com/checkout/preferences";

        Map<String, Object> item = Map.of(
                "title", "Pagamento #" + pagamento.getId(),
                "currency_id", "BRL",
                "quantity", 1,
                "unit_price", pagamento.getValor().doubleValue()
        );

        // NÃO COLOCA external_reference
        Map<String, Object> body = Map.of(
                "items", List.of(item),
                "notification_url", "https://botchiest-unpenuriously-zenobia.ngrok-free.dev/pagamentos/webhook"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mpToken);

        RestTemplate client = new RestTemplate();
        ResponseEntity<Map> resp =
                client.postForEntity(url, new HttpEntity<>(body, headers), Map.class);

        String preferenceId = resp.getBody().get("id").toString();
        String initPoint = resp.getBody().get("init_point").toString();

        pagamento.setGatewayId(preferenceId);
        pagamentoRepo.save(pagamento);

        System.out.println("✅ Checkout criado | PagamentoID=" + pagamento.getId()
                + " | PreferenceID=" + preferenceId);

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
    // 3) PIX DIRECT (REAL)
    // =============================================================
    private PagamentoCreateResponse criarPixDirect(Pagamento pagamento) {

        String url = "https://api.mercadopago.com/v1/payments";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mpToken);

        Map<String, Object> payer = Map.of(
                "email", "test_user_123@testuser.com"
        );

        Map<String, Object> body = Map.of(
                "transaction_amount", pagamento.getValor().doubleValue(),
                "description", "Pagamento #" + pagamento.getId(),
                "payment_method_id", "pix",
                "payer", payer
        );

        RestTemplate client = new RestTemplate();
        try {
            ResponseEntity<Map> resp =
                    client.postForEntity(url, new HttpEntity<>(body, headers), Map.class);

            System.out.println("🔁 MP /v1/payments status=" + resp.getStatusCode().value() + " body=" + resp.getBody());

            Map<String, Object> payment = resp.getBody();
            if (payment == null) {
                throw new RuntimeException("Erro ao criar pagamento PIX: resposta vazia");
            }

            // id do payment
            Long paymentId = Long.valueOf(payment.get("id").toString());

            pagamento.setGatewayId(paymentId.toString());
            pagamentoRepo.save(pagamento);

            // Segurança: cheque se point_of_interaction e transaction_data existem
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

            String qrBase64 = txData.get("qr_code_base64").toString();
            String copiaCola = txData.get("qr_code").toString();

            System.out.println("⚡ PIX criado | paymentId = " + paymentId);

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
        if (payments == null || payments.isEmpty()) {
            System.out.println("⚠ merchant_order SEM payments ainda");
            return;
        }

        Long paymentId = Long.valueOf(payments.get(0).get("id").toString());
        processarPagamentoDireto(paymentId);
    }

    // =============================================================
    // 5) PROCESSAR PAYMENT (Pix Direct + Checkout Pro)
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

            // PIX Direct (não possui preference_id)
            if (pay.get("preference_id") == null) {

                Pagamento pagamento = pagamentoRepo.findByGatewayId(paymentId.toString());
                if (pagamento == null) return;

                String status = pay.get("status").toString();
                atualizarStatusPagamento(pagamento, status);
                return;
            }

            // Checkout Pro
            String preferenceId = pay.get("preference_id").toString();
            Pagamento pagamento = pagamentoRepo.findByGatewayId(preferenceId);

            if (pagamento == null) return;

            String status = pay.get("status").toString();
            atualizarStatusPagamento(pagamento, status);

        } catch (HttpClientErrorException.NotFound e) {
            System.out.println("⚠ Payment " + paymentId + " não encontrado (simulador).");
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
        pagamento.setCriadoEm(LocalDateTime.now());
        pagamentoRepo.save(pagamento);

        if (novoStatus == StatusPagamento.PAGO) {
            Agendamento ag = pagamento.getAgendamento();
            ag.setPago(true);
            agendamentoRepo.save(ag);
            System.out.println("✅ AGENDAMENTO MARCADO COMO PAGO!");
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
            return pagamento; // já está pago
        }

        pagamento.setStatus(StatusPagamento.PAGO);

        // marca agendamento como pago
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

}
