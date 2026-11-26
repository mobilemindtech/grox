package org.grails.datastore.mapping.engine.event

import groovy.transform.CompileStatic

@CompileStatic
class PostDeleteEvent extends AbstractPersistenceEvent {
    PostDeleteEvent(Object entity) { super(entity) }

    @Override
    EventType getEventType() { EventType.PostDelete }
}
