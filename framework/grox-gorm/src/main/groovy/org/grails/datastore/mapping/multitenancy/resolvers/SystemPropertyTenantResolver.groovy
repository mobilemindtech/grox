package org.grails.datastore.mapping.multitenancy.resolvers

import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException

/** Tenant da propriedade de sistema {@code gorm.tenantId} (útil em testes). */
class SystemPropertyTenantResolver implements TenantResolver {

    static final String PROPERTY = 'gorm.tenantId'

    @Override
    Serializable resolveTenantIdentifier() {
        String id = System.getProperty(PROPERTY)
        if (!id) throw new TenantNotFoundException("propriedade de sistema ${PROPERTY} não definida")
        id
    }
}
