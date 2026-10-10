package com.optionpricing.model;

import java.util.regex.Pattern;

/** The one definition of what a ticker symbol may look like, shared by every endpoint that accepts one. */
public final class TickerSymbol {
    public static final String PATTERN = "^[A-Za-z0-9.\\-]{1,12}$";
    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private TickerSymbol() {
    }

    public static boolean isValid(String symbol) {
        return symbol != null && COMPILED.matcher(symbol).matches();
    }

    // Trims and upper-cases, rejecting anything that is not shaped like a ticker.
    public static String normalize(String raw) {
        String symbol = raw == null ? "" : raw.trim().toUpperCase();
        if (!isValid(symbol)) {
            throw new IllegalArgumentException("Ticker must be 1-12 letters, digits, dots or hyphens.");
        }
        return symbol;
    }
}
