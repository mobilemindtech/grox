package org.grox.data.query

import org.grox.data.DomainClass
import org.grox.data.DomainProperty
import org.grox.data.Gorm

/**
 * {@code Livro.createCriteria().list(max: 10) { like('titulo', 'G%'); autor { eq('nome', 'X') };
 * or { ... }; order('titulo'); projections { count() } }} e {@code withCriteria { }}.
 */
class CriteriaBuilder {

    final Class type

    CriteriaBuilder(Class type) {
        this.type = type
    }

    List list(Closure body) { list([:], body) }

    List list(Map params, Closure body) { run(params, body).list(params) }

    List listDistinct(Closure body) {
        QueryRunner r = run([:], body)
        r.distinct = true
        r.list()
    }

    /** Resultado único (ou o valor da projeção, ex. {@code get { projections { count() } }}). */
    Object get(Closure body) { run([:], body).unique() }

    long count(Closure body) { run([:], body).count() }

    private QueryRunner run(Map params, Closure body) {
        def api = Gorm.api(type)
        def delegate = new Delegate(api.domain, '')
        Closure c = (Closure) body.clone()
        c.delegate = delegate
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
        def r = new QueryRunner(api)
        r.criteria = delegate.criteria
        r.orders = delegate.orders
        r.projections = delegate.projections
        r.max = delegate.maxResults
        r.offset = delegate.firstResult
        r.params(params)
        r
    }

    /** Métodos do DSL; {@code prefix} é o caminho da associação aninhada. */
    static class Delegate {
        final DomainClass domain
        final String prefix
        List<Criterion> criteria = []
        List<List<String>> orders = []
        List<Projection> projections = []
        Integer maxResults
        Integer firstResult
        /** {@code createAlias('cliente', '_cliente')}: alias → caminho (compartilhado com os blocos aninhados). */
        Map<String, String> aliases = [:]

        Delegate(DomainClass domain, String prefix) {
            this.domain = domain
            this.prefix = prefix
        }

        private String p(String name) {
            int dot = name.indexOf('.')
            if (dot > 0) {
                String path = aliases[name.substring(0, dot)]
                if (path != null) return path + name.substring(dot)
            }
            prefix + name
        }

        /** Como no GORM/Hibernate: {@code createAlias('servico', '_servico')} e depois {@code eq('_servico.nome', x)}. */
        void createAlias(String associationPath, String alias, int joinType = 0) {
            aliases[alias] = p(associationPath)
        }

        // projeções também valem fora de projections { }, como no GORM (ex.: groupProperty('id'))
        void groupProperty(String prop) { projections << new Projection(type: 'groupProperty', path: p(prop)) }

        void property(String prop) { projections << new Projection(type: 'property', path: p(prop)) }

        void sum(String prop) { projections << new Projection(type: 'sum', path: p(prop)) }

        void avg(String prop) { projections << new Projection(type: 'avg', path: p(prop)) }

        void rowCount() { projections << new Projection(type: 'rowCount') }

        void countDistinct(String prop) { projections << new Projection(type: 'countDistinct', path: p(prop)) }

        private void add(Criterion c) { criteria << c }

        void eq(String prop, Object v) { add(Where.eq(p(prop), v)) }

        void ne(String prop, Object v) { add(Where.ne(p(prop), v)) }

        void gt(String prop, Object v) { add(Where.gt(p(prop), v)) }

        void ge(String prop, Object v) { add(Where.ge(p(prop), v)) }

        void lt(String prop, Object v) { add(Where.lt(p(prop), v)) }

        void le(String prop, Object v) { add(Where.le(p(prop), v)) }

        // sinônimos do criteria do GORM
        void gte(String prop, Object v) { ge(prop, v) }

        void lte(String prop, Object v) { le(prop, v) }

        void like(String prop, Object v) { add(Where.like(p(prop), v)) }

        void ilike(String prop, Object v) { add(Where.ilike(p(prop), v)) }

        void between(String prop, Object a, Object b) { add(Where.between(p(prop), a, b)) }

        void 'in'(String prop, Object values) { add(Where.inList(p(prop), values)) }

        void inList(String prop, Object values) { add(Where.inList(p(prop), values)) }

        void notIn(String prop, Object values) { add(Where.notInList(p(prop), values)) }

        void isNull(String prop) { add(Where.isNull(p(prop))) }

