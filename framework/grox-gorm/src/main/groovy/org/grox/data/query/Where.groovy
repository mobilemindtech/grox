package org.grox.data.query

import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate

/**
 * Fábrica de critérios. É o alvo do código gerado pela AST de {@code where { }}
 * ({@code nome == 'Ana' && idade > 18} → {@code and(eq('nome', 'Ana'), gt('idade', 18))})
 * e é usada pelo {@code createCriteria()}.
 */
class Where {

    static Criterion restriction(String op, String path, Object value, Object value2 = null) {
        new Restriction(op, path, value, value2)
    }

    static Criterion eq(String p, Object v) { restriction(v == null ? 'isNull' : 'eq', p, v) }

    static Criterion ne(String p, Object v) { restriction(v == null ? 'isNotNull' : 'ne', p, v) }

    static Criterion gt(String p, Object v) { restriction('gt', p, v) }

    static Criterion ge(String p, Object v) { restriction('ge', p, v) }

    static Criterion lt(String p, Object v) { restriction('lt', p, v) }

    static Criterion le(String p, Object v) { restriction('le', p, v) }

    static Criterion like(String p, Object v) { restriction('like', p, v) }

    static Criterion ilike(String p, Object v) { restriction('ilike', p, v) }

    static Criterion inList(String p, Object v) {
        v instanceof Range ? between(p, ((Range) v).from, ((Range) v).to) : restriction('in', p, v)
    }

    static Criterion notInList(String p, Object v) { not(inList(p, v)) }

    static Criterion between(String p, Object a, Object b) { restriction('between', p, a, b) }

    static Criterion isNull(String p) { restriction('isNull', p, null) }

    static Criterion isNotNull(String p) { restriction('isNotNull', p, null) }

    static Criterion isEmpty(String p) { restriction('isEmpty', p, null) }

    static Criterion isNotEmpty(String p) { restriction('isNotEmpty', p, null) }

    static Criterion eqProperty(String p, String other) { new PropertyComparison('eq', p, other) }

    static Criterion neProperty(String p, String other) { new PropertyComparison('ne', p, other) }

    static Criterion gtProperty(String p, String other) { new PropertyComparison('gt', p, other) }

    static Criterion geProperty(String p, String other) { new PropertyComparison('ge', p, other) }

    static Criterion ltProperty(String p, String other) { new PropertyComparison('lt', p, other) }

    static Criterion leProperty(String p, String other) { new PropertyComparison('le', p, other) }

    static Criterion and(Criterion... cs) { new Junction(false, cs.toList()) }

    static Criterion or(Criterion... cs) { new Junction(true, cs.toList()) }

    static Criterion not(Criterion c) { new Not(c) }

    /** Propriedade booleana sozinha: {@code where { concluida }}. */
    static Criterion isTrue(String p) { eq(p, true) }

    static class Junction implements Criterion {
        final boolean or
        final List<Criterion> criteria

        Junction(boolean or, List<Criterion> criteria) {
            this.or = or
            this.criteria = criteria
        }

        @Override
        Predicate toPredicate(QueryContext qc) {
            Predicate[] ps = criteria.collect { it.toPredicate(qc) } as Predicate[]
            or ? qc.cb.or(ps) : qc.cb.and(ps)
        }

        @Override
        @groovy.transform.CompileStatic
        String toHql(HqlQuery h) {
            // como cb.and()/cb.or() vazios: verdadeiro e falso
            if (criteria.isEmpty()) return or ? '1=0' : '1=1'
            if (criteria.size() == 1) return criteria[0].toHql(h)
            StringBuilder sb = new StringBuilder('(')
            for (int i = 0; i < criteria.size(); i++) {
                if (i > 0) sb.append(or ? ' or ' : ' and ')
                sb.append(criteria[i].toHql(h))
            }
            sb.append(')').toString()
        }
    }

    static class Not implements Criterion {
        final Criterion criterion

        Not(Criterion criterion) {
            this.criterion = criterion
        }

        @Override
        Predicate toPredicate(QueryContext qc) {
            qc.cb.not(criterion.toPredicate(qc))
        }

        @Override
        @groovy.transform.CompileStatic
        String toHql(HqlQuery h) {
            'not (' + criterion.toHql(h) + ')'
        }
    }

    static class Restriction implements Criterion {
        final String op
        final String path
        final Object value
        final Object value2

