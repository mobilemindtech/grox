package org.grox.data

import org.hibernate.Session
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Plugin hibernate-filter do Grails sobre os filtros nativos do Hibernate: {@code static hibernateFilters = {
 * ativoFilter(condition: 'ativo=1', default: true) }} na domain vira {@code <filter-def>} + {@code <filter>} no
 * orm.xml; filtros {@code default: true} ficam ligados em toda sessão ({@code auto-enabled}). Nas domains:
 * {@code withoutHibernateFilters { }}, {@code withoutHibernateFilter(nome) { }}, {@code withHibernateFilters { }},
 * {@code enableHibernateFilter(nome)} e {@code disableHibernateFilter(nome)}.
 */
class HibernateFilters {

    private static final Logger log = LoggerFactory.getLogger(HibernateFilters)

    /** Filtro declarado numa domain. */
    static class Def {
        String name
        String condition
        boolean defaultEnabled
    }

    /** Lê {@code static hibernateFilters} (só filtros sem parâmetros sobre a própria entidade). */
    static List<Def> read(Class<?> type) {
        Object dsl = org.grox.core.ClassUtils.staticProperty(type, 'hibernateFilters')
        if (!(dsl instanceof Closure)) return []
        List<Def> defs = []
        def builder = new Object() {
            def methodMissing(String name, Object args) {
                Map opts = ((Object[]) args) && ((Object[]) args)[0] instanceof Map ? (Map) ((Object[]) args)[0] : [:]
                if (opts.collection || opts.types || opts.paramTypes || opts.aliasDomain) {
                    log.warn("${type.simpleName}.hibernateFilters.${name}: collection, parâmetros e aliasDomain ainda não são suportados (ignorado)")
                    return null
                }
                if (opts.default instanceof Closure) log.warn("${type.simpleName}.hibernateFilters.${name}: default com closure tratado como true")
                defs << new Def(name: HibernateFilters.checked(name), condition: opts.condition?.toString(), defaultEnabled: opts.default as boolean)
                null
            }
        }
        Closure c = (Closure) ((Closure) dsl).clone()
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.delegate = builder
        c.call()
        defs
    }

    /** Nomes dos filtros ligados por padrão (algum {@code default: true}). */
    static Set<String> defaults(Collection<DomainClass> domains) {
        domains.collectMany { it.hibernateFilters.findAll { it.defaultEnabled }*.name } as Set
    }

    /**
     * O {@code @TenantId} do Hibernate é um filtro de sessão ({@code _tenantId}): desligá-lo pela API do
     * hibernate-filter faria a sessão ver todos os tenants sem passar por {@code Tenants.withoutId}.
     */
    static String checked(String name) {
        if (name == org.hibernate.binder.internal.TenantIdBinder.FILTER_NAME) {
            throw new IllegalArgumentException("${name} é o filtro de multi-tenancy do Hibernate; use Tenants.withoutId/withId")
        }
        name
    }

    static <T> T without(Collection<String> names, Closure<T> body) {
        names.each { checked(it) }
        Persistence.withSession { Session s ->
            List<String> wereEnabled = names.findAll { s.getEnabledFilter(it) != null } as List<String>
            wereEnabled.each { s.disableFilter(it) }
            try {
                body.call()
            } finally {
                wereEnabled.each { s.enableFilter(it) }
            }
        }
    }

    static <T> T with(Collection<String> names, Closure<T> body) {
        names.each { checked(it) }
        Persistence.withSession { Session s ->
            List<String> wereDisabled = names.findAll { s.getEnabledFilter(it) == null } as List<String>
            wereDisabled.each { s.enableFilter(it) }
            try {
                body.call()
            } finally {
                wereDisabled.each { s.disableFilter(it) }
            }
        }
    }
}
