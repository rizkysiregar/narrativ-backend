package com.rizkysiregar.feedzmore.constant;

import lombok.Getter;

@Getter
public enum GlobalMessage {

    SUCCESS("200", "Success"),
    INTERNAL_ERROR("500", "Internal Server Error"),
    BAD_REQUEST("400", "Bad Request"),
    BAD_REQUEST_FILE_TO_LARGE("400", "Bad Request, file too large"),
    UNAUTHORIZED("401", "UNAUTHORIZED"),;


    private final String code;
    private final String message;

    GlobalMessage(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
