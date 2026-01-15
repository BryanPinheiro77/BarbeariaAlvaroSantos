package com.barbearia.agenda.security;

import com.barbearia.agenda.model.Admin;
import com.barbearia.agenda.model.Cliente;
import com.barbearia.agenda.repository.AdminRepository;
import com.barbearia.agenda.repository.ClienteRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminRepository adminRepo;
    private final ClienteRepository clienteRepo;

    public JwtAuthFilter(JwtService jwtService, AdminRepository adminRepo, ClienteRepository clienteRepo) {
        this.jwtService = jwtService;
        this.adminRepo = adminRepo;
        this.clienteRepo = clienteRepo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.startsWith("/auth/")
                || path.startsWith("/servicos/ativos")
                || path.startsWith("/clientes/registrar")
                || path.startsWith("/admin/registrar")
                || path.startsWith("/pagamentos/webhook")
                || path.startsWith("/pagamentos/criar")
                || path.startsWith("/agendamentos/horarios-disponiveis");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            String email = jwtService.extrairEmail(token);
            String tipo = jwtService.extrairTipo(token); // CLIENTE / ADMIN

            if (email == null || email.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

            if ("ADMIN".equalsIgnoreCase(tipo)) {
                Admin admin = adminRepo.findByEmail(email);
                if (admin != null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            email, // <-- PRINCIPAL AGORA É O EMAIL
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    );
                    // opcional: guardar o objeto completo aqui
                    auth.setDetails(admin);

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } else {
                Cliente cliente = clienteRepo.findByEmail(email).orElse(null);
                if (cliente != null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            email, // <-- PRINCIPAL AGORA É O EMAIL
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
                    );
                    auth.setDetails(cliente);

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

}
