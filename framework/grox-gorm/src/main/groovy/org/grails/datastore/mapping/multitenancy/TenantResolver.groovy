package org.grails.datastore.mapping.multitenancy

import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException

/** Resolve o tenant atual ({@code grails.gorm.multiTenancy.tenantResolverClass}). */
interface TenantResolver {
    Serializable resolveTenantIdentifier() throws TenantNotFoundException
}
