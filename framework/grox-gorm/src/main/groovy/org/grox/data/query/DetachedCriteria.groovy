package org.grox.data.query

import org.grox.data.Gorm

/**
 * Consulta de {@code Livro.where { titulo ==~ 'G%' && preco > 10 }} (imutável: cada
 * {@code where}, {@code max}, {@code sort} devolve uma cópia). Executada sob demanda.
 */
class DetachedCriteria<T> implements Iterable<T> {

    final Class<T> type
    final List<Criterion> criteria
    final Map<String, Object> options

    DetachedCriteria(Class<T> type, List<Criterion> criteria = [], Map<String, Object> options = [:]) {
        this.type = type
        this.criteria = criteria.asImmutable()
        this.options = options.asImmutable()
    }

    /** O closure foi reescrito pela AST para devolver um Criterion. */
    DetachedCriteria<T> where(Closure body) {
        new DetachedCriteria<T>(type, criteria + [criterionOf(body, false)], options)
    }

    /** Como where, mas as instruções do closure são combinadas com OR. */
    DetachedCriteria<T> whereAny(Closure body) {
        new DetachedCriteria<T>(type, criteria + [criterionOf(body, true)], options)
    }

    DetachedCriteria<T> where(Criterion c) {
        new DetachedCriteria<T>(type, criteria + [c], options)
    }

    static Criterion criterionOf(Closure body, boolean any) {
        Closure c = (Closure) body.clone()
        c.resolveStrategy = Closure.OWNER_FIRST
        Object r = c.maximumNumberOfParameters == 0 ? c.call() : c.call((Object) null)
        if (r instanceof WhereAny) return any ? Where.or(((WhereAny) r).criteria as Criterion[]) : Where.and(((WhereAny) r).criteria as Criterion[])
        if (r instanceof Criterion) return (Criterion) r
        throw new IllegalStateException('where { } não foi transformado: use a sintaxe de critérios (nome == x && idade > y) ' +
                'em uma chamada Classe.where { } a uma domain class')
    }

    /** Várias instruções no closure (combinadas com AND, ou OR em whereAny). */
    static class WhereAny implements Criterion {
        final List<Criterion> criteria

        WhereAny(Criterion... cs) {
            criteria = cs.toList()
        }

        @Override
        jakarta.persistence.criteria.Predicate toPredicate(QueryContext qc) {
            Where.and(criteria as Criterion[]).toPredicate(qc)
        }
    }

    DetachedCriteria<T> max(int max) { copy(max: max) }

    DetachedCriteria<T> offset(int offset) { copy(offset: offset) }

    DetachedCriteria<T> sort(String property, String order = 'asc') { copy(sort: property, order: order) }

    private DetachedCriteria<T> copy(Map extra) {
        new DetachedCriteria<T>(type, criteria, options + extra)
    }

    // execução @CompileStatic: o mesmo call site atende domains diferentes (no despacho dinâmico, religava a cada troca)
    @groovy.transform.CompileStatic
    private QueryRunner runner(Map params = [:]) {
        def r = new QueryRunner(Gorm.api(type))
        r.criteria = new ArrayList<Criterion>(criteria)
        r.params(options + (params ?: [:]))
    }

    @groovy.transform.CompileStatic
    List<T> list(Map params = [:]) { (List<T>) runner(params).list(options + (params ?: [:])) }

    List<T> findAll(Map params = [:]) { list(params) }

    @groovy.transform.CompileStatic
    T find(Map params = [:]) { (T) runner(params).unique() }

    T get(Map params = [:]) { find(params) }

    @groovy.transform.CompileStatic
    long count() { runner().count() }

    @groovy.transform.CompileStatic
    boolean exists() { runner().unique() != null }

    @groovy.transform.CompileStatic
    int deleteAll() { runner().deleteAll() }

    @groovy.transform.CompileStatic
    int updateAll(Map values) { runner().updateAll(values) }

    @Override
    Iterator<T> iterator() { list().iterator() }

    @Override
    String toString() {
        "DetachedCriteria(${type.simpleName}: ${criteria})"
    }
}
