package org.grox.plugins.auditlog

/** {@code grails.plugin.auditLog.*} sobre os padrões do plugin audit-logging (DefaultAuditLogConfig). */
class AuditLogConfig {

    static final Map<String, Object> DEFAULTS = [
            auditDomainClassName            : null,
            disabled                        : false,
            verbose                         : true,
            failOnError                     : false,
            logIds                          : true,
            logFullClassName                : true,
            excluded                        : ['version', 'lastUpdated', 'lastUpdatedBy'],
            included                        : null,
            mask                            : ['password'],
            propertyMask                    : '**********',
            defaultActor                    : 'SYS',
            usePersistentDirtyPropertyValues: true,
            stampEnabled                    : true,
            asyncWrite                      : false,
            asyncWriteThreads               : 2,
            asyncWriteQueueCapacity         : 10000,
    ].asImmutable()

    /** Configuração efetiva (padrões se o plugin não estiver ativo). */
    static volatile Map<String, Object> current = DEFAULTS

    static Map<String, Object> merge(Map yaml) {
        Map<String, Object> m = new LinkedHashMap<>(DEFAULTS)
        yaml?.each { k, v -> m[k.toString()] = v }
        m.asImmutable()
    }
}
