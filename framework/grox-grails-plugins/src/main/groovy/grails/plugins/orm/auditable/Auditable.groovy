package grails.plugins.orm.auditable

import org.grox.data.DomainClass
import org.grox.data.DomainProperty
import org.grox.data.Gorm
import org.grox.plugins.auditlog.AuditLogPlugin

/**
 * Domain classes auditadas ({@code class Cliente implements Auditable}), como no plugin audit-logging:
 * inserção, alteração (propriedades alteradas, valor antigo e novo) e exclusão gravadas na domain
 * {@code grails.plugin.auditLog.auditDomainClassName}. Os métodos podem ser sobrescritos na domain.
 */
trait Auditable {

    boolean isAuditable(AuditEventType eventType) {
        true
    }

    boolean isLogAssociatedIds() {
        AuditLogContext.context.logIds as boolean
    }

    Collection<AuditEventType> getLogVerboseEvents() {
        Map c = AuditLogContext.context
        if (c.verbose && !c.verboseEvents) return AuditEventType.values() as Set<AuditEventType>
        c.verboseEvents ? (c.verboseEvents as Set<AuditEventType>) : Collections.<AuditEventType> emptySet()
    }

    String getLogClassName() {
        AuditLogContext.context.logFullClassName ? getClass().name : getClass().simpleName
    }

    Collection<String> getLogExcluded() {
        (AuditLogContext.context.excluded ?: []) as Set<String>
    }

    Collection<String> getLogIncluded() {
        AuditLogContext.context.included as Set<String>
    }

    Collection<String> getLogMaskProperties() {
        (AuditLogContext.context.mask ?: []) as Set<String>
    }

    Collection<AuditEventType> getLogIgnoreEvents() {
        ((AuditLogContext.context.ignoreEvents ?: []) as List).collect { it instanceof AuditEventType ? it : AuditEventType.valueOf(it.toString()) } as Set<AuditEventType>
    }

    String getLogURI() {
        AuditLogPlugin.resolver?.currentURI
    }

    String getLogCurrentUserName() {
        AuditLogPlugin.resolver?.currentActor ?: 'N/A'
    }

    /** Propriedades persistentes, filtradas por {@code included}/{@code excluded}. */
    Collection<String> getAuditablePropertyNames() {
        DomainClass d = Gorm.domain(getClass())
        // como o getPersistentProperties() do GORM: sem a identidade, com a versão
        Set<String> persistent = new LinkedHashSet<>(d.persistentProperties.keySet())
        if (d.identity) persistent.remove(d.identity.name)
        if (d.version) persistent << d.version.name
        Set<String> logged = logIncluded != null ? (logIncluded as Set<String>) : persistent - (logExcluded ?: [])
        logged.intersect(persistent) as Set<String>
    }

    Collection<String> getAuditableDirtyPropertyNames() {
        auditablePropertyNames.intersect(invokeMethod('getDirtyPropertyNames', null) as Collection<String>) as Set<String>
    }

    String getLogEntityId() {
        DomainClass d = Gorm.domain(getClass())
        if (!d.composite) return convertLoggedPropertyToString('id', d.identity.get(this))
        '[' + d.compositeProperties.collect { DomainProperty p ->
            Object v = p.get(this)
            "${p.name}:${v instanceof Auditable ? ((Auditable) v).logEntityId : convertLoggedPropertyToString(p.name, v)}"
        }.join(', ') + ']'
    }

    String convertLoggedPropertyToString(String propertyName, Object value) {
        if (value instanceof Enum) return ((Enum) value).name()
        if (value instanceof Auditable) return "[id:${((Auditable) value).logEntityId}]$value"
        if (value != null && Gorm.isDomain(value.getClass())) return "[id:${Gorm.domain(value.getClass()).identity?.get(value)}]$value"
        if (value instanceof Collection) {
            return logAssociatedIds ? ((Collection) value).collect { convertLoggedPropertyToString(propertyName, it) }.join(', ') : 'N/A'
        }
        value?.toString()
    }

    /** Chamado antes de enfileirar cada registro; devolva false para descartá-lo. */
    boolean beforeSaveLog(Object auditEntity) {
        true
    }
}
