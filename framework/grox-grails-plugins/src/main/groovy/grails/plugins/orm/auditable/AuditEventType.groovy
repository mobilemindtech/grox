package grails.plugins.orm.auditable

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.engine.event.EventType

/** Eventos auditados, como no plugin audit-logging. */
@CompileStatic
enum AuditEventType {
    INSERT, UPDATE, DELETE

    @Override
    String toString() { name() }

    static AuditEventType forEventType(EventType type) {
        switch (type) {
            case EventType.PostInsert: return INSERT
            case EventType.PreDelete: return DELETE
            case EventType.PreUpdate: return UPDATE
            default: throw new IllegalArgumentException("Evento inesperado: $type")
        }
    }
}
