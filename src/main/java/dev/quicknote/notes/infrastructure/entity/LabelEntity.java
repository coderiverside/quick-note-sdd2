package dev.quicknote.notes.infrastructure.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The persistence shape of a label. Never referenced from outside the infrastructure package. */
@Entity
@Table(name = "labels")
public class LabelEntity {

    @Id
    public UUID id;

    @Column(name = "owner_id", nullable = false)
    public String ownerId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
