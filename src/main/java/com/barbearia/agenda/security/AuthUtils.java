package com.barbearia.agenda.security;

import com.barbearia.agenda.model.Admin;
import com.barbearia.agenda.model.Cliente;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtils {

    private AuthUtils() {}

    private static Authentication auth() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) {
            throw new RuntimeException("Usuário não autenticado");
        }
        return a;
    }

    /**
     * Alias para compatibilidade com código que chama getAuthenticatedEmail().
     * No teu JwtAuthFilter, principal = email, então getName() devolve o email.
     */
    public static String getAuthenticatedEmail() {
        return auth().getName();
    }

    /**
     * Preferido (mais curto). Mantido para não quebrar chamadas existentes.
     */
    public static String email() {
        return auth().getName(); // no teu filter, principal = email
    }

    public static boolean isAdmin() {
        return auth().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Devolve o id do usuário logado (Cliente/Admin) a partir do details.
     * Requer que teu JwtAuthFilter faça auth.setDetails(cliente/admin).
     */
    public static Long userId() {
        Object details = auth().getDetails();

        if (details instanceof Cliente c) return c.getId();
        if (details instanceof Admin a) return a.getId();

        throw new RuntimeException("Detalhes do usuário não encontrados no SecurityContext (details).");
    }

    public static Cliente clienteDetails() {
        Object details = auth().getDetails();
        if (details instanceof Cliente c) return c;
        throw new RuntimeException("Usuário logado não é CLIENTE.");
    }

    public static Admin adminDetails() {
        Object details = auth().getDetails();
        if (details instanceof Admin a) return a;
        throw new RuntimeException("Usuário logado não é ADMIN.");
    }
}
