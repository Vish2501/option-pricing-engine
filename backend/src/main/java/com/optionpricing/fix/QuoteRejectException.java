package com.optionpricing.fix;

/**
 * A quote request we understood but will not quote. It carries the FIX
 * QuoteRequestRejectReason (tag 658) code and the text to send back to the client.
 */
public class QuoteRejectException extends RuntimeException {
    private final int rejectReason;

    public QuoteRejectException(int rejectReason, String message) {
        super(message);
        this.rejectReason = rejectReason;
    }

    public int rejectReason() {
        return rejectReason;
    }
}
