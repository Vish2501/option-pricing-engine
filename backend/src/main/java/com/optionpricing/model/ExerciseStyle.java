package com.optionpricing.model;

public enum ExerciseStyle {
    // Can only be exercised at expiry.
    EUROPEAN,
    // Can be exercised at any time up to expiry.
    AMERICAN
}
