package org.grox.security.gorm

import org.grox.data.tenant.OptionalTenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grox.security.Authentication
import org.grox.security.SecurityContextHolder

/** Tenant do usuário autenticado ({@code userLookup.tenantPropertyName} / claim {@code tenant} do JWT). */
class UserTenantResolver implements OptionalTenantResolver {

    @Override
    Serializable resolveTenantIdentifierOrNull() {
        SecurityContextHolder.authentication?.principal?.tenantId
    }


    @Override
    Serializable resolveTenantIdentifier() {
        Authentication auth = SecurityContextHolder.authentication
        if (auth == null) throw new TenantNotFoundException('Tenant não encontrado: nenhum usuário autenticado')
        Serializable tenant = auth.principal?.tenantId
        if (tenant == null) throw new TenantNotFoundException("Tenant não encontrado: o usuário '${auth.name}' não tem tenant")
        tenant
    }
}
