package com.pthanureddy.scheduling;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class Errors {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail domain(ResponseStatusException ex) { return ProblemDetail.forStatusAndDetail(ex.getStatusCode(),ex.getReason()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid scheduling request"); }
}
