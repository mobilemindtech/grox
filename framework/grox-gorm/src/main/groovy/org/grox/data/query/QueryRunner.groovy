package org.grox.data.query

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Root
import org.grox.data.GormStaticApi
import org.grox.data.Persistence
import org.hibernate.Session

/**
 * Executa critérios, ordenação, paginação e projeções. {@code list}, {@code count} e {@code unique} usam HQL gerado
 * da árvore de critérios ({@link HqlQuery}): a mesma forma de consulta gera a mesma string, e o Hibernate reaproveita
 * o plano traduzido. Projeções, {@code deleteAll} e {@code updateAll} usam a JPA Criteria.
 */
class QueryRunner {

    final GormStaticApi api
    List<Criterion> criteria = []
    List<List<String>> orders = []
    Integer max
    Integer offset
    List<Projection> projections = []
    boolean distinct

    QueryRunner(GormStaticApi api) {
        this.api = api
    }

    /** max/offset/sort/order de um Map de parâmetros (params de requisição). */
    QueryRunner params(Map p) {
        if (p?.max) max = p.max as int
        if (p?.offset) offset = p.offset as int
        if (p?.sort) {
            String sort = p.sort.toString()
            // só caminhos conhecidos, inteiros (params.sort vem do usuário): o inválido é ignorado, como no list(params)
            if (QueryContext.isValidPath(api.domain, sort)) orders = [[sort, p.order?.toString() == 'desc' ? 'desc' : 'asc']]
        }
        this
    }

    @groovy.transform.CompileStatic
    List list() {
        Persistence.withSession { Session s ->
            api.guard(s)
            if (!projections.isEmpty()) return projected(s, s.criteriaBuilder)
            selectList(s)
        }
    }

    @groovy.transform.CompileStatic
    private List selectList(Session s) {
        HqlQuery h = new HqlQuery(api.domain)
        String where = whereHql(h)
        String order = orderHql(h)
        String hql = 'select ' + (distinct ? 'distinct ' : '') + HqlQuery.ROOT + ' from ' + entityName(s) + ' ' + HqlQuery.ROOT +
                h.joinClause + (where ? ' where ' + where : '') + (order ? ' order by ' + order : '')
        def q = s.createSelectionQuery(hql, api.type)
        h.bind(q)
        if (offset != null && offset > 0) q.firstResult = offset    // negativo: 0, como no GORM
        if (max) q.maxResults = max
        q.resultList
    }

    @groovy.transform.CompileStatic
    private String whereHql(HqlQuery h) {
        criteria.isEmpty() ? null : Where.and(criteria.toArray(new Criterion[0])).toHql(h)
    }

    /** {@code sort}/{@code order} pedidos ou, sem eles, o {@code sort} do mapping. */
    @groovy.transform.CompileStatic
    private String orderHql(HqlQuery h) {
        if (orders) return orders.collect { List<String> o -> h.orderPath(o[0]) + (o[1] == 'desc' ? ' desc' : ' asc') }.join(', ')
        Object configured = api.domain.mapping.settings.get('sort')
        if (configured instanceof Map && configured) {
            Map.Entry e = ((Map) configured).entrySet().first()
            return h.orderPath(e.key.toString()) + (e.value?.toString() == 'desc' ? ' desc' : ' asc')
        }
        configured ? h.orderPath(configured.toString()) + ' asc' : null
    }

    @groovy.transform.CompileStatic
    private String entityName(Session s) {
        HqlQuery.entityName(s, api.type)
    }

    /** {@code list(params)} do GORM: com {@code max} (sem projeções), um {@code PagedResultList} com o {@code totalCount}. */
    List list(Map params) {
        List page = list()
        params?.max != null && !projections ? new grails.gorm.PagedResultList(page, { count() }) : page
    }

    Object unique() {
        max = 1
        List r = list()
        r ? r[0] : null
    }

    @groovy.transform.CompileStatic
    long count() {
        (long) Persistence.withSession { Session s ->
            api.guard(s)
            countHql(s)
        }
    }

