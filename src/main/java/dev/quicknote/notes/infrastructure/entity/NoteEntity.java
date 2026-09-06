package dev.quicknote.notes.infrastructure.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The persistence shape of a note.
 *
 * <p>This type never leaves the infrastructure package — {@code LayerDependencyTest} fails the build
 * if it does. Everything outside sees the domain {@code Note} instead.
 */
@Entity
@Table(name = "notes")
public class NoteEntity {

    @Id
    public UUID id;

    @Column(name = "owner_id", nullable = false)
    public String ownerId;

    @Column(name = "title")
    public String title;

    @Column(name = "body")
    public String body;

    @Column(name = "color", nullable = false)
    public String color;

    @Column(name = "pinned", nullable = false)
    public boolean pinned;

    @Column(name = "state", nullable = false)
    public String state;

    @Column(name = "previous_state")
    public String previousState;

    @Column(name = "trashed_at")
    public Instant trashedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    public long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "note_labels", joinColumns = @JoinColumn(name = "note_id"))
    @Column(name = "label_id", nullable = false)
    public Set<UUID> labelIds = new LinkedHashSet<>();
}
