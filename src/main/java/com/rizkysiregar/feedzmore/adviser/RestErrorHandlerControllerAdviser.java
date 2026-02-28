package com.rizkysiregar.feedzmore.adviser;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.response.BaseResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@AllArgsConstructor
public class RestErrorHandlerControllerAdviser implements ErrorController {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<?> exception(Exception e) {
        log.error("Exception = ", e);
        return new ResponseEntity<>(BaseResponse.builder()
                .code(GlobalMessage.INTERNAL_ERROR.getCode())
                .message(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .build(),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<?> businessException(BusinessException e) {
        log.error("BusinessException = ", e);
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
                .message(e.getMessage())
                .build(),
                HttpStatus.UNAUTHORIZED);
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
                .code(GlobalMessage.UNAUTHORIZED.getCode())
                .message(unfe.getMessage())
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
                .message(ex.getMessage())
                .build(),
                HttpStatus.BAD_REQUEST);
    }

}
