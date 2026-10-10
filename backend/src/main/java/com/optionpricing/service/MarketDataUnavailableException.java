package com.optionpricing.service;

public class MarketDataUnavailableException extends RuntimeException{
    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
