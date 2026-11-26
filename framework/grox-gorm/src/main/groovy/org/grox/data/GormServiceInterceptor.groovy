package org.grox.data

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import org.grox.service.ServiceInterceptor
import org.springframework.transaction.annotation.Propagation

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Services do GORM:
 * <ul>
 *   <li>{@code @Transactional}/{@code @ReadOnly} (classe ou método; do GORM ou do Spring): chamada dentro de
 *       {@code withTransaction}; com {@code propagation = REQUIRES_NEW}, {@code withNewTransaction}
 *       (sessão e transação novas); SUPPORTS, NOT_SUPPORTED e NEVER não abrem transação;</li>
 *   <li>{@code @WithoutTenant}/{@code @CurrentTenant}/{@code @Tenant({ id })}, por fora da transação.</li>
 * </ul>
 */
class GormServiceInterceptor implements ServiceInterceptor {

    int getOrder() { 100 }

    @Override
    Map<String, Closure> interceptors(Class<?> type, Object target, Set<String> methods) {
        Map<String, Propagation> transactional = transactionalMethods(type, methods)
        Set<String> readOnly = readOnlyMethods(type, methods)
        Map<String, Object> tenancy = tenantModes(type, methods)
        Map<String, Closure> result = [:]
        (transactional.keySet() + tenancy.keySet()).each { String name ->
            Propagation propagation = transactional[name]
            Object mode = tenancy[name]
            result[name] = { Closure call ->
                boolean ro = name in readOnly
                Closure body = propagation == null ? call : { -> transaction(type, name, propagation, ro, call) }
                mode == null ? body.call() : withTenant(mode, target, body)
            }
        }
        result
    }

    private static Object transaction(Class<?> type, String name, Propagation propagation, boolean readOnly, Closure body) {
        if (!Persistence.available) return body.call()
        switch (propagation) {
            case Propagation.REQUIRES_NEW: return Persistence.withNewTransaction(body)
            case Propagation.SUPPORTS:
            case Propagation.NOT_SUPPORTED:
            case Propagation.NEVER: return body.call()
            case Propagation.MANDATORY:
                if (Persistence.currentHolder()?.status == null) {
                    throw new org.springframework.transaction.TransactionException("${type.simpleName}.${name}: propagação MANDATORY sem transação em andamento")
                }
                return body.call()
            default: return readOnly ? readOnlyTransaction(body) : Persistence.withTransaction(body)
        }
    }

    /**
     * {@code @ReadOnly} / {@code readOnly = true}, como o {@code HibernateTransactionManager}: a transação nova roda com
     * a sessão em {@code FlushMode.MANUAL} e as entidades carregadas como somente leitura, restaurados depois do
     * commit (alterações feitas nelas não vão ao banco). Participando de uma transação já aberta, vale o modo dela.
     */
    private static Object readOnlyTransaction(Closure body) {
        Persistence.withSession { org.hibernate.Session s ->
            if (s.transaction.active) return Persistence.withTransaction(body)
            org.hibernate.FlushMode mode = s.hibernateFlushMode
            boolean defaultReadOnly = s.defaultReadOnly
            s.hibernateFlushMode = org.hibernate.FlushMode.MANUAL
            s.defaultReadOnly = true
            try {
                return Persistence.withTransaction(body)
            } finally {
                s.hibernateFlushMode = mode
                s.defaultReadOnly = defaultReadOnly
            }
        }
    }

    private static Object withTenant(Object mode, Object target, Closure body) {
        if (mode == 'without') return Tenants.withoutId(body)
        if (mode == 'current') return Tenants.withCurrent(body)
        Closure tenantOf = (Closure) ((Class) mode).getDeclaredConstructor(Object, Object).newInstance(target, target)
        Tenants.withId((Serializable) tenantOf.call(), body)
    }

    /**
     * Métodos transacionais e a propagação de cada um: todos os públicos se a classe tem a anotação,
     * senão os anotados; a anotação do método prevalece sobre a da classe.
     */
    static Map<String, Propagation> transactionalMethods(Class<?> type, Set<String> methods) {
        Propagation classLevel = propagationOf(type)
        Set<String> declared = type.declaredMethods.findAll { Method m -> Modifier.isPublic(m.modifiers) }*.name as Set
        Map<String, Propagation> result = [:]
        methods.each { String n ->
            Method annotated = type.methods.find { it.name == n && propagationOf(it) != null }
            Propagation p = annotated ? propagationOf(annotated) : (n in declared ? classLevel : null)
            if (p != null) result[n] = p
        }
        result
    }

    /** Métodos com {@code @ReadOnly} ou {@code readOnly = true} (do método, senão da classe). */
    static Set<String> readOnlyMethods(Class<?> type, Set<String> methods) {
        Boolean classLevel = readOnlyOf(type)
        Set<String> declared = type.declaredMethods.findAll { Method m -> Modifier.isPublic(m.modifiers) }*.name as Set
        methods.findAll { String n ->
            Method annotated = type.methods.find { it.name == n && propagationOf(it) != null }
            annotated ? readOnlyOf(annotated) : (n in declared && classLevel)
        } as Set<String>
    }

    private static Boolean readOnlyOf(java.lang.reflect.AnnotatedElement e) {
        if (e.isAnnotationPresent(ReadOnly)) return true
        Transactional gorm = e.getAnnotation(Transactional)
        if (gorm) return gorm.readOnly()
        e.getAnnotation(org.springframework.transaction.annotation.Transactional)?.readOnly() ?: false
    }

    /** {@code @Transactional}/{@code @ReadOnly} do GORM ou {@code @Transactional} do Spring. */
    private static Propagation propagationOf(java.lang.reflect.AnnotatedElement e) {
        Transactional gorm = e.getAnnotation(Transactional)
        if (gorm) return gorm.propagation()
        if (e.isAnnotationPresent(ReadOnly)) return Propagation.REQUIRED
        e.getAnnotation(org.springframework.transaction.annotation.Transactional)?.propagation()
    }

    /** {@code @WithoutTenant} / {@code @CurrentTenant} / {@code @Tenant({ id })} (método ou classe). */
    static Map<String, Object> tenantModes(Class<?> type, Set<String> methods) {
        Object classMode = modeOf(type)
        Set<String> declared = type.declaredMethods.findAll { Method m -> Modifier.isPublic(m.modifiers) }*.name as Set
        Map<String, Object> result = [:]
        methods.each { String n ->
            Method annotated = type.methods.find { it.name == n && modeOf(it) != null }
            Object mode = annotated ? modeOf(annotated) : (n in declared ? classMode : null)
            if (mode != null) result[n] = mode
        }
        result
    }

    private static Object modeOf(java.lang.reflect.AnnotatedElement e) {
        if (e.isAnnotationPresent(WithoutTenant)) return 'without'
        if (e.isAnnotationPresent(CurrentTenant)) return 'current'
        e.getAnnotation(Tenant)?.value()
    }
}
