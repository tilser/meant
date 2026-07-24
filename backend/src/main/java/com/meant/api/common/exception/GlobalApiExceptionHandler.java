package com.meant.api.common.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.discount.exception.DiscountCodeException;
import com.meant.api.module.checkout.exception.EmbeddedCheckoutException;
import com.meant.api.module.checkout.exception.CheckoutAttributionException;
import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.exception.MerchantEmbeddingException;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.exception.MerchantIdentityLinkException;
import com.meant.api.module.merchant.exception.MerchantMcpToolException;
import com.meant.api.module.merchant.exception.MerchantProductDetailsException;
import com.meant.api.module.location.exception.InvalidLocationException;
import com.meant.api.module.location.exception.LocationSearchException;
import com.meant.api.module.order.exception.OrderException;
import com.meant.api.module.review.exception.ReviewException;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.exception.UnsupportedProductSearchCurrencyException;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.exception.UserProductSearchException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final String VALIDATION_DETAIL = "Request validation failed.";
    private static final String BAD_REQUEST_DETAIL = "The request could not be processed.";
    private static final String FORBIDDEN_DETAIL = "You are not allowed to access this resource.";
    private static final String NOT_FOUND_DETAIL = "The requested resource was not found.";
    private static final String UPSTREAM_DETAIL = "Upstream service is temporarily unavailable.";
    private static final String INTERNAL_DETAIL = "An unexpected error occurred.";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalApiExceptionHandler::toValidationError)
                .toList();
        return problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                VALIDATION_DETAIL,
                request,
                exception,
                errors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<Map<String, String>> errors = exception.getConstraintViolations().stream()
                .map(violation -> validationError(pathName(violation.getPropertyPath()), violation.getMessage()))
                .toList();
        return problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                VALIDATION_DETAIL,
                request,
                exception,
                errors
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                VALIDATION_DETAIL,
                request,
                exception,
                methodValidationErrors(exception)
        );
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> handleBadRequest(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                BAD_REQUEST_DETAIL,
                request,
                exception
        );
    }

    @ExceptionHandler({
            OpenRouterException.class,
            MerchantCatalogSearchException.class,
            MerchantEmbeddingException.class,
            MerchantEnrichmentException.class,
            MerchantMcpToolException.class,
            MerchantProductDetailsException.class,
            LocationSearchException.class,
            ReviewException.class,
            UserProductSearchException.class
    })
    ResponseEntity<ProblemDetail> handleIntegrationException(RuntimeException exception, HttpServletRequest request) {
        if (exception instanceof ApiException apiException) {
            return handleApiException(apiException, exception, request);
        }
        return problem(
                HttpStatus.BAD_GATEWAY,
                ApiErrorCode.UPSTREAM_SERVICE_ERROR,
                UPSTREAM_DETAIL,
                request,
                exception
        );
    }

    @ExceptionHandler({
            AgentException.class,
            CartException.class,
            CheckoutAttributionException.class,
            EmbeddedCheckoutException.class,
            DiscountCodeException.class,
            MerchantIdentityLinkException.class,
            InvalidLocationException.class,
            OrderException.class,
            SelectedOfferResolutionException.class,
            UnsupportedProductSearchCurrencyException.class,
            UserException.class
    })
    ResponseEntity<ProblemDetail> handleBusinessException(RuntimeException exception, HttpServletRequest request) {
        if (exception instanceof ApiException apiException) {
            return handleApiException(apiException, exception, request);
        }
        return problem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                BAD_REQUEST_DETAIL,
                request,
                exception
        );
    }

    private ResponseEntity<ProblemDetail> handleApiException(
            ApiException apiException,
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return problem(
                apiException.getStatus(),
                apiException.getErrorCode(),
                apiException.getSafeMessage(),
                request,
                exception
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.AUTHENTICATION_REQUIRED,
                "Authentication is required.",
                request,
                exception
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.FORBIDDEN,
                FORBIDDEN_DETAIL,
                request,
                exception
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatusCode statusCode = exception.getStatusCode();
        ApiErrorCode code = codeForStatus(statusCode);
        return problem(
                statusCode,
                code,
                safeDetail(statusCode),
                request,
                exception
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnhandledException(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                INTERNAL_DETAIL,
                request,
                exception
        );
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatusCode status,
            ApiErrorCode code,
            String detail,
            HttpServletRequest request,
            Exception exception
    ) {
        return problem(status, code, detail, request, exception, List.of());
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatusCode status,
            ApiErrorCode code,
            String detail,
            HttpServletRequest request,
            Exception exception,
            List<Map<String, String>> validationErrors
    ) {
        String traceId = traceId();
        logException(status, traceId, exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title(status));
        problem.setType(URI.create("https://api.meant.com/problems/" + code.getValue()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", code.getValue());
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("traceId", traceId);
        if (!validationErrors.isEmpty()) {
            problem.setProperty("errors", validationErrors);
        }

        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(problem);
    }

    private static Map<String, String> toValidationError(FieldError error) {
        return validationError(error.getField(), error.getDefaultMessage());
    }

    private static Map<String, String> validationError(String field, String message) {
        return Map.of("field", safeField(field), "message", safeMessage(message));
    }

    private static String safeField(String field) {
        return field == null || field.isBlank() ? "unknown" : field;
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "Invalid value." : message;
    }

    private static List<Map<String, String>> methodValidationErrors(HandlerMethodValidationException exception) {
        return exception.getParameterValidationResults().stream()
                .flatMap(result -> validationErrors(result).stream())
                .toList();
    }

    private static List<Map<String, String>> validationErrors(ParameterValidationResult result) {
        String parameterName = result.getMethodParameter().getParameterName();
        if (result instanceof ParameterErrors parameterErrors) {
            return parameterErrors.getFieldErrors().stream()
                    .map(GlobalApiExceptionHandler::toValidationError)
                    .toList();
        }
        return result.getResolvableErrors().stream()
                .map(error -> validationError(parameterName, message(error)))
                .toList();
    }

    private static String message(MessageSourceResolvable error) {
        return error.getDefaultMessage();
    }

    private static String pathName(Path path) {
        if (path == null) {
            return null;
        }
        String name = null;
        for (Path.Node node : path) {
            if (node.getName() != null && !node.getName().isBlank()) {
                name = node.getName();
            }
        }
        return name;
    }

    private void logException(HttpStatusCode status, String traceId, Exception exception) {
        if (status.is5xxServerError()) {
            log.error("API error traceId={}", traceId, exception);
            return;
        }
        log.warn("API request rejected traceId={} status={} exception={} message={}", traceId, status.value(),
                exception.getClass().getName(), exception.getMessage());
    }

    private static String traceId() {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private static ApiErrorCode codeForStatus(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ApiErrorCode.NOT_FOUND;
        }
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            return ApiErrorCode.AUTHENTICATION_REQUIRED;
        }
        if (status.value() == HttpStatus.FORBIDDEN.value()) {
            return ApiErrorCode.FORBIDDEN;
        }
        if (status.is4xxClientError()) {
            return ApiErrorCode.BAD_REQUEST;
        }
        if (status.is5xxServerError()) {
            return ApiErrorCode.INTERNAL_ERROR;
        }
        return ApiErrorCode.BAD_REQUEST;
    }

    private static String safeDetail(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return NOT_FOUND_DETAIL;
        }
        if (status.value() == HttpStatus.FORBIDDEN.value()) {
            return FORBIDDEN_DETAIL;
        }
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            return "Authentication is required.";
        }
        if (status.is4xxClientError()) {
            return BAD_REQUEST_DETAIL;
        }
        return INTERNAL_DETAIL;
    }

    private static String title(HttpStatusCode status) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        return httpStatus == null ? "HTTP " + status.value() : httpStatus.getReasonPhrase();
    }

}
