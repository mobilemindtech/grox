package org.grails.datastore.mapping.multitenancy.resolvers

import org.grails.datastore.mapping.multitenancy.TenantResolver

class FixedTenantResolver implements TenantResolver {

    Serializable tenantId

    FixedTenantResolver() {
        this('DEFAULT')
    }

    FixedTenantResolver(Serializable tenantId) {
        this.tenantId = tenantId
    }

    @Override
    Serializable resolveTenantIdentifier() {
        tenantId
    }
}
