package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What somebody typed in the search box, checked once and turned into what the search queries need. 2 to 50
 * characters after trimming, anything else answers 400: one letter matches nearly everything and is no search.
 */
public record SearchTerm(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 50;
    // more words than this narrow nothing down, they only make the query longer
    private static final int MAX_WORDS = 8;
    // the words the 'simple' text search configuration indexes: runs of letters and digits, in any script
    private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    public SearchTerm {
        value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }

    public static SearchTerm of(String raw) {
        return new SearchTerm(raw);
    }

    /** "%term%", with % _ and ! typed by the user matched literally (ESCAPE '!'). */
    public String containsPattern() {
        return "%" + escapeLike(value) + "%";
    }

    /** "term%", escaped like {@link #containsPattern()}. */
    public String prefixPattern() {
        return escapeLike(value) + "%";
    }

    /**
     * A full-text query that wants every word, each also as the start of a longer word, so a word still being
     * typed already matches ("kopi sus" finds "kopi susu"): {@code kopi:* & sus:*}. Only letters and digits
     * get in, so nothing the user typed is read as tsquery syntax. Empty when the term holds no word at all
     * (only punctuation), which can match no caption.
     */
    public String prefixTsQuery() {
        return Arrays.stream(NOT_A_WORD.split(value))
                .filter(word -> !word.isEmpty())
                .limit(MAX_WORDS)
                .map(word -> word + ":*")
                .collect(Collectors.joining(" & "));
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
