package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.model.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
@Slf4j
public class BaseController {
    // every success response will be appended here !!
    public static BaseResponse<?> buildSuccessResponse(Object data) {
        return BaseResponse.builder()
                .code(GlobalMessage.SUCCESS.getCode())
                .message(GlobalMessage.SUCCESS.getMessage())
                .data(data)
                .build();
    }
}
