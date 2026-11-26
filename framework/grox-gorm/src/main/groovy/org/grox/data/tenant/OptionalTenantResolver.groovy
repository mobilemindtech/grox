package org.grox.data.tenant

import org.grails.datastore.mapping.multitenancy.TenantResolver

/** Resolver que informa "sem tenant" com null em vez de exceção (evita o custo por requisição). */
interface OptionalTenantResolver extends TenantResolver {
    Serializable resolveTenantIdentifierOrNull()
}
