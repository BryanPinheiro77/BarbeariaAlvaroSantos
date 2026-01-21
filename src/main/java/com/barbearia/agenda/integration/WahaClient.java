package com.barbearia.agenda.integration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Service
public class WahaClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${waha.base-url:http://localhost:3000}")
    private String baseUrl;

    @Value("${waha.api-key:}")
    private String apiKey;

    /**
     * Nome fixo da sessão conforme você pediu.
     * Se quiser voltar a parametrizar depois, basta trocar por @Value("${waha.session:default}")
     */
    private static final String SESSION = "default";

    // Retry curto (não segura transação nem trava request por muito tempo)
    private static final int START_RETRIES = 5;
    private static final long START_RETRY_SLEEP_MS = 1200;

    @PostConstruct
    public void init() {
        String normalized = normalizeBaseUrl(baseUrl);
        System.out.println("WAHA: Cliente configurado em: " + normalized + " | session=" + SESSION);

        // Tenta subir a sessão no start do back (melhor esforço)
        try {
            startSessionIfNeeded();
        } catch (Exception e) {
            System.err.println("WAHA: Não foi possível validar/iniciar sessão no startup: " + e.getMessage());
        }
    }

    /**
     * Envia texto usando a sessão default.
     * Se WAHA retornar 422 com sessão STOPPED, tenta startar e reenviar 1x.
     */
    public boolean sendText(String phoneNumber, String message) {
        String normalizedBase = normalizeBaseUrl(baseUrl);

        // Melhor esforço: garante sessão antes de enviar (rápido)
        startSessionIfNeeded();

        String url = normalizedBase + "/api/sendText";
        String formattedNumber = formatNumber(phoneNumber);

        Map<String, Object> body = Map.of(
                "session", SESSION,
                "chatId", formattedNumber + "@c.us",
                "text", message
        );

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("WAHA: Mensagem enviada para: " + phoneNumber);
                return true;
            }

            // Se veio 422, pode ser sessão parada — tenta start e reenvia 1 vez
            if (response.getStatusCode().value() == 422) {
                System.err.println("WAHA: 422 ao enviar. Tentando iniciar sessão e reenviar. Body=" + safeBody(response));
                forceStartSessionWithRetry();

                ResponseEntity<String> retry = restTemplate.postForEntity(url, request, String.class);
                if (retry.getStatusCode().is2xxSuccessful()) {
                    System.out.println("WAHA: Mensagem enviada (após restart/start) para: " + phoneNumber);
                    return true;
                }

                System.err.println("WAHA: Falha no reenvio. status=" + retry.getStatusCode() + " body=" + safeBody(retry));
                return false;
            }

            System.err.println("WAHA: Falha ao enviar. status=" + response.getStatusCode() + " body=" + safeBody(response));
            return false;

        } catch (RestClientException e) {
            System.err.println("WAHA: Erro de rede ao enviar mensagem: " + e.getMessage());
            return false;
        }
    }

    /**
     * Consulta o status da sessão default.
     * Retorna null se não conseguir consultar.
     *
     * Endpoint: GET /api/sessions/{name}
     */
    public String getSessionStatus() {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        String url = normalizedBase + "/api/sessions/" + SESSION;

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Map.class
            );

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                System.err.println("WAHA: getSessionStatus falhou. status=" + resp.getStatusCode());
                return null;
            }

            Object status = resp.getBody().get("status");
            return status == null ? null : status.toString();

        } catch (RestClientException e) {
            System.err.println("WAHA: Não foi possível obter status da sessão. " + e.getMessage());
            return null;
        }
    }

    /**
     * Se a sessão não estiver WORKING, tenta startar.
     * Não bloqueia por muito tempo e não lança erro fatal.
     */
    public void startSessionIfNeeded() {
        String status = getSessionStatus();
        if ("WORKING".equalsIgnoreCase(status)) return;

        // Se status veio null (erro de rede) ou STOPPED/PAUSED/etc, tenta start com retry curto.
        forceStartSessionWithRetry();
    }

    /**
     * Endpoint: POST /api/sessions/{name}/start
     * Faz retry curto porque às vezes o WAHA acabou de subir.
     */
    private void forceStartSessionWithRetry() {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        String url = normalizedBase + "/api/sessions/" + SESSION + "/start";

        for (int i = 1; i <= START_RETRIES; i++) {
            try {
                ResponseEntity<String> resp = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        new HttpEntity<>(buildHeaders()),
                        String.class
                );

                if (resp.getStatusCode().is2xxSuccessful()) {
                    // Confere se ficou WORKING
                    String status = getSessionStatus();
                    System.out.println("WAHA: startSession attempt=" + i + " status=" + status);
                    if ("WORKING".equalsIgnoreCase(status)) return;
                } else {
                    System.err.println("WAHA: startSession attempt=" + i + " HTTP=" + resp.getStatusCode()
                            + " body=" + (resp.getBody() == null ? "" : resp.getBody()));
                }
            } catch (RestClientException e) {
                System.err.println("WAHA: startSession attempt=" + i + " erro=" + e.getMessage());
            }

            sleepSilently(START_RETRY_SLEEP_MS);
        }

        // Melhor esforço: não derruba o fluxo
        System.err.println("WAHA: Não conseguiu colocar a sessão em WORKING após " + START_RETRIES + " tentativas.");
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // WAHA usa "X-Api-Key" conforme doc
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-Api-Key", apiKey);
        }
        return headers;
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null) return "";
        String b = raw.trim();

        // remove trailing slash
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);

        if (b.startsWith("http://") || b.startsWith("https://")) return b;

        // default para internal
        return "http://" + b;
    }

    private String formatNumber(String number) {
        String clean = number == null ? "" : number.replaceAll("\\D", "");
        return clean.startsWith("55") ? clean : "55" + clean;
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String safeBody(ResponseEntity<String> resp) {
        return resp == null ? "" : (resp.getBody() == null ? "" : resp.getBody());
    }
}
