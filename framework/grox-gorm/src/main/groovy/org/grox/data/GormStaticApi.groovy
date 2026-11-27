package org.grox.data

import org.grox.data.query.Criterion
import org.grox.data.query.HqlQuery
import org.grox.data.query.Where
import org.grox.data.tenant.TenantSupport
import org.grox.data.binding.DataBinder
import org.hibernate.Session
import org.hibernate.query.SelectionQuery

/** Métodos estáticos do GORM de uma domain class ({@code Usuario.get(1)}, {@code list()}...). */
class GormStaticApi {

    final DomainClass domain
    final Class type

    GormStaticApi(DomainClass domain) {
        this.domain = domain
        this.type = domain.type
    }

    Object convertId(Object id) {
        if (id == null) return null
        // chave composta: o id é uma instância da própria entidade com as partes da chave preenchidas
        if (domain.composite) return type.isInstance(id) ? id : (id instanceof Map ? type.newInstance((Map) id) : id)
        Class idType = domain.identity.type
        if (idType.isInstance(id)) return id
        if (id instanceof Number && Number.isAssignableFrom(idType)) return DataBinder.convert(id.toString(), idType)
        DataBinder.convert(id.toString(), idType)
    }

    /** Classes multi-tenant exigem um tenant resolvido (ou withoutId). */
    void guard(Session s) {
        if (domain.multiTenant) TenantSupport.requireTenant(s)
    }

    Object get(Object id) {
        Object key = convertId(id)
        key == null ? null : Persistence.withSession { Session s -> guard(s); s.find(type, key) }
    }

    Object load(Object id) {
        Object key = convertId(id)
        key == null ? null : Persistence.withSession { Session s -> guard(s); s.getReference(type, key) }
    }

    /** Um {@code count} pelo id, sem carregar a entidade (nem as associações EAGER), como no GORM. */
    boolean exists(Object id) {
        if (domain.composite) return get(id) != null
        Object key = convertId(id)
        key != null && ((Long) query([:], true, false, Where.eq(domain.identity.name, key))) > 0
    }

    /** Uma consulta ({@code id in (...)}) na ordem dos ids, com {@code null} para os inexistentes, como no GORM. */
    List getAll(Collection ids) {
        if (!ids) return []
        if (domain.composite) return ids.collect { get(it) }
        List keys = ids.collect { convertId(it) }
        List distinct = keys.findAll { it != null }.unique()
        Map<Object, Object> byId = [:]
        if (distinct) ((List) query([:], false, false, Where.restriction('in', domain.identity.name, distinct))).each { byId[domain.identity.get(it)] = it }
        keys.collect { it == null ? null : byId[it] }
    }

    /** Com {@code max}, um {@code PagedResultList} ({@code totalCount}), como no GORM. */
    List list(Map params = [:]) {
        List page = (List) query(params, false, false, null)
        params?.max != null ? new grails.gorm.PagedResultList(page, { count() }) : page
    }

    long count() {
        (long) query([:], true, false, null)
    }

    private String defaultSort() {
        domain.composite ? domain.compositeId[0] + '.id' : domain.identity.name
    }

    Object first(String sort = null) {
        first(sort ? [sort: sort] : [:])
    }

    /** {@code first([sort: 'nome', readOnly: true])}, como no GORM. */
    Object first(Map args) {
        Object r = list((args ?: [:]) + [max: 1, sort: args?.sort ?: defaultSort(), order: 'asc'])
        r ? ((List) r)[0] : null
    }

    Object last(String sort = null) {
        last(sort ? [sort: sort] : [:])
    }

    Object last(Map args) {
        Object r = list((args ?: [:]) + [max: 1, sort: args?.sort ?: defaultSort(), order: 'desc'])
        r ? ((List) r)[0] : null
    }

    List findAllWhere(Map criteria, Map params = [:]) {
        (List) query(params, false, false, where(criteria))
    }

    Object findWhere(Map criteria) {
        query([:], false, true, where(criteria))
    }

    private static Criterion where(Map criteria) {
        Where.and(criteria.collect { k, v -> Where.eq(k.toString(), v) } as Criterion[])
    }

