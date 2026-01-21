package com.barbearia.agenda.integration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

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


    public boolean sendText(String phoneNumber, String message) {
        String url = baseUrl + "/api/sendText";
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

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("WAHA OK: " + response.getBody());
                return true;
            }

            System.err.println("WAHA status=" + response.getStatusCode());
            System.err.println("WAHA body=" + response.getBody());
            return false;

        } catch (HttpStatusCodeException e) {
            System.err.println("WAHA status=" + e.getStatusCode());
            System.err.println("WAHA body=" + e.getResponseBodyAsString());
            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    private String formatNumber(String number) {
        String clean = number == null ? "" : number.replaceAll("\\D", "");
        return clean.startsWith("55") ? clean : "55" + clean;
    }
}
