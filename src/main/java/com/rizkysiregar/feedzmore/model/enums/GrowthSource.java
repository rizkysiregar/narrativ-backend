package com.rizkysiregar.feedzmore.model.enums;

/** What the growth statistics count. The table name is fixed here, never taken from a request. */
public enum GrowthSource {
    USERS("users"),
    POSTS("posts");

    private final String tableName;

    GrowthSource(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
