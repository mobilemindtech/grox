package grails.plugins.orm.auditable.resolvers

import grails.plugins.orm.auditable.AuditLogContext
import org.grox.security.SecurityContextHolder
import org.grox.web.RequestContext

/** Usuário autenticado (ou {@code defaultActor}) e URI da requisição corrente. */
class DefaultAuditRequestResolver implements AuditRequestResolver {

    @Override
    String getCurrentActor() {
        SecurityContextHolder.authentication?.principal?.username ?: AuditLogContext.context.defaultActor ?: 'N/A'
    }

    @Override
    String getCurrentURI() {
        RequestContext.currentOrNull()?.request?.path
    }
}
