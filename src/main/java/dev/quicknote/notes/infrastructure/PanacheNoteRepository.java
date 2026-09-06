package dev.quicknote.notes.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteId;
import dev.quicknote.notes.domain.NoteQuery;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.notes.domain.port.NoteRepository;
import dev.quicknote.notes.infrastructure.entity.NoteEntity;
import dev.quicknote.notes.infrastructure.mapper.NoteMapper;

/**
 * Panache in its repository flavour, deliberately — active-record entities would put the ORM
 * directly into the domain model, which Constitution Principle III forbids.
 *
 * <p>Every query is parameter-bound. Every query is index-backed; an unindexed access path is a
 * defect here, not a tuning opportunity.
 */
@ApplicationScoped
public class PanacheNoteRepository implements PanacheRepositoryBase<NoteEntity, UUID>, NoteRepository {

    @Inject
    EntityManager em;

    @Override
    public Note save(Note note) {
        NoteEntity entity = findById(note.id().value());
        if (entity == null) {
            entity = NoteMapper.toEntity(note);
            persist(entity);
        } else {
            NoteMapper.apply(note, entity);
        }
        return NoteMapper.toDomain(entity);
    }

    @Override
    public Optional<Note> findOwned(NoteId id, UserId owner) {
        // Ownership is part of the lookup, not a check afterwards: there is no way to obtain a note
        // that belongs to someone else.
        return find("id = ?1 and ownerId = ?2", id.value(), owner.value())
                .firstResultOptional()
                .map(NoteMapper::toDomain);
    }

    @Override
    public Page<Note> list(NoteQuery query) {
        StringBuilder where = new StringBuilder("owner_id = :owner AND state = :state");
        if (query.hasLabelFilter()) {
            where.append(" AND EXISTS (SELECT 1 FROM note_labels nl WHERE nl.note_id = n.id"
                    + " AND nl.label_id = :labelId)");
        }
        if (query.hasSearch()) {
            // Leading-wildcard ILIKE is only sane because pg_trgm GIN indexes back both columns.
            // The columns are compared bare on purpose: wrapping either in COALESCE makes the
            // predicate a function of an expression, and PostgreSQL will not use a column index for
            // it — turning every search into a sequential scan. NULL ILIKE yields NULL, and
            // NULL OR TRUE is TRUE, so a note with no title still matches on its body.
            where.append(" AND (title ILIKE :term ESCAPE '\\' OR body ILIKE :term ESCAPE '\\')");
        }

        String base = "FROM notes n WHERE " + where;
        Query countQuery = em.createNativeQuery("SELECT count(*) " + base);
        Query pageQuery = em.createNativeQuery("SELECT n.id " + base
                + " ORDER BY pinned DESC, updated_at DESC, n.id DESC LIMIT :limit OFFSET :offset");

        bind(countQuery, query, false);
        bind(pageQuery, query, true);

        long total = ((Number) countQuery.getSingleResult()).longValue();
        @SuppressWarnings("unchecked")
        List<UUID> ids = pageQuery.getResultList();

        List<Note> notes = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            NoteEntity entity = findById(id);
            if (entity != null) {
                notes.add(NoteMapper.toDomain(entity));
            }
        }
        return new Page<>(notes, query.page(), query.size(), total);
    }

    private void bind(Query q, NoteQuery query, boolean paged) {
        q.setParameter("owner", query.owner().value());
        q.setParameter("state", query.state().name());
        if (query.hasLabelFilter()) {
            q.setParameter("labelId", query.labelId().value());
        }
        if (query.hasSearch()) {
            q.setParameter("term", "%" + escapeLike(query.search()) + "%");
        }
        if (paged) {
            q.setParameter("limit", query.size());
            q.setParameter("offset", (long) query.page() * query.size());
        }
    }

    /** A search term containing %, _ or \ must match literally, never alter the pattern. */
    static String escapeLike(String term) {
        return term.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public boolean purge(NoteId id, UserId owner) {
        return delete("id = ?1 and ownerId = ?2 and state = ?3", id.value(), owner.value(), "TRASHED") > 0;
    }

    @Override
    public long purgeAllTrashed(UserId owner) {
        return delete("ownerId = ?1 and state = ?2", owner.value(), "TRASHED");
    }

    @Override
    public long purgeTrashedBefore(Instant cutoff, int batchSize) {
        @SuppressWarnings("unchecked")
        List<UUID> doomed = em.createNativeQuery(
                        "SELECT id FROM notes WHERE state = 'TRASHED' AND trashed_at < :cutoff LIMIT :batch")
                .setParameter("cutoff", cutoff)
                .setParameter("batch", batchSize)
                .getResultList();
        if (doomed.isEmpty()) {
            return 0;
        }
        return delete("id in ?1", doomed);
    }

    @Override
    public void detachLabelEverywhere(LabelId labelId, UserId owner) {
        em.createNativeQuery("DELETE FROM note_labels nl USING notes n"
                        + " WHERE nl.note_id = n.id AND nl.label_id = :labelId AND n.owner_id = :owner")
                .setParameter("labelId", labelId.value())
                .setParameter("owner", owner.value())
                .executeUpdate();
    }
}
