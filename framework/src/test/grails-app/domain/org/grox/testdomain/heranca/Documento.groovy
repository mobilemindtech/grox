package org.grox.testdomain.heranca

/** Hierarquia multi-tenant: o tenantId fica na raiz e vale para as subclasses. */
class Documento implements grails.gorm.MultiTenant<Documento> {
    String titulo
}