    /**
     * Consulta em HQL gerado ({@link HqlQuery}): {@code params} com max, offset, sort, order; {@code restriction}
     * opcional. A mesma forma de consulta gera a mesma string, e o Hibernate reaproveita o plano.
     */
    @groovy.transform.CompileStatic
    Object query(Map params, boolean count, boolean unique, Criterion restriction) {
        Persistence.withSession { Session s ->
            guard(s)
            HqlQuery h = new HqlQuery(domain)
            String where = restriction?.toHql(h)
            String root = HqlQuery.entityName(s, type) + ' ' + HqlQuery.ROOT
            if (count) {
                def q = s.createSelectionQuery('select count(' + HqlQuery.ROOT + ') from ' + root + h.joinClause + (where ? ' where ' + where : ''), Long)
                h.bind(q)
                return (Object) (q.uniqueResult() ?: 0L)
            }
            String order = ordering(h, params)
            def q = s.createSelectionQuery('select ' + HqlQuery.ROOT + ' from ' + root + h.joinClause + (where ? ' where ' + where : '') +
                    (order ? ' order by ' + order : ''), type)
            h.bind(q)
            // offset negativo (links de paginação): 0, como o Hibernate 5 tolerava
            Object offset = params?.get('offset')
            if (offset) q.firstResult = Math.max(0, offset as int)
            Object max = params?.get('max')
            if (unique) q.maxResults = 1
            else if (max) q.maxResults = max as int
            List result = q.resultList
            (Object) (unique ? (result ? result[0] : null) : result)
        }
    }

    /** {@code sort}/{@code order} dos params (só propriedades da domain) ou o {@code sort} do mapping. */
    @groovy.transform.CompileStatic
    private String ordering(HqlQuery h, Map params) {
        Object sort = params?.get('sort')
        String order = params?.get('order')?.toString()
        if (sort && !domain.hasPersistentProperty(sort.toString())) sort = null
        if (!sort) {
            Object configured = domain.mapping.settings.get('sort')
            if (configured instanceof Map && configured) {
                Map.Entry e = ((Map) configured).entrySet().first()
                sort = e.key
                order = order ?: e.value?.toString()
            } else {
                sort = configured
            }
        }
        if (!sort) return null
        String prop = sort.toString()
        if (!domain.hasPersistentProperty(prop)) return null
        h.orderPath(prop) + (order?.toLowerCase() == 'desc' ? ' desc' : ' asc')
    }

    // --- HQL ---

    /** Chaves do mapa de parâmetros que o GORM trata como opções da consulta, não parâmetros nomeados. */
    static final Set<String> QUERY_OPTIONS = ['max', 'offset', 'sort', 'order', 'readOnly', 'cache', 'fetchSize', 'timeout', 'flushMode', 'lock'] as Set

    /** {@code executeQuery(hql, [nome: x, max: 10])}: separa as opções (max, offset...) dos parâmetros, como o GORM. */
    private static List splitOptions(Object params, Map pagination) {
        if (!(params instanceof Map)) return [params, pagination]
        Map p = (Map) params
        Map opts = p.findAll { k, v -> k.toString() in QUERY_OPTIONS }
        if (!opts) return [params, pagination]
        [p.findAll { k, v -> !(k.toString() in QUERY_OPTIONS) }, (pagination ?: [:]) + opts]
    }

    List executeQuery(String hql, Object params = null, Map pagination = [:]) {
        (params, pagination) = splitOptions(params, pagination)
        Persistence.withSession { Session s ->
            guard(s)
            SelectionQuery q = s.createSelectionQuery(ordinals(hql), Object)
            bindParams(q, params)
            paginate(q, pagination)
            q.resultList
        }
    }

    List findAll(String hql, Object params = null, Map pagination = [:]) {
        (params, pagination) = splitOptions(params, pagination)
        Persistence.withSession { Session s ->
            guard(s)
            SelectionQuery q = s.createSelectionQuery(ordinals(hql), type)
            bindParams(q, params)
            paginate(q, pagination)
            q.resultList
        }
    }

    Object find(String hql, Object params = null) {
        List r = findAll(hql, params, [max: 1])
        r ? r[0] : null
    }

    int executeUpdate(String hql, Object params = null) {
        Persistence.withTransaction {
            guard(Persistence.currentSession())
            def q = Persistence.currentSession().createMutationQuery(ordinals(hql))
            bindParams(q, params)
            try {
                q.executeUpdate()
            } catch (RuntimeException e) {
                throw Persistence.translate(e)
            }
        }
    }

