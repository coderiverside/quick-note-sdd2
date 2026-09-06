package dev.quicknote.notes.domain;

/**
 * A listing request, expressed in domain terms so the application layer never hands a transport or
 * persistence type to the repository.
 *
 * @param owner the only user whose notes may be returned
 * @param state which notes to include; never null
 * @param labelId optional label filter
 * @param search optional case-insensitive substring matched against title and body
 */
public record NoteQuery(UserId owner, NoteState state, LabelId labelId, String search, int page, int size) {

    public static NoteQuery of(UserId owner, NoteState state, int page, int size) {
        return new NoteQuery(owner, state, null, null, page, size);
    }

    public NoteQuery withLabel(LabelId label) {
        return new NoteQuery(owner, state, label, search, page, size);
    }

    public NoteQuery withSearch(String term) {
        return new NoteQuery(owner, state, labelId, term, page, size);
    }

    public boolean hasLabelFilter() {
        return labelId != null;
    }

    public boolean hasSearch() {
        return search != null && !search.isBlank();
    }
}
