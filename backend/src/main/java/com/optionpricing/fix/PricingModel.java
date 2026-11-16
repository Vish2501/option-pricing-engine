package com.optionpricing.fix;

import com.optionpricing.dto.PricingResponse;

/**
 * The model a FIX client can ask to be quoted from. The pricing service always runs all
 * three models, so choosing one only decides which of its prices goes into the quote.
 */
public enum PricingModel {
    BLACK_SCHOLES("BS"),
    MONTE_CARLO("MC"),
    BINOMIAL_TREE("BT");

    /** Our own FIX tag carrying the model code. Tags from 5000 up are reserved for user-defined fields. */
    public static final int FIX_TAG = 5001;

    private final String fixCode;

    PricingModel(String fixCode) {
        this.fixCode = fixCode;
    }

    public String fixCode() {
        return fixCode;
    }

    public static PricingModel fromFixCode(String code) {
        for (PricingModel model : values()) {
            if (model.fixCode.equalsIgnoreCase(code.trim())) {
                return model;
            }
        }
        throw new IllegalArgumentException("Unknown pricing model '" + code + "'. Use BS, MC or BT.");
    }

    public double priceFrom(PricingResponse response) {
        return switch (this) {
            case BLACK_SCHOLES -> response.blackScholesPrice();
            case MONTE_CARLO -> response.monteCarloPrice();
            case BINOMIAL_TREE -> response.binomialTreePrice();
        };
    }
}
