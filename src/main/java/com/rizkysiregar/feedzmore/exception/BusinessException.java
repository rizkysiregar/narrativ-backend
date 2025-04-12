package com.rizkysiregar.feedzmore.exception;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import lombok.*;
import org.springframework.http.HttpStatus;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BusinessException extends RuntimeException {

    private String code;
    private String message;
    private HttpStatus httpStatus;

    public BusinessException(GlobalMessage globalMessage, HttpStatus httpStatus) {
        super(globalMessage.getMessage());
        this.code = globalMessage.getCode();
        this.message = globalMessage.getMessage();
        this.httpStatus = httpStatus;
    }

    public BusinessException(GlobalMessage globalMessage, String additionalMessage, HttpStatus httpStatus) {
        super(globalMessage.getMessage() + " " + additionalMessage);
        this.code = globalMessage.getCode();
        this.message = globalMessage.getMessage() + " " + additionalMessage;
        this.httpStatus = httpStatus;
    }

    @SneakyThrows
    public static void throwError(GlobalMessage globalMessage, String additionalMessage, HttpStatus httpStatus) {
        throw new BusinessException(globalMessage, additionalMessage, httpStatus);
    }

    @SneakyThrows
    public static void throwError(GlobalMessage globalMessage, HttpStatus httpStatus) {
        throw new BusinessException(globalMessage, httpStatus);
    }

}
