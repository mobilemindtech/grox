package org.grails.datastore.mapping.engine.event

import groovy.transform.CompileStatic

@CompileStatic
class PreDeleteEvent extends AbstractPersistenceEvent {
    PreDeleteEvent(Object entity) { super(entity) }

    @Override
    EventType getEventType() { EventType.PreDelete }
}
