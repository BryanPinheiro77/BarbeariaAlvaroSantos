package com.barbearia.agenda.integration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class WahaClient {

    @Value("${waha.base-url:http://localhost:3000}")
    private String baseUrl;

    @Value("${waha.api-key:}")
    private String apiKey;

    private static final String SESSION = "default";
    private static final int START_RETRIES = 3;
    private static final long START_RETRY_SLEEP_MS = 1000;

    private static final Set<String> HEALTHY_SESSION_STATES = Set.of(
            "WORKING",
            "STARTING",
            "CONNECTED",
            "OPENING"
    );

    private final RestTemplate restTemplate;

    public WahaClient() {
        this.restTemplate = buildRestTemplate();
    }

    @PostConstruct
    public void init() {
        String normalized = normalizeBaseUrl(baseUrl);
        System.out.println("WAHA: Cliente configurado em: " + normalized + " | session=" + SESSION);

        try {
            ensureSessionReady();
        } catch (Exception e) {
            System.err.println("WAHA: Não foi possível validar/iniciar sessão no startup: " + e.getMessage());
        }
    }

    public boolean sendText(String phoneNumber, String message) {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        String url = normalizedBase + "/api/sendText";
        String formattedNumber = formatNumber(phoneNumber);

        Map<String, Object> body = Map.of(
                "session", SESSION,
                "chatId", formattedNumber + "@c.us",
                "text", message
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, buildHeaders());

        ensureSessionReady();

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("WAHA: Mensagem enviada para: " + phoneNumber);
                return true;
            }

            String responseBody = safeBody(response);

            System.err.println("WAHA: Falha ao enviar. status=" + response.getStatusCode()
                    + " body=" + responseBody);

            if (shouldRetryAfterSendFailure(response.getStatusCode().value(), responseBody)) {
                System.err.println("WAHA: Tentando recuperar sessão e reenviar 1x...");
                recoverSession();
                return retrySendOnce(url, request, phoneNumber);
            }

            return false;

        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();

            System.err.println("WAHA: Erro HTTP ao enviar. status=" + status + " body=" + responseBody);

            if (shouldRetryAfterSendFailure(status, responseBody)) {
                System.err.println("WAHA: Tentando recuperar sessão e reenviar 1x...");
                recoverSession();
                return retrySendOnce(url, request, phoneNumber);
            }

            return false;

        } catch (ResourceAccessException e) {
            System.err.println("WAHA: Timeout/erro de conexão ao enviar mensagem: " + e.getMessage());
            return false;

        } catch (RestClientException e) {
            System.err.println("WAHA: Erro de rede ao enviar mensagem: " + e.getMessage());
            return false;
        }
    }

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

        } catch (HttpStatusCodeException e) {
            System.err.println("WAHA: getSessionStatus HTTP=" + e.getStatusCode().value()
                    + " body=" + e.getResponseBodyAsString());
            return null;

        } catch (ResourceAccessException e) {
            System.err.println("WAHA: getSessionStatus timeout/conexão: " + e.getMessage());
            return null;

        } catch (RestClientException e) {
            System.err.println("WAHA: Não foi possível obter status da sessão. " + e.getMessage());
            return null;
        }
    }

    public void ensureSessionReady() {
        String status = normalizeStatus(getSessionStatus());

        if (isHealthyStatus(status)) {
            return;
        }

        recoverSession();
    }

    private void recoverSession() {
        forceStartSessionWithRetry();
    }

    private void forceStartSessionWithRetry() {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        String url = normalizedBase + "/api/sessions/" + SESSION + "/start";

        for (int i = 1; i <= START_RETRIES; i++) {
            String currentStatus = normalizeStatus(getSessionStatus());

            if (isHealthyStatus(currentStatus)) {
                System.out.println("WAHA: Sessão já saudável antes do /start. status=" + currentStatus);
                return;
            }

            try {
                ResponseEntity<String> resp = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        new HttpEntity<>(buildHeaders()),
                        String.class
                );

                if (resp.getStatusCode().is2xxSuccessful()) {
                    String afterStatus = normalizeStatus(getSessionStatus());
                    System.out.println("WAHA: startSession attempt=" + i + " status=" + afterStatus);

                    if (isHealthyStatus(afterStatus)) {
                        return;
                    }
                } else {
                    String body = safeBody(resp);

                    if (isAlreadyStarted(resp.getStatusCode().value(), body)) {
                        System.out.println("WAHA: Sessão já estava iniciada. Prosseguindo normalmente.");
                        return;
                    }

                    System.err.println("WAHA: startSession attempt=" + i
                            + " HTTP=" + resp.getStatusCode().value()
                            + " body=" + body);
                }

            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                String body = e.getResponseBodyAsString();

                if (isAlreadyStarted(status, body)) {
                    System.out.println("WAHA: Sessão já estava iniciada. Prosseguindo normalmente.");
                    return;
                }

                System.err.println("WAHA: startSession attempt=" + i
                        + " erro HTTP=" + status
                        + " body=" + body);

            } catch (ResourceAccessException e) {
                System.err.println("WAHA: startSession attempt=" + i
                        + " timeout/conexão=" + e.getMessage());

            } catch (RestClientException e) {
                System.err.println("WAHA: startSession attempt=" + i
                        + " erro=" + e.getMessage());
            }

            sleepSilently(START_RETRY_SLEEP_MS);
        }

        System.err.println("WAHA: Não conseguiu colocar a sessão em estado saudável após "
                + START_RETRIES + " tentativas.");
    }

    private boolean retrySendOnce(String url, HttpEntity<Map<String, Object>> request, String phoneNumber) {
        try {
            ResponseEntity<String> retry = restTemplate.postForEntity(url, request, String.class);

            if (retry.getStatusCode().is2xxSuccessful()) {
                System.out.println("WAHA: Mensagem enviada (após recuperação) para: " + phoneNumber);
                return true;
            }

            System.err.println("WAHA: Falha no reenvio. status=" + retry.getStatusCode()
                    + " body=" + safeBody(retry));
            return false;

        } catch (HttpStatusCodeException e) {
            System.err.println("WAHA: Erro HTTP no reenvio. status=" + e.getStatusCode().value()
                    + " body=" + e.getResponseBodyAsString());
            return false;

        } catch (ResourceAccessException e) {
            System.err.println("WAHA: Timeout/erro de conexão no reenvio: " + e.getMessage());
            return false;

        } catch (RestClientException e) {
            System.err.println("WAHA: Erro de rede no reenvio: " + e.getMessage());
            return false;
        }
    }

    private boolean shouldRetryAfterSendFailure(int statusCode, String body) {
        if (isAlreadyStarted(statusCode, body)) {
            return false;
        }

        if (statusCode == 422 && body != null) {
            String b = body.toLowerCase(Locale.ROOT);

            return b.contains("stopped")
                    || b.contains("not started")
                    || b.contains("disconnected");
        }

        return statusCode == 503 || statusCode == 502 || statusCode == 504;
    }

    private boolean isAlreadyStarted(int statusCode, String body) {
        if (statusCode != 422 || body == null) {
            return false;
        }

        return body.toLowerCase(Locale.ROOT).contains("already started");
    }

    private boolean isHealthyStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }

        return HEALTHY_SESSION_STATES.contains(status.toUpperCase(Locale.ROOT));
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }

        return status.trim().toUpperCase(Locale.ROOT);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-Api-Key", apiKey);
        }

        return headers;
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }

        String b = raw.trim();

        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }

        if (b.startsWith("http://") || b.startsWith("https://")) {
            return b;
        }

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
