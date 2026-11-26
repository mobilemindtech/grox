package sample

/** Registros do audit log (grails.plugin.auditLog.auditDomainClassName), como o gerado pelo audit-quickstart. */
class AuditTrail {
    String actor
    String uri
    String className
    String persistedObjectId
    Long persistedObjectVersion = 0
    String eventName
    String propertyName
    String oldValue
    String newValue
    Date dateCreated
    Date lastUpdated

    static constraints = {
        actor nullable: true
        uri nullable: true
        className nullable: true
        persistedObjectId nullable: true
        persistedObjectVersion nullable: true
        eventName nullable: true
        propertyName nullable: true
        oldValue nullable: true, maxSize: 20
        newValue nullable: true, maxSize: 20
    }

    static mapping = {
        table 'audit_log'
        version false
    }
}
