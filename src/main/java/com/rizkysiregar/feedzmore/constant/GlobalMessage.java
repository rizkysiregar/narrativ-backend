package com.rizkysiregar.feedzmore.constant;

import lombok.Getter;

@Getter
public enum GlobalMessage {

    SUCCESS("200", "Success"),
    CREATED("201", "Created"),
    INTERNAL_ERROR("500", "Internal Server Error"),
    BAD_REQUEST("400", "Bad Request"),
    TOO_MANY_REQUEST("429", "Too many request"),
    TOO_MANY_REQUEST_OTP("429", "Too many request, You can request a new OTP after 5 minutes."),
    BAD_REQUEST_FILE_TO_LARGE("400", "Bad Request, file too large"),
    NOT_FOUND("404", "Data Not Found"),
    UNAUTHORIZED("401", "UNAUTHORIZED"),
    PARAMETER_NOT_FOUND("404", "Parameter Not Found")
    ;


    private final String code;
    private final String message;

    GlobalMessage(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
