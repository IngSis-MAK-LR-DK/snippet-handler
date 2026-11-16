package edu.austral.ingsis.snippethandler.execution;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.austral.ingsis.snippethandler.error.ForbiddenException;
import edu.austral.ingsis.snippethandler.error.NotFoundException;
import edu.austral.ingsis.snippethandler.permission.PermissionDeniedException;
import edu.austral.ingsis.snippethandler.permission.Role;
import edu.austral.ingsis.snippethandler.snippet.SnippetAccess;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Checks, while the WebSocket opens, that the user can read the snippet: the same rule as running a
 * test over HTTP. It runs inside the HTTP request, with the token already validated; the messages
 * that come later have no request around them, so the snippet and the token are kept in the
 * session.
 */
public class SnippetRunHandshake implements HandshakeInterceptor {

    static final String SNIPPET = "snippet";
    static final String TOKEN = "token";
    private static final Pattern PATH = Pattern.compile("/snippets/([^/]+)/run$");

    private final SnippetAccess access;

    public SnippetRunHandshake(SnippetAccess access) {
        this.access = access;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return refuse(response, HttpStatus.UNAUTHORIZED);
        }
        UUID snippetId = snippetIdOf(request.getURI().getPath());
        if (snippetId == null) {
            return refuse(response, HttpStatus.NOT_FOUND);
        }
        try {
            attributes.put(SNIPPET, access.require(snippetId, Role.READER));
        } catch (ForbiddenException | PermissionDeniedException e) {
            return refuse(response, HttpStatus.FORBIDDEN);
        } catch (NotFoundException e) {
            return refuse(response, HttpStatus.NOT_FOUND);
        }
        attributes.put(TOKEN, jwt.getToken().getTokenValue());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Exception exception) {
        // Nothing to do once it is open.
    }

    private static UUID snippetIdOf(String path) {
        Matcher matcher = PATH.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean refuse(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return false;
    }
}
