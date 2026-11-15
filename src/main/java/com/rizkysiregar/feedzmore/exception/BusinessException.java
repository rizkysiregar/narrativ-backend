package com.rizkysiregar.feedzmore.exception;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.i18n.LocalizedMessages;
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
    /** What the message was built from, so the response can be worded in the caller's language. Null for ad-hoc exceptions. */
    private GlobalMessage globalMessage;
    private Object[] args;

    public BusinessException(GlobalMessage globalMessage, HttpStatus httpStatus) {
        super(globalMessage.getMessage());
        this.code = globalMessage.getCode();
        this.message = globalMessage.getMessage();
        this.httpStatus = httpStatus;
        this.globalMessage = globalMessage;
    }

    public BusinessException(GlobalMessage globalMessage, HttpStatus httpStatus, Object... args) {
        super(LocalizedMessages.english(globalMessage, args));
        this.code = globalMessage.getCode();
        this.message = LocalizedMessages.english(globalMessage, args);
        this.httpStatus = httpStatus;
        this.globalMessage = globalMessage;
        this.args = args;
    }

    @SneakyThrows
    public static void throwError(GlobalMessage globalMessage, HttpStatus httpStatus) {
        throw new BusinessException(globalMessage, httpStatus);
    }

    /** The message in the language of the current request. */
    public String getLocalizedMessage() {
        return globalMessage == null ? message : LocalizedMessages.resolve(globalMessage, args);
    }

    /** Stable identifier clients can switch on; null for ad-hoc exceptions that carry no {@link GlobalMessage}. */
    public String getErrorCode() {
        return globalMessage == null ? null : globalMessage.getErrorCode();
    }

}
