package com.mfa.stepup.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfa.stepup.domain.exception.InvalidIdempotencyKeyException;
import com.mfa.stepup.domain.model.IdempotencyKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;

/** Header guard only; durable replay/ownership remains in the use case for every caller. */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    public IdempotencyFilter(ObjectMapper mapper) { this.mapper=mapper; }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !(request.getContextPath()+"/api/v1/operation-authorizations").equals(request.getRequestURI());
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        try { IdempotencyKey.from(request.getHeader("Idempotency-Key")); }
        catch (InvalidIdempotencyKeyException ex) {
            var problem=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,ex.getMessage());
            problem.setTitle("Invalid Idempotency Key");
            problem.setType(URI.create("https://api.example.invalid/errors/invalid-idempotency-key"));
            response.setStatus(400); response.setContentType("application/problem+json");
            mapper.writeValue(response.getOutputStream(),problem); return;
        }
        chain.doFilter(request,response);
    }
}
