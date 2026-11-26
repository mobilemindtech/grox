package org.grox.data

import org.grox.core.ClassUtils
import org.grox.data.validation.Validator
import org.hibernate.FlushMode
import org.hibernate.Session

import java.util.concurrent.ConcurrentHashMap

/** Registro das domain classes e das APIs do GORM (uma aplicação por JVM). */
class Gorm {

    private static final Map<Class, GormStaticApi> APIS = new ConcurrentHashMap<>()

    /** Filtros do hibernate-filter ligados por padrão (para withoutHibernateFilters). */
    static Set<String> hibernateFilterDefaults() {
        Persistence.datastore ? HibernateFilters.defaults(Persistence.datastore.domains.values()) : ([] as Set)
    }

    static void init(Datastore datastore) {
        Persistence.datastore = datastore
        APIS.clear()
        datastore.domains.values().each { APIS[it.type] = new GormStaticApi(it) }
        Validator.uniqueChecker = { Object target, String prop, Object value, List<String> scope ->
            GormStaticApi api = api(target.getClass())
            Map criteria = [(prop): value]
            scope.each { criteria[it] = target[it] }
            if (api.domain.composite) return true   // unique em entidade de chave composta: a própria PK garante
            Object id = api.domain.identity.get(target)
            Persistence.withSession { Session s ->
                // sem auto-flush: a consulta não pode gravar alterações ainda não validadas
                FlushMode previous = s.hibernateFlushMode
                s.hibernateFlushMode = FlushMode.MANUAL
                try {
                    List found = api.findAllWhere(criteria, [max: 2])
                    found.every { api.domain.identity.get(it) == id && id != null }
                } finally {
                    s.hibernateFlushMode = previous
                }
            }
        } as Validator.UniqueChecker
    }

    static void reset() {
        Persistence.datastore?.close()
        Persistence.datastore = null
        APIS.clear()
        Validator.uniqueChecker = null
    }

    static boolean isDomain(Class<?> type) {
        type != null && APIS.containsKey(ClassUtils.userClass(type))
    }

    static GormStaticApi api(Class<?> type) {
        GormStaticApi a = APIS.get(ClassUtils.userClass(type))
        if (a == null) {
            if (Persistence.datastore == null) {
                throw new IllegalStateException("GORM não inicializado: ${type.name} só pode ser usada com a aplicação iniciada (dataSource)")
            }
            throw new IllegalStateException("${type.name} não é uma domain class registrada")
        }
        a
    }

    static DomainClass domain(Class<?> type) {
        api(type).domain
    }

    static Collection<DomainClass> getDomains() {
        APIS.values()*.domain
    }

    static Session getCurrentSession() {
        Persistence.currentSession()
    }
}
