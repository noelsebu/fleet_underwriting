package com.insurance.underwriting.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation failures on REST endpoints (@Valid on @RequestBody).
     * Returns a JSON 400 response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Catches unhandled exceptions from MVC (Thymeleaf) controllers and renders
     * the error.html template with contextual information.
     */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleAll(Exception ex, HttpServletRequest request) {
        HttpStatus status = resolveStatus(ex);

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status",    status.value());
        mav.addObject("error",     status.getReasonPhrase());
        mav.addObject("message",   ex.getMessage());
        mav.addObject("path",      request.getRequestURI());
        mav.addObject("timestamp", java.time.LocalDateTime.now());
        mav.setStatus(status);
        return mav;
    }

    private HttpStatus resolveStatus(Exception ex) {
        if (ex instanceof IllegalArgumentException) return HttpStatus.BAD_REQUEST;
        if (ex instanceof org.springframework.security.access.AccessDeniedException) return HttpStatus.FORBIDDEN;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
