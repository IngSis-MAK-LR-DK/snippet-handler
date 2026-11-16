package edu.austral.ingsis.snippethandler.observability;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Agrega al contexto de logs quien hace el pedido: el {@code sub} del JWT (por ejemplo {@code
 * auth0|abc123}). Es un identificador, no un dato personal: nunca se loguea el mail ni el token.
 *
 * <p>Se registra en la cadena de Spring Security, justo despues de la autenticacion: antes de eso
 * todavia no se sabe quien es.
 */
public class UserMdcFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "userId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = currentUserId();
        if (userId != null) {
            MDC.put(MDC_KEY, userId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken().getSubject();
        }
        return null;
    }
}
