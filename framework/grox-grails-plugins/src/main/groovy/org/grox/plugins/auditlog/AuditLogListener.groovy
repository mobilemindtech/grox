package org.grox.plugins.auditlog

import grails.plugins.orm.auditable.AuditEventType
import grails.plugins.orm.auditable.AuditLogContext
import grails.plugins.orm.auditable.Auditable
import org.grox.data.DomainClass
import org.grox.data.DomainProperty
import org.grox.data.Gorm
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.EventType
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEvent

/**
 * Listener do audit log, com a lógica do {@code AuditLogListener} do plugin: INSERT (depois de
 * inserir) e DELETE (antes de excluir) com todas as propriedades auditáveis no modo verbose; UPDATE
 * (antes de gravar) com as propriedades alteradas, valor antigo e novo. Um registro por propriedade.
 */
class AuditLogListener extends AbstractPersistenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditLogListener)

    AuditLogListener() {
        super(null)
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        eventType == PostInsertEvent || eventType == PreUpdateEvent || eventType == PreDeleteEvent
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        if (!(event.entityObject instanceof Auditable) || AuditLogPlugin.auditDomainClass == null) return
        Map context = AuditLogContext.context
        if (context.disabled) return
        try {
            AuditEventType type = AuditEventType.forEventType(event.eventType)
            Auditable domain = (Auditable) event.entityObject
            if (!domain.isAuditable(type) || domain.logIgnoreEvents?.contains(type)) return
            boolean verbose = context.verbose || domain.logVerboseEvents?.contains(type)
            if (event.eventType == EventType.PreUpdate) {
                Map<String, Object> newMap = [:], oldMap = [:]
                if (verbose) {
                    Collection<String> dirty = domain.auditableDirtyPropertyNames
                    oldMap = dirty.collectEntries { String p -> [p, originalValue(domain, p)] }
                    newMap = values(dirty, domain)
                }
                if (newMap || oldMap || !verbose) logChanges(domain, newMap, oldMap, type)
            } else {
                Map<String, Object> map = verbose ? values(domain.auditablePropertyNames, domain) : [:]
                if (map || !verbose) {
                    if (type == AuditEventType.DELETE) {
                        logChanges(domain, [:], map.findAll { k, v -> !(v instanceof Collection) }, type)
                    } else {
                        logChanges(domain, map, [:], type)
                    }
                }
            }
        } catch (Exception e) {
            if (context.failOnError) throw e
            log.error("Erro ao auditar ${event.eventType} de ${event.entityObject}", e)
        }
    }

    private static Map<String, Object> values(Collection<String> names, Object domain) {
        names.collectEntries { String n -> [n, domain.metaClass.getProperty(domain, n)] }
    }

    private static Object originalValue(Object domain, String property) {
        DomainProperty p = Gorm.domain(domain.getClass()).persistentProperties[property]
        p?.collection ? 'N/A' : domain.invokeMethod('getPersistentValue', property)
    }

    private void logChanges(Auditable domain, Map<String, Object> newMap, Map<String, Object> oldMap, AuditEventType type) {
        Long version = versionOf(domain, newMap, oldMap)
        Map base = [actor: domain.logCurrentUserName, uri: domain.logURI, className: domain.logClassName, eventName: type.name(),
                    persistedObjectId: domain.logEntityId, persistedObjectVersion: version]
        if (!newMap && !oldMap) {
            queue(domain, base)
            return
        }
        (newMap.keySet() + oldMap.keySet()).each { String property ->
            Object newVal = newMap[property], oldVal = oldMap[property]
            String newStr = newVal != null ? maskAndTruncate(domain, property, domain.convertLoggedPropertyToString(property, newVal)) : null
            String oldStr = newVal != oldVal ? maskAndTruncate(domain, property, domain.convertLoggedPropertyToString(property, oldVal)) : null
            queue(domain, base + [propertyName: property, oldValue: oldStr, newValue: newStr])
        }
    }

    private static void queue(Auditable domain, Map params) {
        Object audit = AuditLogPlugin.auditDomainClass.getDeclaredConstructor().newInstance()
        params.each { k, v -> if (audit.hasProperty((String) k)) audit[(String) k] = v }
        if (domain.beforeSaveLog(audit)) AuditLogPlugin.enqueue(audit, AuditLogPlugin.tenantFor(domain))
    }

    private static Long versionOf(Object domain, Map newMap, Map oldMap) {
        Object v = newMap?.version ?: oldMap?.version
        DomainClass d = Gorm.domain(domain.getClass())
        if (!v && d.version) v = d.version.get(domain)
        v as Long
    }

    private static String maskAndTruncate(Auditable domain, String property, String value) {
        if (value == null) return null
        value = value.trim()
        if (domain.logMaskProperties?.contains(property)) return (AuditLogContext.context.propertyMask ?: '********') as String
        int max = AuditLogPlugin.truncateLength
        value.length() > max ? value.substring(0, max) : value
    }
}
