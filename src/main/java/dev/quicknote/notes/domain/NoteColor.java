package dev.quicknote.notes.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** The closed palette a note's colour is drawn from. The wire form is the lowercase name. */
public enum NoteColor {
    DEFAULT,
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    TEAL,
    BLUE,
    DARK_BLUE,
    PURPLE,
    PINK,
    BROWN;

    /** The wire form: lowercase, underscore-separated. */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Every permitted value, in wire form — used to build the error message on a bad colour. */
    public static String permittedValues() {
        return Arrays.stream(values()).map(NoteColor::wireName).collect(Collectors.joining(", "));
    }

    /** Parses a wire value, returning empty rather than throwing so the caller owns the error shape. */
    public static Optional<NoteColor> fromWire(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(c -> c.wireName().equals(value.trim().toLowerCase(Locale.ROOT)))
                .findFirst();
    }
}
