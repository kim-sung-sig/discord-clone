package com.example.discord.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Shared by MVC failures, servlet error dispatches and pre-controller rate limiting. */
public final class ApiProblems {
    private ApiProblems() {
    }

    public static ProblemDetail create(HttpStatusCode status, String code, String detail,
                                       HttpServletRequest request, HttpServletResponse response) {
        String requestId = response.getHeader("X-Request-Id");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            response.setHeader("X-Request-Id", requestId);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        String originalPath = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        problem.setInstance(URI.create(originalPath == null ? request.getRequestURI() : originalPath));
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestId);
        return problem;
    }


    public static String code(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 410 -> "GONE";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 423 -> "LOGIN_LOCKED";
            case 429 -> "RATE_LIMITED";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
    }

    public static String detail(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return "An unexpected server error occurred";
        }
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "Request rejected" : known.getReasonPhrase();
    }
}
