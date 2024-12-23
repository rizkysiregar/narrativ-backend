package com.rizkysiregar.feedzmore.util;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.response.BaseResponse;

public class ResponseUtil {
    public static <T> BaseResponse<T> success(T data) {
        return BaseResponse.<T>builder()
                .code("200")
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> BaseResponse<T> success(String message, T data) {
        return BaseResponse.<T>builder()
                .code("200")
                .message(message)
                .data(data)
                .build();
    }


    public static BaseResponse<Void> success(String message) {
        return BaseResponse.<Void>builder()
                .code("200")
                .message(message)
                .build();
    }

    public static BaseResponse<Void> error(GlobalMessage globalMessage) {
        return BaseResponse.<Void>builder()
                .code(globalMessage.getCode())
                .message(globalMessage.getMessage())
                .build();
    }

    public static BaseResponse<Void> error(String message) {
        return BaseResponse.<Void>builder()
                .code("500")
                .message(message)
                .build();
    }

}
