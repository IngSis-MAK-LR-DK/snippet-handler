package edu.austral.ingsis.snippethandler.revalidation;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Revalidation of snippets after a new version of a language becomes available (ítem 4). */
@RestController
@RequestMapping("/languages")
public class RevalidationController {

    private final RevalidationService revalidations;

    public RevalidationController(RevalidationService revalidations) {
        this.revalidations = revalidations;
    }

    @PostMapping("/{language}/versions/{version}/revalidate")
    public ResponseEntity<RevalidationRunView> start(
            @PathVariable String language, @PathVariable String version) {
        RevalidationRunView run = revalidations.start(language, version);
        return ResponseEntity.accepted()
                .location(URI.create("/languages/revalidations/" + run.id()))
                .body(run);
    }

    @GetMapping("/revalidations/{id}")
    public RevalidationRunView find(@PathVariable UUID id) {
        return revalidations.find(id);
    }
}
