package org.grails.datastore.mapping.multitenancy.web

import org.grox.web.RequestContext
import org.grox.web.WebRequest
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException

/** Tenant guardado na sessão HTTP no atributo {@link #ATTRIBUTE}, como no GORM. */
class SessionTenantResolver implements TenantResolver {

    static final String ATTRIBUTE = 'gorm.tenantId'

    @Override
    Serializable resolveTenantIdentifier() {
        WebRequest wr = RequestContext.currentOrNull()
        Object id = wr?.getSession(false)?.getAttribute(ATTRIBUTE)
        if (id == null) throw new TenantNotFoundException("sessão sem o atributo ${ATTRIBUTE}")
        (Serializable) id
    }
}
