package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.PagamentoCreateRequest;
import com.barbearia.agenda.dto.PagamentoCreateResponse;
import com.barbearia.agenda.dto.PagamentoListDTO;
import com.barbearia.agenda.model.Pagamento;
import com.barbearia.agenda.service.PagamentoService;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    //Buscar pagamento por id
    @GetMapping("/{id}")
    public ResponseEntity<PagamentoListDTO> buscarPorId(@PathVariable Long id) {
        Pagamento pagamento = pagamentoService.buscarPorId(id);
        return ResponseEntity.ok(new PagamentoListDTO(pagamento));
    }

    //Buscar pagamentos por status
    @GetMapping
    public ResponseEntity<List<PagamentoListDTO>> listarPorStatus(
        @RequestParam(required = false) String status
                ) {
        List<Pagamento> lista = pagamentoService.listarPorStatus(status);
        List<PagamentoListDTO> dto = lista.stream()
                .map(PagamentoListDTO::new)
                .toList();
        return ResponseEntity.ok(dto);
    }

    //Buscar por agendamento
    @GetMapping("/agendamentos/{id}")
    public ResponseEntity<List<PagamentoListDTO>> listarPorAgendamento(@PathVariable Long id) {
        List<Pagamento> lista = pagamentoService.listarPorAgendamento(id);
        List<PagamentoListDTO> dto = lista.stream()
                .map(PagamentoListDTO::new)
                .toList();
        return ResponseEntity.ok(dto);
    }

    //Cancelar pagamento
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id) {
        Pagamento pagamento = pagamentoService.cancelar(id);
        return ResponseEntity.ok(new PagamentoListDTO(pagamento));
    }

    //Confirmar pagamento manualmente
    @PatchMapping("/{id}/confirmar-manual")
    public ResponseEntity<PagamentoListDTO> confirmarManual(@PathVariable Long id) {
        Pagamento pagamento = pagamentoService.confirmarManual(id);
        return ResponseEntity.ok(new PagamentoListDTO(pagamento));
    }

    // -------------- MOCK WEBHOOK PARA TESTES --------------
    @PostMapping("/mock/webhook/{id}")
    public ResponseEntity<?> mockWebhook(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        System.out.println("🧪 MOCK WEBHOOK → Pagamento " + id + " | Status = " + status);

        // Simula retorno do Mercado Pago
        pagamentoService.mockStatus(id, status);

        return ResponseEntity.ok(Map.of(
                "mensagem", "Mock enviado com sucesso",
                "paymentId", id,
                "status", status
        ));
    }
}
