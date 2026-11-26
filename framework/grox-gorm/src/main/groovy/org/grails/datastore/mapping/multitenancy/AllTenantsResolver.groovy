package org.grails.datastore.mapping.multitenancy

/** Resolver que também sabe listar todos os tenants (usado por {@code Tenants.eachTenant}). */
interface AllTenantsResolver extends TenantResolver {
    Iterable<Serializable> resolveTenantIds()
}
