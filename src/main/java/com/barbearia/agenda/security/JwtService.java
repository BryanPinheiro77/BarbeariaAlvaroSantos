package com.barbearia.agenda.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

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

    public String gerarToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(CHAVE, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extrairEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(CHAVE)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
