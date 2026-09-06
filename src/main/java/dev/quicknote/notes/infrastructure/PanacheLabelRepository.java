package dev.quicknote.notes.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;

import dev.quicknote.notes.domain.Label;
import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.notes.domain.port.LabelRepository;
import dev.quicknote.notes.infrastructure.entity.LabelEntity;
import dev.quicknote.notes.infrastructure.mapper.LabelMapper;

/** Panache repositories again, for the same reason: the ORM stays out of the domain. */
@ApplicationScoped
public class PanacheLabelRepository implements PanacheRepositoryBase<LabelEntity, UUID>, LabelRepository {

    @Override
    public Label save(Label label) {
        LabelEntity entity = findById(label.id().value());
        if (entity == null) {
            entity = LabelMapper.toEntity(label);
            persist(entity);
        } else {
            LabelMapper.apply(label, entity);
        }
        return LabelMapper.toDomain(entity);
    }

    @Override
    public Optional<Label> findOwned(LabelId id, UserId owner) {
        return find("id = ?1 and ownerId = ?2", id.value(), owner.value())
                .firstResultOptional()
                .map(LabelMapper::toDomain);
    }

    @Override
    public Page<Label> list(UserId owner, int page, int size) {
        var query = find("ownerId = ?1", Sort.ascending("name"), owner.value());
        long total = query.count();
        List<Label> labels = query.page(page, size).list().stream()
                .map(LabelMapper::toDomain)
                .toList();
        return new Page<>(labels, page, size, total);
    }

    @Override
    public boolean nameTaken(UserId owner, String comparisonKey, LabelId ignoring) {
        // The database index is the real guarantee; this exists to produce a clean 409 rather than a
        // constraint-violation stack in the common, uncontended case.
        UUID ignore = ignoring == null ? null : ignoring.value();
        return find(
                        "ownerId = ?1 and lower(trim(name)) = ?2 and (?3 is null or id <> ?3)",
                        owner.value(),
                        comparisonKey,
                        ignore)
                .firstResultOptional()
                .isPresent();
    }

    @Override
    public void delete(LabelId id, UserId owner) {
        delete("id = ?1 and ownerId = ?2", id.value(), owner.value());
    }
}
