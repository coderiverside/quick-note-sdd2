package dev.quicknote.notes.domain.port;

import java.util.Optional;

import dev.quicknote.notes.domain.Label;
import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.notes.domain.UserId;

/** Persistence for labels. Every lookup is scoped to the owner; there is no unscoped finder. */
public interface LabelRepository {

    Label save(Label label);

    Optional<Label> findOwned(LabelId id, UserId owner);

    Page<Label> list(UserId owner, int page, int size);

    /** True if the owner already has a label whose comparison key matches, ignoring one identifier. */
    boolean nameTaken(UserId owner, String comparisonKey, LabelId ignoring);

    void delete(LabelId id, UserId owner);
}
