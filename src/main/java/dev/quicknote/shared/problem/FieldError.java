package dev.quicknote.shared.problem;

/** One offending field in a validation failure. Codes are part of the contract. */
public record FieldError(String field, String code, String message) {

    public static final String REQUIRED = "required";
    public static final String TOO_LONG = "too_long";
    public static final String BLANK = "blank";
    public static final String OUT_OF_RANGE = "out_of_range";
    public static final String INVALID_FORMAT = "invalid_format";
    public static final String TOO_MANY_ITEMS = "too_many_items";
}
