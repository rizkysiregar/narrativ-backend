package com.rizkysiregar.feedzmore.adviser;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.i18n.LocalizedMessages;
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
            log.warn("HTTP.CLIENT.ERROR type={} message={}", e.getClass().getSimpleName(), e.getMessage());
            return new ResponseEntity<>(clientError(status), status);
        }
        log.error("HTTP.UNHANDLED.EXCEPTION", e);
        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.INTERNAL_ERROR),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static BaseResponse<Void> clientError(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> BaseResponse.error(GlobalMessage.BAD_REQUEST);
            case NOT_FOUND -> BaseResponse.error(GlobalMessage.NOT_FOUND);
            default -> BaseResponse.<Void>builder()
                    .code(String.valueOf(status.value()))
                    .message(status.getReasonPhrase())
                    .build();
        };
    }

    // the field names and the messages (already in the caller's language, from the validation annotations) are all
    // there is to tell, so there is nothing for a client to fill in
    private static BaseResponse<Void> validationError(String message) {
        BaseResponse<Void> body = BaseResponse.error(GlobalMessage.BAD_REQUEST);
        body.setMessage(message);
        return body;
    }

    private static BaseResponse<Void> businessBody(BusinessException e) {
        BaseResponse<Void> body = BaseResponse.<Void>builder()
                .code(e.getCode())
                .message(e.getLocalizedMessage())
                .errorCode(e.getErrorCode())
                .build();
        if (e.getArgs() != null) {
            body.setArgs(LocalizedMessages.stringArgs(e.getArgs()));
        }
        return body;
    }

    // services throw this when a row they were asked for does not exist
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> entityNotFound(EntityNotFoundException e) {
        log.warn("HTTP.ENTITY.NOT.FOUND message={}", e.getMessage());
        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.NOT_FOUND), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> uploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("HTTP.UPLOAD.TOO.LARGE");
        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadableRequestBody(HttpMessageNotReadableException e) {
        log.warn("HTTP.BODY.UNREADABLE type={}", e.getClass().getSimpleName());
        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<?> businessException(BusinessException e) {
        // 4xx is an expected outcome (validation, not found, forbidden): one line, no stack trace
        if (e.getHttpStatus() != null && e.getHttpStatus().is5xxServerError()) {
            log.error("BUSINESS.EXCEPTION status={} code={}", e.getHttpStatus(), e.getCode(), e);
        } else {
            log.warn("BUSINESS.EXCEPTION status={} code={} message={}", e.getHttpStatus(), e.getCode(), e.getMessage());
        }
        return new ResponseEntity<>(
                businessBody(e),
                e.getHttpStatus()
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(value = HttpStatus.UNAUTHORIZED)
    public ResponseEntity<?> badCredentialsException(BadCredentialsException e) {
        log.warn("AUTH.BAD.CREDENTIALS");
        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.BAD_CREDENTIALS),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(value = HttpStatus.UNAUTHORIZED)
    public ResponseEntity<?> disabledException(DisabledException e) {
        log.warn("AUTH.USER.DISABLED message={}", e.getMessage());
        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.USER_DISABLED),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AuthorizationDeniedException ex) {
        log.warn("AUTH.ACCESS.DENIED");
        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.ACCESS_DENIED),
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

        log.warn("VALIDATION.FAILED kind=method message={}", errorMessage);

        return new ResponseEntity<>(errorMessage.isBlank() ? BaseResponse.error(GlobalMessage.BAD_REQUEST) : validationError(errorMessage),
                HttpStatus.BAD_REQUEST);
    }

    // a request parameter that cannot be read as its type, e.g. from=abc for a date or an id that is no UUID
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> methodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("VALIDATION.FAILED kind=param param={}", ex.getName());
        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.INVALID_PARAMETER, ex.getName()),
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

        log.warn("VALIDATION.FAILED kind=body message={}", errorMessage);

        return new ResponseEntity<>(validationError(errorMessage),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(value = HttpStatus.UNAUTHORIZED)
    public ResponseEntity<?> usernameNotFoundException(UsernameNotFoundException unfe) {
        log.warn("AUTH.USER.NOT.FOUND message={}", unfe.getMessage());
        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.BAD_CREDENTIALS),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("VALIDATION.FAILED kind=constraint message={}", ex.getMessage());
//        Map<String, String> errors = new HashMap<>();
//        ex.getConstraintViolations().forEach(cv -> {
//            String field = cv.getPropertyPath().iterator().next().getName();
//            errors.put(field, cv.getMessage());
//        });
//        return errors;

        return new ResponseEntity<>(BaseResponse.error(GlobalMessage.BAD_REQUEST),
                HttpStatus.BAD_REQUEST);
    }

}
