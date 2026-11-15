package io.github.sebastianbadillo.linkly.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns our exceptions into ProblemDetail JSON (RFC 9457), e.g.
 * {"status": 409, "title": "Conflict", "detail": "Alias 'bf26' is already taken"}.
 * Controllers stay clean: they just let the exception fly, and this class picks the status code.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({InvalidUrlException.class, InvalidAliasException.class})
    ProblemDetail handleBadInput(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AliasTakenException.class)
    ProblemDetail handleAliasTaken(AliasTakenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(LinkNotFoundException.class)
    ProblemDetail handleNotFound(LinkNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CodeGenerationException.class)
    ProblemDetail handleCodeGeneration(CodeGenerationException e) {
        // Generic message on purpose: internal details stay in our logs, not in the response.
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not create a short link right now, please try again");
    }
}
