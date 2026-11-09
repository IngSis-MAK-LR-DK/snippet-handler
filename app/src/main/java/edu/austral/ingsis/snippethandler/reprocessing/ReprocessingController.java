package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Follow the background lint (US 15) and format (US 12) that a change of rules starts. */
@RestController
@RequestMapping("/rules/reprocessing")
public class ReprocessingController {

    private final ReprocessingService reprocessing;

    public ReprocessingController(ReprocessingService reprocessing) {
        this.reprocessing = reprocessing;
    }

    @GetMapping
    public List<ReprocessingView> list(@AuthenticationPrincipal Jwt jwt) {
        return reprocessing.list(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public ReprocessingView find(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reprocessing.find(jwt.getSubject(), id);
    }
}
