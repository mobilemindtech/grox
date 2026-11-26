package org.grails.datastore.mapping.engine.event

import groovy.transform.CompileStatic

@CompileStatic
class PreUpdateEvent extends AbstractPersistenceEvent {
    PreUpdateEvent(Object entity) { super(entity) }

    @Override
    EventType getEventType() { EventType.PreUpdate }
}