    @groovy.transform.CompileStatic
    private long countHql(Session s) {
        HqlQuery h = new HqlQuery(api.domain)
        String where = whereHql(h)
        String hql = 'select ' + (distinct ? 'count(distinct ' + HqlQuery.ROOT + ')' : 'count(' + HqlQuery.ROOT + ')') + ' from ' +
                entityName(s) + ' ' + HqlQuery.ROOT + h.joinClause + (where ? ' where ' + where : '')
        def q = s.createSelectionQuery(hql, Long)
        h.bind(q)
        q.uniqueResult() ?: 0L
    }

    int deleteAll() {
        Persistence.withTransaction {
            Session s = Persistence.currentSession()
            api.guard(s)
            CriteriaBuilder cb = s.criteriaBuilder
            CriteriaDelete d = cb.createCriteriaDelete(api.type)
            Root root = d.from(api.type)
            if (criteria) d.where(Where.and(criteria as Criterion[]).toPredicate(new QueryContext(cb, root, api.domain)))
            s.createMutationQuery(d).executeUpdate()
        }
    }

    int updateAll(Map values) {
        Persistence.withTransaction {
            Session s = Persistence.currentSession()
            api.guard(s)
            CriteriaBuilder cb = s.criteriaBuilder
            CriteriaUpdate u = cb.createCriteriaUpdate(api.type)
            Root root = u.from(api.type)
            def qc = new QueryContext(cb, root, api.domain)
            values.each { k, v -> u.set(root.get(k.toString()), qc.convert(k.toString(), v)) }
            if (criteria) u.where(Where.and(criteria as Criterion[]).toPredicate(qc))
            s.createMutationQuery(u).executeUpdate()
        }
    }

    private List projected(Session s, CriteriaBuilder cb) {
        CriteriaQuery<Object[]> q = cb.createQuery(Object[])
        Root root = q.from(api.type)
        def qc = new QueryContext(cb, root, api.domain)
        List<Expression> selections = []
        List<Expression> groups = []
        projections.each { Projection p ->
            Expression e = switch (p.type) {
                case 'property', 'distinct' -> qc.path(p.path)
                case 'groupProperty' -> { def path = qc.path(p.path); groups << path; yield path }
                case 'count' -> p.path ? cb.count(qc.path(p.path)) : cb.count(root)
                case 'rowCount' -> cb.count(root)
                case 'countDistinct' -> cb.countDistinct(qc.path(p.path))
                case 'sum' -> cb.sum((Expression<Number>) qc.path(p.path))
                case 'avg' -> cb.avg((Expression<Number>) qc.path(p.path))
                case 'min' -> cb.least((Expression<Comparable>) qc.path(p.path))
                case 'max' -> cb.greatest((Expression<Comparable>) qc.path(p.path))
                default -> throw new IllegalArgumentException("projeção desconhecida: ${p.type}")
            }
            selections << e
            if (p.type == 'distinct') q.distinct(true)
        }
        q.multiselect(selections)
        if (criteria) q.where(Where.and(criteria as Criterion[]).toPredicate(qc))
        if (groups) q.groupBy(groups)
        List<Order> os = ordering(qc)
        if (os) q.orderBy(os)
        List<Object[]> rows = paginate(s.createQuery(q)).resultList
        selections.size() == 1 ? rows.collect { it instanceof Object[] ? ((Object[]) it)[0] : it } : rows.collect { it as List }
    }

    private List<Order> ordering(QueryContext qc) {
        orders.collect { List<String> o -> o[1] == 'desc' ? qc.cb.desc(qc.orderPath(o[0])) : qc.cb.asc(qc.orderPath(o[0])) }
    }

    /** Sem ordenação explícita, usa o {@code sort} do mapping. */
    private void defaultOrder(QueryContext qc, CriteriaQuery q) {
        Object configured = api.domain.mapping.settings.sort
        if (configured instanceof Map && configured) {
            Map.Entry e = ((Map) configured).entrySet().first()
            q.orderBy(e.value?.toString() == 'desc' ? qc.cb.desc(qc.orderPath(e.key.toString())) : qc.cb.asc(qc.orderPath(e.key.toString())))
        } else if (configured) {
            q.orderBy(qc.cb.asc(qc.orderPath(configured.toString())))
        }
    }

    private <Q> Q paginate(Q query) {
        if (offset > 0) query.firstResult = offset    // negativo: 0, como no GORM
        if (max) query.maxResults = max
        query
    }
}
