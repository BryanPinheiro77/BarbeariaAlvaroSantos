package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.PagamentoCreateRequest;
import com.barbearia.agenda.dto.PagamentoCreateResponse;
import com.barbearia.agenda.service.PagamentoService;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;

    public PagamentoController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @PostMapping("/criar")
    public ResponseEntity<PagamentoCreateResponse> criar(@RequestBody PagamentoCreateRequest req) {
        return ResponseEntity.ok(pagamentoService.criarPagamento(req));
    }

    @PermitAll
    @CrossOrigin(origins = "*")
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody Map<String, Object> payload) {

        System.out.println("🔔 Webhook recebido: " + payload);

        // FORMATO REAL (topic + resource)
        if (payload.containsKey("topic")) {
            String topic = payload.get("topic").toString();

            if (topic.equals("merchant_order")) {
                String resource = payload.get("resource").toString();
                Long merchantOrderId = extractId(resource);
                pagamentoService.processarWebhook(merchantOrderId);
                return ResponseEntity.ok().build();
            }

            if (topic.equals("payment")) {
                String resource = payload.get("resource").toString();
                Long paymentId = extractId(resource);
                pagamentoService.processarPagamentoDireto(paymentId);
                return ResponseEntity.ok().build();
            }
        }

        // FORMATO DO SIMULADOR (type + data.id)
        if (payload.containsKey("type")) {
            String type = payload.get("type").toString();

            if (type.equals("merchant_order")) {
                Long id = Long.valueOf(payload.get("id").toString());
                pagamentoService.processarWebhook(id);
                return ResponseEntity.ok().build();
            }

            if (type.equals("payment") && payload.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                Long paymentId = Long.valueOf(data.get("id").toString());
                pagamentoService.processarPagamentoDireto(paymentId);
                return ResponseEntity.ok().build();
            }
        }

        System.out.println("⚠ Nenhum ID encontrado no webhook");
        return ResponseEntity.ok().build();
    }

    private Long extractId(String resource) {
        if (resource.contains("/")) {
            return Long.valueOf(resource.substring(resource.lastIndexOf("/") + 1));
        }
        return Long.valueOf(resource);
    }
}
