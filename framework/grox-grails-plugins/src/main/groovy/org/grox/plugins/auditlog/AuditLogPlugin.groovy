package org.grox.plugins.auditlog

import grails.plugins.orm.auditable.resolvers.AuditRequestResolver
import org.grox.data.DomainClass
import org.grox.data.Gorm
import org.grox.data.Persistence
import org.hibernate.Session
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Plugin audit-logging no Grox: ativado quando {@code grails.plugin.auditLog.auditDomainClassName}
 * está configurado. Registra o {@link AuditLogListener} e grava os registros de auditoria depois do
 * commit da transação (numa sessão nova), no próprio fluxo ou, com {@code asyncWrite}, num executor.
 */
class AuditLogPlugin {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPlugin)

    static volatile AuditRequestResolver resolver
    static volatile Class<?> auditDomainClass
    static volatile int truncateLength = Integer.MAX_VALUE
    static volatile ExecutorService executor
    /** Registros pendentes por transação do Hibernate, gravados depois do commit. */
    private static final Map<Object, List<Object>> PENDING = new ConcurrentHashMap<>()

    /** Configura a partir de {@code grails.plugin.auditLog.*}; devolve false se o plugin não estiver ativo. */
    static boolean init(Map yaml, ClassLoader loader, AuditRequestResolver requestResolver) {
        shutdown()
        Map<String, Object> config = AuditLogConfig.merge(yaml)
        AuditLogConfig.current = config
        String className = config.auditDomainClassName
        if (!className) return false
        auditDomainClass = Class.forName(className, true, loader)
        if (!Gorm.isDomain(auditDomainClass)) throw new IllegalArgumentException("auditDomainClassName $className não é uma domain class")
        DomainClass d = Gorm.domain(auditDomainClass)
        Map<String, Map<String, Object>> c = d.effectiveConstraints()
        int max = Math.min((c.oldValue?.maxSize ?: 255) as int, (c.newValue?.maxSize ?: 255) as int)
        truncateLength = Math.min(max, (config.truncateLength ?: Integer.MAX_VALUE) as int)
        resolver = requestResolver
        if (config.asyncWrite) {
            int threads = config.asyncWriteThreads as int
            executor = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(config.asyncWriteQueueCapacity as int),
                    Thread.ofVirtual().name('audit-log-', 0).factory(), new ThreadPoolExecutor.CallerRunsPolicy())
        }
        log.info("Audit log ativo: ${className}${executor ? ' (gravação assíncrona)' : ''}")
        true
    }

    static void shutdown() {
        executor?.shutdown()
        executor?.awaitTermination(10, TimeUnit.SECONDS)
        executor = null
        auditDomainClass = null
        resolver = null
        AuditLogConfig.current = AuditLogConfig.DEFAULTS
    }

    /**
     * O tenant em que o registro de auditoria é gravado (só se a domain de auditoria for multi-tenant): o da entidade
     * auditada, ou o atual. Capturado na hora do evento: a gravação é depois do commit, numa sessão nova, e com
     * {@code asyncWrite} noutra thread (sem o contexto da requisição); dentro de {@code Tenants.withoutId}, o
     * registro ficaria sem tenant e não seria gravado.
     */
    static Object tenantFor(Object audited) {
        Class<?> auditClass = auditDomainClass
        if (auditClass == null || !Gorm.domain(auditClass).multiTenant) return null
        DomainClass d = Gorm.domain(audited.getClass())
        Object own = d.multiTenant ? d.tenantProperty?.get(audited) : null
        own != null ? own : grails.gorm.multitenancy.Tenants.currentIdOrNull()
    }

    /** Enfileira o registro na transação corrente (gravado após o commit) ou grava já, se não houver transação. */
    static void enqueue(Object audit, Object tenant = null) {
        Object[] entry = [audit, tenant] as Object[]
        Session s = Persistence.currentHolder()?.session
        if (s == null || !s.transaction.active) {
            write([entry])
            return
        }
        def tx = s.transaction
        List<Object> pending = PENDING.get(tx)
        if (pending == null) {
            pending = Collections.synchronizedList(new ArrayList<>())
            PENDING.put(tx, pending)
            tx.registerSynchronization(new jakarta.transaction.Synchronization() {
                @Override
                void beforeCompletion() {}

                @Override
                void afterCompletion(int status) {
                    List<Object> done = PENDING.remove(tx)
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED && done) afterCommit(new ArrayList<>(done))
                }
            })
        }
        pending << entry
    }

    private static void afterCommit(List<Object> audits) {
        ExecutorService ex = executor
        if (ex == null) {
            write(audits)
            return
        }
        try {
            ex.execute {
                try {
                    write(audits)
                } catch (Throwable e) {
                    log.error("Falha ao gravar ${audits.size()} registro(s) de auditoria", e)
                }
            }
        } catch (RejectedExecutionException e) {
            write(audits)
        }
    }

    /** Numa sessão nova (a da requisição pode estar no fim do commit, ou ser de outra thread), no tenant capturado. */
    static void write(List<Object> entries) {
        entries.groupBy { Object e -> ((Object[]) e)[1] }.each { Object tenant, List<Object> group ->
            Closure save = {
                Persistence.withNewTransaction {
                    group.each { Object e -> ((Object[]) e)[0].invokeMethod('save', [[failOnError: true]] as Object[]) }
                }
            }
            tenant != null ? grails.gorm.multitenancy.Tenants.withId((Serializable) tenant, save) : save.call()
        }
    }
}
