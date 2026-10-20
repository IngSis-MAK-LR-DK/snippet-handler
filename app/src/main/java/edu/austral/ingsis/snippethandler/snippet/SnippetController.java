package edu.austral.ingsis.snippethandler.snippet;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.BadRequestException;
import edu.austral.ingsis.snippethandler.permission.Relation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/snippets")
public class SnippetController {

    private final SnippetService snippets;
    private final SnippetQueries queries;

    public SnippetController(SnippetService snippets, SnippetQueries queries) {
        this.snippets = snippets;
        this.queries = queries;
    }

    /** US 3: snippet written in the editor. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SnippetDetail> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SnippetRequest request) {
        return created(snippets.create(jwt.getSubject(), request));
    }

    /** US 1: snippet uploaded as a file. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SnippetDetail> createFromFile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file,
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) String description,
            @RequestParam @NotBlank String language,
            @RequestParam @NotBlank String version) {
        SnippetRequest request =
                new SnippetRequest(name, description, language, version, read(file));
        return created(snippets.create(jwt.getSubject(), request));
    }

    /** US 4: changes made in the editor. */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SnippetDetail update(
            @PathVariable UUID id, @Valid @RequestBody SnippetUpdateRequest request) {
        return snippets.update(id, request);
    }

    /** US 2: changes uploaded as a file; every field is optional. */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SnippetDetail updateFromFile(
            @PathVariable UUID id,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String version) {
        String content = file == null ? null : read(file);
        return snippets.update(
                id, new SnippetUpdateRequest(name, description, language, version, content));
    }

    /** US 5. Sortable by name, language and compliance ({@code sort=name,desc}). */
    @GetMapping
    public Page<SnippetSummary> list(
            @RequestParam(defaultValue = "ALL") Relation relation,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Compliance compliance,
            @PageableDefault(sort = "name") Pageable pageable) {
        return queries.list(new SnippetFilter(relation, name, language, compliance), pageable);
    }

    @GetMapping("/{id}")
    public SnippetDetail get(@PathVariable UUID id) {
        return queries.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        snippets.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<Void> share(
            @PathVariable UUID id, @Valid @RequestBody ShareRequest request) {
        snippets.share(id, request.userId());
        return ResponseEntity.noContent().build();
    }

    /** US 13. */
    @GetMapping("/{id}/download")
    public ResponseEntity<String> download(
            @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean formatted) {
        SnippetFile file = queries.download(id, formatted);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(file.content());
    }

    @PostMapping("/{id}/format")
    public SnippetDetail format(@PathVariable UUID id) {
        return snippets.format(id);
    }

    private static ResponseEntity<SnippetDetail> created(SnippetDetail snippet) {
        return ResponseEntity.created(URI.create("/snippets/" + snippet.id())).body(snippet);
    }

    private static String read(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BadRequestException("Could not read uploaded file");
        }
    }
}
