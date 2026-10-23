package com.example.discord.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Covers servlet failures dispatched to /error rather than a controller exception. */
@Hidden
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request, HttpServletResponse response) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer number && number >= 400 && number <= 599 ? number : 500;
        HttpStatusCode statusCode = HttpStatusCode.valueOf(status);
        return ResponseEntity.status(statusCode).contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ApiProblems.create(statusCode, ApiProblems.code(statusCode), ApiProblems.detail(statusCode), request, response));
    }
}
