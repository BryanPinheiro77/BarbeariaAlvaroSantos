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
    // 2) CHECKOUT PRO — preference_id + external_reference
    // =============================================================
    private PagamentoCreateResponse criarCheckoutPro(Pagamento pagamento) {

        String url = "https://api.mercadopago.com/checkout/preferences";

        Map<String, Object> item = Map.of(
                "title", "Pagamento #" + pagamento.getId(),
                "currency_id", "BRL",
                "quantity", 1,
                "unit_price", pagamento.getValor().doubleValue()
        );

        Map<String, Object> body = Map.of(
                "items", List.of(item),
                // vamos usar o ID interno como external_reference
                "external_reference", pagamento.getId().toString(),
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

        // no banco continua guardando o preference_id
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
    // 3) PIX DIRECT (fake temporário)
    // =============================================================
    private PagamentoCreateResponse criarPixDirect(Pagamento pagamento) {

        pagamento.setGatewayId("mp_pix_fake_001");
        pagamentoRepo.save(pagamento);

        return new PagamentoCreateResponse(
                pagamento.getId(),
                pagamento.getMetodo(),
                pagamento.getStatus().name(),
                "data:image/png;base64,...",
                "0002010102122689...",
                null
        );
    }

    // =============================================================
    // 4) PROCESSAR MERCHANT_ORDER (topic=merchant_order)
    // =============================================================
    public void processarWebhook(Long merchantOrderId) {

        String urlOrder = "https://api.mercadopago.com/merchant_orders/" + merchantOrderId;

        RestTemplate rest = new RestTemplate();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(mpToken);

        ResponseEntity<Map> resp = rest.exchange(
                urlOrder, HttpMethod.GET, new HttpEntity<>(h), Map.class);

        Map<String, Object> order = resp.getBody();
        if (order == null) {
            System.out.println("⚠ merchant_order veio nulo");
            return;
        }

        System.out.println("💾 merchant_order: " + order);

        List<Map<String, Object>> payments = (List<Map<String, Object>>) order.get("payments");
        if (payments == null || payments.isEmpty()) {
            System.out.println("⚠ merchant_order SEM payments ainda");
            return;
        }

        Long paymentId = Long.valueOf(payments.get(0).get("id").toString());
        System.out.println("👉 Chamando processarPagamentoDireto com paymentId=" + paymentId);
        processarPagamentoDireto(paymentId);
    }

    // =============================================================
    // 5) PROCESSAR PAYMENT DIRETO (topic=payment)
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
            if (pay == null) {
                System.out.println("⚠ payment veio nulo");
                return;
            }

            System.out.println("💾 Detalhe do payment: " + pay);

            // 1) tenta preference_id
            String preferenceId = pay.get("preference_id") != null
                    ? pay.get("preference_id").toString()
                    : null;

            // 2) tenta external_reference (id interno do Pagamento)
            String externalRef = pay.get("external_reference") != null
                    ? pay.get("external_reference").toString()
                    : null;

            Pagamento pagamento = null;

            if (preferenceId != null && !preferenceId.isBlank()) {
                System.out.println("🔎 Buscando pagamento por preference_id=" + preferenceId);
                pagamento = pagamentoRepo.findByGatewayId(preferenceId);
            }

            if (pagamento == null && externalRef != null && !externalRef.isBlank()) {
                try {
                    Long pagId = Long.valueOf(externalRef);
                    System.out.println("🔎 Buscando pagamento por external_reference (id interno)=" + pagId);
                    pagamento = pagamentoRepo.findById(pagId).orElse(null);
                } catch (NumberFormatException e) {
                    System.out.println("⚠ external_reference não é numérico: " + externalRef);
                }
            }

            if (pagamento == null) {
                System.out.println("⚠ Não achou pagamento no banco para preference_id="
                        + preferenceId + " e external_reference=" + externalRef);
                return;
            }

            String status = pay.get("status").toString();
            atualizarStatusPagamento(pagamento, status);

        } catch (HttpClientErrorException.NotFound e) {
            System.out.println("⚠ Payment " + paymentId + " não encontrado (simulador / teste inválido).");
        } catch (HttpClientErrorException e) {
            System.out.println("⚠ Erro HTTP ao buscar payment "
                    + paymentId + ": " + e.getStatusCode()
                    + " - " + e.getResponseBodyAsString());
        }
    }

    // =============================================================
    // 6) ATUALIZAR STATUS
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
}
