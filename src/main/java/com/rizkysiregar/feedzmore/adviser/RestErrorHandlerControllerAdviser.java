package com.rizkysiregar.feedzmore.adviser;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.response.BaseResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@AllArgsConstructor
public class RestErrorHandlerControllerAdviser implements ErrorController {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<?> exception(Exception e) {
        // Spring MVC's own client errors (wrong method, unsupported media type, missing parameter, unknown path...)
        // carry their status: they are the caller's mistake, not a server failure
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            log.warn("{} {}", e.getClass().getSimpleName(), e.getMessage());
            return new ResponseEntity<>(BaseResponse.builder()
                    .code(String.valueOf(status.value()))
                    .message(clientErrorMessage(status))
                    .build(), status);
        }
        log.error("Exception = ", e);
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.INTERNAL_ERROR.getCode())
                .message(GlobalMessage.INTERNAL_ERROR.getMessage())
                .build(),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static String clientErrorMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> GlobalMessage.BAD_REQUEST.getMessage();
            case NOT_FOUND -> GlobalMessage.NOT_FOUND.getMessage();
            default -> status.getReasonPhrase();
        };
    }

    // services throw this when a row they were asked for does not exist
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> entityNotFound(EntityNotFoundException e) {
        log.warn("EntityNotFoundException: {}", e.getMessage());
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.NOT_FOUND.getCode())
                .message(GlobalMessage.NOT_FOUND.getMessage())
                .build(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> uploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("Upload rejected, too large");
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE.getCode())
                .message(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE.getMessage())
                .build(), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadableRequestBody(HttpMessageNotReadableException e) {
        log.warn("Unable to read request body: {}", e.getClass().getSimpleName());
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.BAD_REQUEST.getCode())
                .message(GlobalMessage.BAD_REQUEST.getMessage())
                .build(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<?> businessException(BusinessException e) {
        // 4xx is an expected outcome (validation, not found, forbidden): one line, no stack trace
        if (e.getHttpStatus() != null && e.getHttpStatus().is5xxServerError()) {
            log.error("BusinessException = ", e);
        } else {
            log.warn("BusinessException {} {}: {}", e.getHttpStatus(), e.getCode(), e.getMessage());
        }
        return new ResponseEntity<>(
                BaseResponse.builder()
                        .code(e.getCode())
                        .message(e.getMessage())
                        .build(),
                e.getHttpStatus()
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(value = HttpStatus.UNAUTHORIZED)
    public ResponseEntity<?> badCredentialsException(BadCredentialsException e) {
        log.warn("BadCredentialsException = ", e);
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.UNAUTHORIZED.getCode())
                .message(GlobalMessage.BAD_CREDENTIALS.getMessage())
                .build(),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(value = HttpStatus.UNAUTHORIZED)
    public ResponseEntity<?> disabledException(DisabledException e) {
        log.warn("DisabledException = {}", e.getMessage());
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.USER_DISABLED.getCode())
                .message(GlobalMessage.USER_DISABLED.getMessage())
                .build(),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AuthorizationDeniedException ex) {
        log.warn("AuthorizationDeniedException Adviser Handler");
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.ACCESS_DENIED.getCode())
                .message(GlobalMessage.ACCESS_DENIED.getMessage())
                .build(),
                HttpStatus.FORBIDDEN
        );
    }

    // thrown instead of MethodArgumentNotValidException when a controller method also has constraints on its
    // own parameters (e.g. @PathVariable @NotBlank next to @Valid @RequestBody)
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> handlerMethodValidationException(HandlerMethodValidationException ex) {
        String errorMessage = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    if (result instanceof ParameterErrors parameterErrors) {
                        return parameterErrors.getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage());
                    }
                    return result.getResolvableErrors().stream().map(MessageSourceResolvable::getDefaultMessage);
                })
                .collect(Collectors.joining("; "));

        log.warn("Method validation failed: {}", errorMessage);

        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.BAD_REQUEST.getCode())
                .message(errorMessage.isBlank() ? GlobalMessage.BAD_REQUEST.getMessage() : errorMessage)
                .build(),
                HttpStatus.BAD_REQUEST);
    }

    // a request parameter that cannot be read as its type, e.g. from=abc for a date or an id that is no UUID
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> methodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("Invalid value for parameter {}", ex.getName());
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.BAD_REQUEST.getCode())
                .message("Invalid value for parameter '" + ex.getName() + "'")
                .build(),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> methodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", errorMessage);

        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.BAD_REQUEST.getCode())
                .message(errorMessage)
                .build(),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(value = HttpStatus.UNAUTHORIZED)
    public ResponseEntity<?> usernameNotFoundException(UsernameNotFoundException unfe) {
        log.warn("Method Arguments not Valid Exception = ", unfe);
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.BAD_CREDENTIALS.getCode())
                .message(GlobalMessage.BAD_CREDENTIALS.getMessage())
                .build(),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("## handleConstraintViolationException = ", ex);
//        Map<String, String> errors = new HashMap<>();
//        ex.getConstraintViolations().forEach(cv -> {
//            String field = cv.getPropertyPath().iterator().next().getName();
//            errors.put(field, cv.getMessage());
//        });
//        return errors;

        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.BAD_REQUEST.getCode())
                .message(GlobalMessage.BAD_REQUEST.getMessage())
                .build(),
                HttpStatus.BAD_REQUEST);
    }

}
