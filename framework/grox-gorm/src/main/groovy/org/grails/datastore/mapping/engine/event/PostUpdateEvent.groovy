package org.grails.datastore.mapping.engine.event

import groovy.transform.CompileStatic

@CompileStatic
class PostUpdateEvent extends AbstractPersistenceEvent {
    PostUpdateEvent(Object entity) { super(entity) }

    @Override
    EventType getEventType() { EventType.PostUpdate }
}
