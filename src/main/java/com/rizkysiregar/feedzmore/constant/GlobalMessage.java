package com.rizkysiregar.feedzmore.constant;

import lombok.Getter;

@Getter
public enum GlobalMessage {

    SUCCESS("200", "Success"),
    INTERNAL_ERROR("500", "Internal Server Error"),
    BAD_REQUEST("400", "Bad Request"),
    UNAUTHORIZED("401", "UNAUTHORIZED"),;


    private final String code;
    private final String message;

    GlobalMessage(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
