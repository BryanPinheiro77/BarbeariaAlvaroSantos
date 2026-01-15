package com.barbearia.agenda.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey CHAVE;
    private final long EXPIRATION;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {
        this.CHAVE = Keys.hmacShaKeyFor(secret.getBytes());
        this.EXPIRATION = expiration;
    }

    // Mantém compatibilidade (se alguém ainda chamar com 1 parâmetro)
    public String gerarToken(String email) {
        return gerarToken(email, null);
    }

    // Novo: salva o tipo no token (ADMIN / CLIENTE)
    public String gerarToken(String email, String tipo) {
        Map<String, Object> claims = new HashMap<>();
        if (tipo != null && !tipo.isBlank()) {
            claims.put("tipo", tipo.toUpperCase());
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(CHAVE, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extrairEmail(String token) {
        return getClaims(token).getSubject();
    }

    public String extrairTipo(String token) {
        Object v = getClaims(token).get("tipo");
        return v == null ? null : String.valueOf(v);
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(CHAVE)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
