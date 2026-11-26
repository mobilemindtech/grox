package org.grox.testdomain

import grails.gorm.MultiTenant
import grails.plugins.orm.auditable.Auditable

/** Entidade multi-tenant auditada (teste do audit log com tenant). */
class NotaAuditada implements MultiTenant<NotaAuditada>, Auditable {
    String texto
}
