package org.grails.datastore.mapping.engine.event

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.engine.EntityAccess

/** Evento de persistência do GORM (subconjunto): entidade e acesso às propriedades. */
@CompileStatic
abstract class AbstractPersistenceEvent extends org.springframework.context.ApplicationEvent {
    final Object entityObject
    final EntityAccess entityAccess
    boolean cancelled

    AbstractPersistenceEvent(Object entity) {
        super(entity)
        this.entityObject = entity
        this.entityAccess = new EntityAccess(entity)
    }

    abstract EventType getEventType()

    void cancel() { cancelled = true }
}
