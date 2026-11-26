package grails.plugins.orm.auditable

import org.grox.core.Scoped
import org.grox.plugins.auditlog.AuditLogConfig

/**
 * Configuração do audit log ({@code grails.plugin.auditLog.*}) com sobrescritas por bloco, como no
 * plugin: {@code AuditLogContext.withoutAuditLog { ... }}, {@code withConfig([verbose: false]) { ... }}.
 * O escopo usa {@code ScopedValue} (o plugin usa ThreadLocal).
 */
class AuditLogContext {

    private static final ScopedValue<Map> LOCAL = ScopedValue.newInstance()

    static <T> T withConfig(Map config, Closure<T> block) {
        Scoped.where(LOCAL, mergeConfig(config), block)
    }

    static <T> T withoutVerboseAuditLog(Closure<T> block) {
        withConfig([verbose: false], block)
    }

    static <T> T withoutAuditLog(Closure<T> block) {
        withConfig([disabled: true], block)
    }

    static Map getContext() {
        LOCAL.bound ? LOCAL.get() : AuditLogConfig.current
    }

    private static Map mergeConfig(Map local) {
        getContext() + (local ?: [:])
    }
}
