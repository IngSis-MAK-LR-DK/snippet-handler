package edu.austral.ingsis.snippethandler.users;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Users a snippet can be shared with (US 7). */
@RestController
@RequestMapping("/users")
public class UserController {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserDirectory users;

    public UserController(UserDirectory users) {
        this.users = users;
    }

    /** The caller is never offered as a share target. */
    @GetMapping
    public List<UserView> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return users.search(query, Math.max(page, 0), boundedSize).stream()
                .filter(user -> !user.id().equals(jwt.getSubject()))
                .toList();
    }
}
