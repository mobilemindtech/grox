package org.grox.plugins.auditlog

import org.grox.core.GroxModule
import org.grox.data.GormEvents
import org.grox.data.GormModule
import org.grox.plugins.springsecurity.SpringSecurityModule

/**
 * Plugin audit-logging ({@code grails.plugin.auditLog.auditDomainClassName}): bean
 * {@code auditRequestResolver} (o do resources.groovy, se houver) e o listener de auditoria.
 */
class AuditLogModule extends GroxModule {

    int getOrder() { 640 }

    private Map auditLogConfig() {
        Object yaml = config.getProperty('grails.plugin.auditLog')
        runtime.module(GormModule)?.datastore && yaml instanceof Map && ((Map) yaml).auditDomainClassName ? (Map) yaml : null
    }

    @Override
    void beansReady(Map<String, Object> beans) {
        if (auditLogConfig() == null || beans.auditRequestResolver instanceof grails.plugins.orm.auditable.resolvers.AuditRequestResolver) return
        def springSecurityService = runtime.module(SpringSecurityModule)?.springSecurityService
        beans.auditRequestResolver = springSecurityService
                ? new grails.plugins.orm.auditable.resolvers.SpringSecurityRequestResolver(springSecurityService: springSecurityService)
                : new grails.plugins.orm.auditable.resolvers.DefaultAuditRequestResolver()
    }

    /** Depois dos listeners do GORM (que são zerados numa recarga). */
    @Override
    void contextReady() {
        Map yaml = auditLogConfig()
        if (yaml == null) {
            AuditLogPlugin.shutdown()
            return
        }
        def resolver = (grails.plugins.orm.auditable.resolvers.AuditRequestResolver) runtime.autowirer.beans.auditRequestResolver
        if (AuditLogPlugin.init(yaml, runtime.application.classLoader, resolver)) {
            GormEvents.register(new AuditLogListener())
        }
    }

    /** Termina as gravações assíncronas pendentes. */
    @Override
    void release(Shutdown shutdown) {
        if (!shutdown.reload) AuditLogPlugin.shutdown()
    }
}