    private static final java.util.regex.Pattern ORDINAL = ~/\?(\d+)/
    private static final java.util.regex.Pattern JDBC_STYLE = ~/\?(?!\d)/

    /**
     * Parâmetros posicionais herdados do Hibernate 5 (Grails ≤ 5), que o Hibernate 7 recusa: {@code ?0, ?1...}
     * (base zero) viram {@code ?1, ?2...} e {@code ?} sem número vira {@code ?1, ?2...} na ordem.
     * Strings entre aspas não são alteradas.
     */
    static String ordinals(String hql) {
        if (hql == null || hql.indexOf('?') < 0) return hql
        String cached = ORDINALS_CACHE.get(hql)
        if (cached != null) return cached
        String converted = convertOrdinals(hql)
        if (ORDINALS_CACHE.size() < 2000) ORDINALS_CACHE.put(hql, converted)   // HQL montado com valores não enche a memória
        converted
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> ORDINALS_CACHE = new java.util.concurrent.ConcurrentHashMap<>()
    private static final java.util.regex.Pattern ZERO_BASED = ~/\?0(?!\d)/

    private static String convertOrdinals(String hql) {
        boolean zeroBased = ZERO_BASED.matcher(hql).find()
        int next = 0
        StringBuilder out = new StringBuilder()
        // alterna trechos fora/dentro de aspas simples
        hql.split(/(?=')|(?<=')/).inject(false) { boolean quoted, String part ->
            if (part == "'") {
                out << part
                return !quoted
            }
            if (quoted) {
                out << part
            } else {
                String p = zeroBased ? part.replaceAll(ORDINAL) { all, n -> "?${(n as int) + 1}" } : part
                out << p.replaceAll(JDBC_STYLE) { "?${++next}" }
            }
            quoted
        }
        out.toString()
    }

    private static void bindParams(Object q, Object params) {
        if (params instanceof Map) ((Map) params).each { k, v -> q.setParameter(k.toString(), value(v)) }
        else if (params instanceof Collection) ((Collection) params).eachWithIndex { v, int i -> q.setParameter(i + 1, value(v)) }
        else if (params instanceof Object[]) ((Object[]) params).eachWithIndex { v, int i -> q.setParameter(i + 1, value(v)) }
        // valor solto, como no GORM: executeQuery('... where x.id = ?0', id)
        else if (params != null) q.setParameter(1, value(params))
    }

    /** GString vira String (também dentro de listas para {@code in (:lista)}), como no GORM; o Hibernate 7 recusa GString. */
    private static Object value(Object v) {
        if (v instanceof GString) return v.toString()
        if (v instanceof Collection && ((Collection) v).any { it instanceof GString }) return ((Collection) v).collect { it instanceof GString ? it.toString() : it }
        v
    }

    /**
     * {@code max}/{@code offset} e as opções do GORM. Antes só a paginação era aplicada: {@code lock: true} rodava
     * sem {@code SELECT ... FOR UPDATE} (atualizações concorrentes se perdiam).
     */
    private static void paginate(SelectionQuery q, Map p) {
        if (!p) return
        if (p.offset) q.firstResult = Math.max(0, p.offset as int)
        if (p.max) q.maxResults = p.max as int
        if (p.lock) q.lockMode = jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
        if (p.readOnly != null) q.readOnly = p.readOnly as boolean
        if (p.cache != null) q.cacheable = p.cache as boolean
        if (p.fetchSize) q.fetchSize = p.fetchSize as int
        if (p.timeout) q.timeout = p.timeout as int
        if (p.flushMode) q.queryFlushMode = p.flushMode.toString().equalsIgnoreCase('commit') || p.flushMode.toString().equalsIgnoreCase('manual') ?
                org.hibernate.query.QueryFlushMode.NO_FLUSH : org.hibernate.query.QueryFlushMode.FLUSH
    }

    // --- dynamic finders ---

    Object invokeDynamic(String name, Object[] args) {
        if (name.startsWith('listOrderBy') && name.length() > 11) {
            Map params = args && args[0] instanceof Map ? new LinkedHashMap((Map) args[0]) : [:]
            params.sort = name.substring(11).uncapitalize()
            return list(params)
        }
        DynamicFinder.Plan plan = DynamicFinder.plan(domain, name)
        if (plan == null) throw new MissingMethodException(name, type, args, true)
        DynamicFinder.execute(this, plan, args)
    }
}
