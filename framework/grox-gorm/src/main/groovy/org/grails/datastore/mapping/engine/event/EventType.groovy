package org.grails.datastore.mapping.engine.event

/** Tipo do evento de persistência, como no GORM. */
enum EventType {
    PreInsert, PostInsert, PreUpdate, PostUpdate, PreDelete, PostDelete, PreLoad, PostLoad, SaveOrUpdate, Validation
}
