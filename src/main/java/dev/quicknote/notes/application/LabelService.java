package dev.quicknote.notes.application;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import dev.quicknote.notes.domain.Label;
import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.notes.domain.port.LabelRepository;
import dev.quicknote.notes.domain.port.NoteRepository;
import dev.quicknote.shared.correlation.CorrelationContext;
import dev.quicknote.shared.problem.DomainException;

/**
 * Use cases for labels.
 *
 * <p>Deleting a label detaches it from every note and deletes no note — a rule enforced twice, here
 * and by the cascade on the attachment table, because losing a user's notes to a tidy-up is not a
 * recoverable mistake.
 */
@ApplicationScoped
public class LabelService {

    private static final Logger LOG = Logger.getLogger(LabelService.class);

    private final LabelRepository labels;
    private final NoteRepository notes;

    @Inject
    public LabelService(LabelRepository labels, NoteRepository notes) {
        this.labels = labels;
        this.notes = notes;
    }

    @Transactional
    public Label create(UserId owner, String name) {
        Label label = Label.create(owner, name);
        if (labels.nameTaken(owner, label.comparisonKey(), null)) {
            throw new DomainException.LabelNameConflict();
        }
        Label saved = labels.save(label);
        LOG.infof(
                "label created [labelId=%s owner=%s correlationId=%s]",
                saved.id(), owner.value(), CorrelationContext.current());
        return saved;
    }

    public Page<Label> list(UserId owner, int page, int size) {
        return labels.list(owner, page, size);
    }

    @Transactional
    public Label rename(UserId owner, LabelId id, String newName) {
        Label label = requireOwned(owner, id);
        label.rename(newName);
        if (labels.nameTaken(owner, label.comparisonKey(), id)) {
            throw new DomainException.LabelNameConflict();
        }
        // Renaming touches no note content; every note carrying the label sees the new name at once,
        // because notes reference the label by identity rather than by name.
        return labels.save(label);
    }

    @Transactional
    public void delete(UserId owner, LabelId id) {
        Label label = requireOwned(owner, id);
        notes.detachLabelEverywhere(label.id(), owner);
        labels.delete(label.id(), owner);
        LOG.infof(
                "label deleted [labelId=%s owner=%s correlationId=%s]",
                label.id(), owner.value(), CorrelationContext.current());
    }

    /**
     * Confirms every label belongs to the caller before a note is allowed to reference it. A label
     * owned by someone else is reported as absent, exactly like one that never existed.
     */
    public void requireAllOwned(UserId owner, Set<LabelId> labelIds) {
        for (LabelId labelId : labelIds) {
            requireOwned(owner, labelId);
        }
    }

    private Label requireOwned(UserId owner, LabelId id) {
        return labels.findOwned(id, owner).orElseThrow(DomainException.LabelNotFound::new);
    }
}
