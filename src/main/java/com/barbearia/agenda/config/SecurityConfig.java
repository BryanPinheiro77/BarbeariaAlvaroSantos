package com.barbearia.agenda.config;

import com.barbearia.agenda.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // =========================
                        // PÚBLICAS
                        // =========================
                        .requestMatchers(
                                "/auth/login",
                                "/clientes/registrar"
                        ).permitAll()

                        // serviços para cliente (somente ativos)
                        .requestMatchers(HttpMethod.GET, "/servicos/ativos").permitAll()

                        // webhook do Mercado Pago tem que ser público
                        .requestMatchers(
                                "/pagamentos/webhook",
                                "/pagamentos/webhook/**"
                        ).permitAll()

                        // horários disponíveis (se você quer permitir sem login)
                        // se preferir exigir login do cliente, troque pra hasRole("CLIENTE")
                        .requestMatchers("/agendamentos/horarios-disponiveis").permitAll()

                        // =========================
                        // ADMIN
                        // =========================
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // CRUD de serviços só admin
                        .requestMatchers("/servicos/**").hasRole("ADMIN")

                        /// PAGAMENTOS (CLIENTE)
                        .requestMatchers(HttpMethod.POST, "/pagamentos/criar").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/pagamentos/*").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/pagamentos/agendamentos/**").hasRole("CLIENTE")

                        // PAGAMENTOS (ADMIN)
                        .requestMatchers(HttpMethod.GET, "/pagamentos").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/pagamentos/*/confirmar-manual").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/pagamentos/*/cancelar").hasRole("ADMIN")
                        .requestMatchers("/pagamentos/mock/**").hasRole("ADMIN")


                        // =========================
                        // CLIENTE (AGENDAMENTOS)
                        // =========================
                        .requestMatchers("/agendamentos/**").hasRole("CLIENTE")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
