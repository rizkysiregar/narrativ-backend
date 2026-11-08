package com.rizkysiregar.feedzmore.model.enums;

public enum MetricGranularity {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String sqlUnit;

    MetricGranularity(String sqlUnit) {
        this.sqlUnit = sqlUnit;
    }

    /** The unit for date_trunc; a week starts on Monday. */
    public String sqlUnit() {
        return sqlUnit;
    }
}
