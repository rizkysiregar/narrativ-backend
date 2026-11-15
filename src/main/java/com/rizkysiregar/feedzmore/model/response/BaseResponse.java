package com.rizkysiregar.feedzmore.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.i18n.LocalizedMessages;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaseResponse<T> {
    private String code;
    private String message;
    private T data;
    /** Stable identifier of an error (the name of its GlobalMessage); absent on success. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String errorCode;
    /** Values filled into the message, so a client that words errors itself can reuse them; absent when none. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String[] args;

    /** An error body worded in the language of the current request. */
    public static BaseResponse<Void> error(GlobalMessage globalMessage, Object... args) {
        return BaseResponse.<Void>builder()
                .code(globalMessage.getCode())
                .errorCode(globalMessage.getErrorCode())
                .message(LocalizedMessages.resolve(globalMessage, args))
                .args(LocalizedMessages.stringArgs(args))
                .build();
    }
}
