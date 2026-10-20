package edu.austral.ingsis.snippethandler.testcase;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/snippets/{snippetId}/tests")
public class TestCaseController {

    private final TestCaseService tests;

    public TestCaseController(TestCaseService tests) {
        this.tests = tests;
    }

    @GetMapping
    public List<TestCaseView> list(@PathVariable UUID snippetId) {
        return tests.list(snippetId);
    }

    @PostMapping
    public ResponseEntity<TestCaseView> create(
            @PathVariable UUID snippetId, @Valid @RequestBody TestCaseRequest request) {
        TestCaseView test = tests.create(snippetId, request);
        return ResponseEntity.created(
                        URI.create("/snippets/%s/tests/%s".formatted(snippetId, test.id())))
                .body(test);
    }

    @PutMapping("/{testId}")
    public TestCaseView update(
            @PathVariable UUID snippetId,
            @PathVariable UUID testId,
            @Valid @RequestBody TestCaseRequest request) {
        return tests.update(snippetId, testId, request);
    }

    @DeleteMapping("/{testId}")
    public ResponseEntity<Void> delete(@PathVariable UUID snippetId, @PathVariable UUID testId) {
        tests.delete(snippetId, testId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{testId}/run")
    public TestRunResult run(@PathVariable UUID snippetId, @PathVariable UUID testId) {
        return tests.run(snippetId, testId);
    }
}
