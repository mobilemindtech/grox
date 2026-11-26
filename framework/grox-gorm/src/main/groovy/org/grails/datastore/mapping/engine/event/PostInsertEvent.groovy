package org.grails.datastore.mapping.engine.event

import groovy.transform.CompileStatic

@CompileStatic
class PostInsertEvent extends AbstractPersistenceEvent {
    PostInsertEvent(Object entity) { super(entity) }

    @Override
    EventType getEventType() { EventType.PostInsert }
}
