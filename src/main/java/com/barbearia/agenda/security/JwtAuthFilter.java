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

    public JwtAuthFilter(
            JwtService jwtService,
            AdminRepository adminRepo,
            ClienteRepository clienteRepo
    ) {
        this.jwtService = jwtService;
        this.adminRepo = adminRepo;
        this.clienteRepo = clienteRepo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/auth/")
                || path.startsWith("/servicos/ativos")
                || path.startsWith("/clientes/registrar");
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

            // ================= ADMIN =================
            Admin admin = adminRepo.findByEmail(email);
            if (admin != null) {
                var auth = new UsernamePasswordAuthenticationToken(
                        admin, null, List.of()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                filterChain.doFilter(request, response);
                return;
            }

            // ================= CLIENTE =================
            Cliente cliente = clienteRepo.findByEmail(email).orElse(null);
            if (cliente != null) {
                var auth = new UsernamePasswordAuthenticationToken(
                        cliente, null, List.of()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                filterChain.doFilter(request, response);
                return;
            }

            // token válido mas usuário não encontrado
            SecurityContextHolder.clearContext();

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}

