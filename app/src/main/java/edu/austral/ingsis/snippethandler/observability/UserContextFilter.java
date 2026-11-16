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
 * Puts who is making the request in the logging context, so every line logged while serving it
 * carries the user without anyone writing it. It must run after the token is validated. Only the
 * {@code sub} of the token goes there: never the token itself, nor the email.
 */
public class UserContextFilter extends OncePerRequestFilter {

    public static final String USER = "user";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            MDC.put(USER, jwt.getToken().getSubject());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(USER);
        }
    }
}
