package com.rizkysiregar.feedzmore.util;

import java.util.Locale;

public final class LikePatterns {

    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /** Lower cased "%value%" pattern where % and _ typed by the admin are matched literally. */
    public static String contains(String value) {
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
