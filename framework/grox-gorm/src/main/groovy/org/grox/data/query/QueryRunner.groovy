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

/** Executa critérios, ordenação, paginação e projeções via JPA Criteria. */
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

    List list() {
        Persistence.withSession { Session s ->
            api.guard(s)
            CriteriaBuilder cb = s.criteriaBuilder
            if (projections) return projected(s, cb)
            CriteriaQuery q = cb.createQuery(api.type)
            Root root = q.from(api.type)
            def qc = new QueryContext(cb, root, api.domain)
            q.select(root)
            if (distinct) q.distinct(true)
            if (criteria) q.where(Where.and(criteria as Criterion[]).toPredicate(qc))
            List<Order> os = ordering(qc)
            if (os) q.orderBy(os)
            else defaultOrder(qc, q)
            paginate(s.createQuery(q)).resultList
        }
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

    long count() {
        Persistence.withSession { Session s ->
            api.guard(s)
            CriteriaBuilder cb = s.criteriaBuilder
            CriteriaQuery<Long> q = cb.createQuery(Long)
            Root root = q.from(api.type)
            def qc = new QueryContext(cb, root, api.domain)
            q.select(distinct ? cb.countDistinct(root) : cb.count(root))
            if (criteria) q.where(Where.and(criteria as Criterion[]).toPredicate(qc))
            s.createQuery(q).singleResult
        }
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
