package org.grails.datastore.mapping.engine.event

import groovy.transform.CompileStatic

@CompileStatic
class PreInsertEvent extends AbstractPersistenceEvent {
    PreInsertEvent(Object entity) { super(entity) }

    @Override
    EventType getEventType() { EventType.PreInsert }
}