        Restriction(String op, String path, Object value, Object value2) {
            this.op = op
            this.path = path
            this.value = value
            this.value2 = value2
        }

        @Override
        Predicate toPredicate(QueryContext qc) {
            def cb = qc.cb
            Path p = qc.path(path)
            Object v = qc.convert(path, value)
            Expression<Comparable> c = (Expression<Comparable>) p
            return switch (op) {
                case 'eq' -> cb.equal(p, v)
                case 'ne' -> cb.notEqual(p, v)
                case 'gt' -> cb.greaterThan(c, (Comparable) v)
                case 'ge' -> cb.greaterThanOrEqualTo(c, (Comparable) v)
                case 'lt' -> cb.lessThan(c, (Comparable) v)
                case 'le' -> cb.lessThanOrEqualTo(c, (Comparable) v)
                case 'like' -> cb.like((Expression<String>) p, v.toString())
                case 'ilike' -> cb.like(cb.lower((Expression<String>) p), v.toString().toLowerCase())
                case 'in' -> ((Collection) v) ? p.in((Collection) v) : cb.disjunction()
                case 'between' -> cb.between(c, (Comparable) v, (Comparable) qc.convert(path, value2))
                case 'isNull' -> cb.isNull(p)
                case 'isNotNull' -> cb.isNotNull(p)
                case 'isEmpty' -> cb.isEmpty((Expression<Collection>) p)
                case 'isNotEmpty' -> cb.isNotEmpty((Expression<Collection>) p)
                default -> throw new IllegalArgumentException("operador desconhecido: ${op}")
            }
        }

        @Override
        @groovy.transform.CompileStatic
        String toHql(HqlQuery h) {
            String p = h.path(path)
            return switch (op) {
                case 'eq' -> p + ' = ' + h.param(path, value)
                case 'ne' -> p + ' <> ' + h.param(path, value)
                case 'gt' -> p + ' > ' + h.param(path, value)
                case 'ge' -> p + ' >= ' + h.param(path, value)
                case 'lt' -> p + ' < ' + h.param(path, value)
                case 'le' -> p + ' <= ' + h.param(path, value)
                case 'like' -> p + ' like ' + h.param(value.toString())
                case 'ilike' -> 'lower(' + p + ') like ' + h.param(value.toString().toLowerCase())
                case 'in' -> {
                    Object v = h.qc.convert(path, value)
                    yield (v instanceof Collection && !((Collection) v).isEmpty()) ? p + ' in (' + h.param(v) + ')' : '1=0'
                }
                case 'between' -> p + ' between ' + h.param(path, value) + ' and ' + h.param(path, value2)
                case 'isNull' -> p + ' is null'
                case 'isNotNull' -> p + ' is not null'
                case 'isEmpty' -> p + ' is empty'
                case 'isNotEmpty' -> p + ' is not empty'
                default -> throw new IllegalArgumentException("operador desconhecido: ${op}")
            }
        }

        @Override
        String toString() {
            "${path} ${op} ${value}${value2 != null ? " ${value2}" : ''}"
        }
    }

    static class PropertyComparison implements Criterion {
        final String op
        final String left
        final String right

        PropertyComparison(String op, String left, String right) {
            this.op = op
            this.left = left
            this.right = right
        }

        @Override
        Predicate toPredicate(QueryContext qc) {
            Expression<Comparable> a = (Expression<Comparable>) qc.path(left)
            Expression<Comparable> b = (Expression<Comparable>) qc.path(right)
            return switch (op) {
                case 'eq' -> qc.cb.equal(a, b)
                case 'ne' -> qc.cb.notEqual(a, b)
                case 'gt' -> qc.cb.greaterThan(a, b)
                case 'ge' -> qc.cb.greaterThanOrEqualTo(a, b)
                case 'lt' -> qc.cb.lessThan(a, b)
                case 'le' -> qc.cb.lessThanOrEqualTo(a, b)
                default -> throw new IllegalArgumentException(op)
            }
        }

        static final Map<String, String> HQL_OPS = [eq: '=', ne: '<>', gt: '>', ge: '>=', lt: '<', le: '<=']

        @Override
        @groovy.transform.CompileStatic
        String toHql(HqlQuery h) {
            String o = HQL_OPS[op]
            if (o == null) throw new IllegalArgumentException(op)
            h.path(left) + ' ' + o + ' ' + h.path(right)
        }
    }
}
