package edu.austral.ingsis.snippethandler.error;

import edu.austral.ingsis.snippethandler.asset.AssetStoreException;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.UnsupportedLanguageException;
import edu.austral.ingsis.snippethandler.permission.PermissionDeniedException;
import edu.austral.ingsis.snippethandler.permission.PermissionServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps every error to an RFC 7807 problem detail. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidSnippetException.class)
    public ProblemDetail invalidSnippet(InvalidSnippetException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, e);
        problem.setProperty("errors", e.getErrors());
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler({ForbiddenException.class, PermissionDeniedException.class})
    public ProblemDetail forbidden(RuntimeException e) {
        return problem(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler({
        BadRequestException.class,
        UnsupportedLanguageException.class,
        PropertyReferenceException.class
    })
    public ProblemDetail badRequest(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler({
        LanguageServiceException.class,
        AssetStoreException.class,
        PermissionServiceException.class
    })
    public ProblemDetail downstreamFailure(RuntimeException e) {
        LOG.error("Downstream service failure", e);
        return problem(HttpStatus.BAD_GATEWAY, e);
    }

    private static ProblemDetail problem(HttpStatus status, RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }
}
