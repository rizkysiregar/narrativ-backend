package com.rizkysiregar.feedzmore.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Simple return type
@AllArgsConstructor
@Getter
public enum Operation {
    CREATED,
    DRAFTED,
    SUBMITTED,
    UPDATED,
    ACTIVATED,
    APPROVED,
    DELETED,
    EXECUTED,
    FAILED,
    LIKED,
    DISLIKED
}
