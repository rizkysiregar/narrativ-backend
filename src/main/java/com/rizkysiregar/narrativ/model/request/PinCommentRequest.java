package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class PinCommentRequest {
    @NotNull
    UUID commentId;
}