        void isNotNull(String prop) { add(Where.isNotNull(p(prop))) }

        void isEmpty(String prop) { add(Where.isEmpty(p(prop))) }

        void isNotEmpty(String prop) { add(Where.isNotEmpty(p(prop))) }

        void idEq(Object id) {
            if (domain.composite) throw new UnsupportedOperationException("idEq em ${domain.name} (chave composta): use as partes da chave")
            add(Where.eq(p(domain.identity.name), id))
        }

        void eqProperty(String a, String b) { add(Where.eqProperty(p(a), p(b))) }

        void neProperty(String a, String b) { add(Where.neProperty(p(a), p(b))) }

        void gtProperty(String a, String b) { add(Where.gtProperty(p(a), p(b))) }

        void geProperty(String a, String b) { add(Where.geProperty(p(a), p(b))) }

        void ltProperty(String a, String b) { add(Where.ltProperty(p(a), p(b))) }

        void leProperty(String a, String b) { add(Where.leProperty(p(a), p(b))) }

        void and(Closure body) { add(Where.and(nested(body) as Criterion[])) }

        void or(Closure body) { add(Where.or(nested(body) as Criterion[])) }

        void not(Closure body) { add(Where.not(Where.and(nested(body) as Criterion[]))) }

        void order(String prop, String direction = 'asc') { orders << [p(prop), direction == 'desc' ? 'desc' : 'asc'] }

        void maxResults(int max) { maxResults = max }

        void firstResult(int first) { firstResult = first }

        void cache(boolean ignored) {}

        void projections(Closure body) {
            Closure c = (Closure) body.clone()
            c.delegate = new ProjectionDelegate(projections, domain, { String n -> p(n) })
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.call()
        }

        private List<Criterion> nested(Closure body) {
            def d = new Delegate(domain, prefix)
            d.aliases = aliases
            Closure c = (Closure) body.clone()
            c.delegate = d
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.call()
            orders.addAll(d.orders)
            d.criteria
        }

        /** Associação aninhada: {@code autor { eq('nome', 'X') }}. */
        def methodMissing(String name, Object args) {
            Object[] a = (Object[]) args
            DomainProperty prop = domain[name]
            if (a.length == 1 && a[0] instanceof Closure && prop?.association) {
                def d = new Delegate(Gorm.domain(prop.referencedType), prefix + name + '.')
                d.aliases = aliases
                Closure c = (Closure) ((Closure) a[0]).clone()
                c.delegate = d
                c.resolveStrategy = Closure.DELEGATE_FIRST
                c.call()
                criteria.addAll(d.criteria)
                orders.addAll(d.orders)
                projections.addAll(d.projections)
                return null
            }
            throw new MissingMethodException(name, CriteriaBuilder, a)
        }
    }

    static class ProjectionDelegate {
        final List<Projection> projections
        final DomainClass domain
        final Closure<String> resolve

        ProjectionDelegate(List<Projection> projections, DomainClass domain, Closure<String> resolve) {
            this.projections = projections
            this.domain = domain
            this.resolve = resolve
        }

        /** Associação dentro de projections: {@code projections { plano { groupProperty('descricao') } }}. */
        def methodMissing(String name, Object args) {
            Object[] a = (Object[]) args
            DomainProperty prop = domain[name]
            if (a.length == 1 && a[0] instanceof Closure && prop?.association) {
                Closure c = (Closure) ((Closure) a[0]).clone()
                c.delegate = new ProjectionDelegate(projections, Gorm.domain(prop.referencedType), { String n -> resolve.call(name + '.' + n) })
                c.resolveStrategy = Closure.DELEGATE_FIRST
                c.call()
                return null
            }
            throw new MissingMethodException(name, ProjectionDelegate, a)
        }

        private void add(String type, String path = null) { projections << new Projection(type: type, path: path ? resolve.call(path) : null) }

        void property(String p) { add('property', p) }

        void distinct(String p) { add('distinct', p) }

        void groupProperty(String p) { add('groupProperty', p) }

        void count(String p = null) { add('count', p) }

        void rowCount() { add('rowCount') }

        void countDistinct(String p) { add('countDistinct', p) }

        void sum(String p) { add('sum', p) }

        void avg(String p) { add('avg', p) }

        void min(String p) { add('min', p) }

        void max(String p) { add('max', p) }
    }
}
