package com.meant.api.common.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Lets Spring Data persist new entities with application-assigned IDs directly.
 *
 * <p>Without an explicit new-state marker, a non-null ID makes {@code save} use JPA merge,
 * which can issue an existence select before every insert. Loaded and already-persisted
 * entities are marked non-new so their update semantics remain unchanged.</p>
 */
@MappedSuperclass
public abstract class AssignedIdEntity<ID> implements Persistable<ID> {

    @Transient
    private boolean newEntity = true;

    protected AssignedIdEntity() {
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    protected void markNotNew() {
        this.newEntity = false;
    }
}
