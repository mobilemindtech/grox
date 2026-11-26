package org.grox.testdomain

import grails.gorm.MultiTenant

/** Domain de auditoria multi-tenant (grails.plugin.auditLog.auditDomainClassName). */
class AuditoriaTenant implements MultiTenant<AuditoriaTenant> {
    String actor
    String uri
    String className
    String eventName
    String persistedObjectId
    Long persistedObjectVersion
    String propertyName
    String oldValue
    String newValue

    static constraints = {
        actor nullable: true; uri nullable: true; className nullable: true; eventName nullable: true
        persistedObjectId nullable: true; persistedObjectVersion nullable: true
        propertyName nullable: true; oldValue nullable: true; newValue nullable: true
    }
}
