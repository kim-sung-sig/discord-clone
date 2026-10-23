package com.example.discord.auth;

import com.example.discord.api.ApiProblems;
import com.example.discord.expression.ExpressionNotFoundException;
import com.example.discord.gateway.GatewayForbiddenException;
import com.example.discord.gateway.GatewayResyncRequiredException;
import com.example.discord.gateway.GatewaySessionNotFoundException;
import com.example.discord.invite.InviteDeletedException;
import com.example.discord.invite.InviteExpiredException;
import com.example.discord.invite.InviteMaxUsesExceededException;
import com.example.discord.invite.InviteNotFoundException;
import com.example.discord.message.MessageMutationRejectedException;
import com.example.discord.message.MessageNotFoundException;
import com.example.discord.message.MessagePublishRejectedException;
import com.example.discord.social.SocialNotFoundException;
import com.example.discord.social.SocialPolicyException;
import com.example.discord.storage.AttachmentNotFoundException;
import com.example.discord.thread.ThreadNotFoundException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Kept in auth to share its package-private authentication exceptions without widening the domain API. */
@Hidden
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> failure(Exception exception, HttpServletRequest request, HttpServletResponse response) {
        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = null;
        String detail = null;
        HttpHeaders headers = new HttpHeaders();
        if (exception instanceof ResponseStatusException rejected) {
            status = rejected.getStatusCode();
            headers.addAll(rejected.getHeaders());
            detail = status.is5xxServerError() ? null : rejected.getReason();
        } else if (exception instanceof LoginLockedException) {
            status = HttpStatus.LOCKED;
            code = "LOGIN_LOCKED";
            detail = "login temporarily locked";
        } else if (exception instanceof InvalidCredentialsException) {
            status = HttpStatus.UNAUTHORIZED;
            code = "INVALID_CREDENTIALS";
            detail = "invalid credentials";
        } else if (exception instanceof DuplicateAccountException) {
            status = HttpStatus.CONFLICT;
            code = "DUPLICATE_ACCOUNT";
            detail = "account already exists";
        } else if (exception instanceof InvalidRefreshTokenException) {
            status = HttpStatus.UNAUTHORIZED;
            code = "INVALID_REFRESH_TOKEN";
            detail = "refresh token invalid";
        } else if (exception instanceof RefreshTokenReuseDetectedException) {
            status = HttpStatus.UNAUTHORIZED;
            code = "REFRESH_TOKEN_REUSE_DETECTED";
            detail = "refresh token reuse detected";
        } else if (exception instanceof GatewayResyncRequiredException) {
            status = HttpStatus.CONFLICT;
            code = "RESYNC_REQUIRED";
            detail = exception.getMessage();
        } else if (exception instanceof MessagePublishRejectedException) {
            status = HttpStatus.CONFLICT;
            detail = exception.getMessage();
        } else if (exception instanceof GatewayForbiddenException || exception instanceof MessageMutationRejectedException
                || exception instanceof SocialPolicyException) {
            status = HttpStatus.FORBIDDEN;
            detail = exception.getMessage();
        } else if (exception instanceof InviteDeletedException || exception instanceof InviteExpiredException
                || exception instanceof InviteMaxUsesExceededException) {
            status = HttpStatus.GONE;
            detail = exception.getMessage();
        } else if (exception instanceof ExpressionNotFoundException || exception instanceof GatewaySessionNotFoundException
                || exception instanceof InviteNotFoundException || exception instanceof MessageNotFoundException
                || exception instanceof SocialNotFoundException || exception instanceof AttachmentNotFoundException
                || exception instanceof ThreadNotFoundException) {
            status = HttpStatus.NOT_FOUND;
            detail = exception.getMessage();
        } else if (exception instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
            detail = "invalid request";
        } else if (exception instanceof IllegalStateException) {
            Object mapped = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
            if (mapped instanceof HandlerMethod handler) {
                // Preserve the two legacy state-rejection contracts; unrelated internal failures remain 500.
                String controller = handler.getBeanType().getName();
                if (controller.equals("com.example.discord.experience.StageController")) {
                    status = HttpStatus.BAD_REQUEST;
                } else if (controller.equals("com.example.discord.thread.ThreadController")) {
                    status = HttpStatus.FORBIDDEN;
                }
            }
        }
        ProblemDetail problem = ApiProblems.create(status, code == null ? ApiProblems.code(status) : code,
            detail == null ? ApiProblems.detail(status) : detail, request, response);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest webRequest) {
        ServletWebRequest servlet = (ServletWebRequest) webRequest;
        String detail = status.value() == 400 ? "invalid request" : ApiProblems.detail(status);
        if (exception instanceof ResponseStatusException rejected && !status.is5xxServerError()
                && rejected.getReason() != null) {
            detail = rejected.getReason();
        }
        ProblemDetail problem = ApiProblems.create(status, ApiProblems.code(status),
            detail,
            servlet.getRequest(), servlet.getResponse());
        HttpHeaders copied = new HttpHeaders();
        copied.addAll(headers);
        copied.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.handleExceptionInternal(exception, problem, copied, status, webRequest);
    }
}
