package com.barbearia.agenda.integration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WahaClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${waha.base-url:http://localhost:3000}")
    private String baseUrl;

    @Value("${waha.api-key:}")
    private String apiKey;

    @Value("${waha.session:default}")
    private String session;

    @PostConstruct
    public void init() {
        System.out.println("WAHA: Cliente de integração configurado em: " + baseUrl);
    }

    public void sendText(String phoneNumber, String message) {
        try {
            String url = baseUrl + "/api/sendText";

            // Garante que o número tenha o formato correto (ex: 5511999999999)
            String formattedNumber = formatNumber(phoneNumber);

            Map<String, Object> body = new HashMap<>();
            body.put("chatId", formattedNumber + "@c.us");
            body.put("text", message);
            body.put("session", session);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set("X-Api-Key", apiKey);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Mensagem enviada para: " + phoneNumber);
            } else {
                System.err.println("WAHA retornou status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Erro ao enviar mensagem WAHA: " + e.getMessage());
        }
    }

    private String formatNumber(String number) {
        String clean = number == null ? "" : number.replaceAll("\\D", "");
        return clean.startsWith("55") ? clean : "55" + clean;
    }
}
