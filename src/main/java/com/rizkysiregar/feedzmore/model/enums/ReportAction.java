package com.rizkysiregar.feedzmore.model.enums;

/** What an admin did about a report. NONE closes it without touching the target. */
public enum ReportAction {
    NONE,
    TAKEDOWN,
    BAN,
    HARD_DELETE
}
