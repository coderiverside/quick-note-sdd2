package dev.quicknote.notes.infrastructure.mapper;

import dev.quicknote.notes.domain.Label;
import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.notes.infrastructure.entity.LabelEntity;

/** The entity/domain boundary for labels. */
public final class LabelMapper {

    private LabelMapper() {}

    public static Label toDomain(LabelEntity entity) {
        return Label.rehydrate(LabelId.of(entity.id), UserId.of(entity.ownerId), entity.name, entity.createdAt);
    }

    public static void apply(Label label, LabelEntity entity) {
        entity.id = label.id().value();
        entity.ownerId = label.ownerId().value();
        entity.name = label.name();
        entity.createdAt = label.createdAt();
    }

    public static LabelEntity toEntity(Label label) {
        LabelEntity entity = new LabelEntity();
        apply(label, entity);
        return entity;
    }
}
