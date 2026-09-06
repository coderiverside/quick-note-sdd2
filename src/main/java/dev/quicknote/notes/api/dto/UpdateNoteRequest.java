package dev.quicknote.notes.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Matches the {@code UpdateNoteRequest} schema.
 *
 * <p>{@code JsonNode} is doing real work for {@code title} and {@code body}: a Java {@code null} means
 * the property was absent and the field is untouched, while a {@code NullNode} means an explicit JSON
 * null and clears it. {@code Optional} cannot express this — Jackson deserializes an absent property
 * to {@code Optional.empty()}, which is indistinguishable from an explicit null, and PATCH would then
 * be unable to leave a field alone.
 */
public record UpdateNoteRequest(
        JsonNode title, JsonNode body, String color, Boolean pinned, Boolean archived, Boolean trashed) {

    public boolean isEmpty() {
        return title == null && body == null && color == null && pinned == null && archived == null && trashed == null;
    }

    public boolean touchesContent() {
        return title != null || body != null || color != null;
    }
}
