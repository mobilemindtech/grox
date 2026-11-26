package org.grox.data

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.grox.data.binding.DataBinder

import java.util.concurrent.ConcurrentHashMap

/**
 * Dynamic finders do GORM: {@code findBy}, {@code findAllBy}, {@code countBy} com
 * operadores ({@code NomeLike}, {@code IdadeGreaterThan}, {@code IdadeBetween}...)
 * combinados por {@code And} ou {@code Or}; {@code listOrderBy<Prop>}. Finders booleanos do GORM:
 * {@code findAllEnabled()}, {@code findNotEnabled()}, {@code findAllEnabledByNome(n)} (a propriedade
 * booleana vale true, ou false com {@code Not}, sem consumir argumento).
 */
class DynamicFinder {

    static final List<String> PREFIXES = ['findAllBy', 'findBy', 'countBy']
    /** Maior primeiro, para o sufixo mais longo vencer. */
    static final List<String> OPERATORS = ['LessThanEquals', 'GreaterThanEquals', 'IsNotEmpty', 'NotInList', 'IsNotNull',
                                           'NotEqual', 'LessThan', 'GreaterThan', 'Between', 'InRange', 'InList',
                                           'IsEmpty', 'IsNull', 'NotLike', 'Ilike', 'Like', 'Equal']
    static final Map<String, Integer> ARITY = [Between: 2, IsNull: 0, IsNotNull: 0, IsEmpty: 0, IsNotEmpty: 0]

    static class Clause {
        String property
        String operator
        int arity
    }

    /** {@code findAll<Bool>By...}: a condição booleana entra com AND mesmo quando o resto usa Or, como no GORM. */
    private static final java.util.regex.Pattern BOOLEAN_FINDER = ~/^(findAll|find)(Not)?([A-Z]\w*?)(?:By([A-Z]\w*))?$/

    static class Plan {
        String prefix
        List<Clause> clauses
        boolean or
        /** Condição do finder booleano (ou null). */
        Clause required

        int getArity() {
            (clauses.sum { it.arity } ?: 0) as int
        }
    }

    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>()
    private static final Object NOT_A_FINDER = new Object()

    static Plan plan(DomainClass d, String method) {
        Object p = CACHE.computeIfAbsent(d.type.name + '#' + method) { parse(d, method) ?: NOT_A_FINDER }
        p.is(NOT_A_FINDER) ? null : (Plan) p
    }

    static Plan parse(DomainClass d, String method) {
        String prefix = PREFIXES.find { method.startsWith(it) && method.length() > it.length() }
        if (prefix == null) return parseBoolean(d, method)
        parseClauses(d, method, prefix, method.substring(prefix.length()))
    }

    private static Plan parseBoolean(DomainClass d, String method) {
        def m = BOOLEAN_FINDER.matcher(method)
        if (!m.matches()) return null
        String prop = m.group(3).uncapitalize()
        Class<?> type = d.hasPersistentProperty(prop) ? d[prop].type : null
        if (type != boolean && type != Boolean) return null
        Clause required = new Clause(property: prop, operator: m.group(2) ? 'IsFalse' : 'IsTrue', arity: 0)
        String prefix = m.group(1) == 'findAll' ? 'findAllBy' : 'findBy'
        Plan plan = m.group(4) ? parseClauses(d, method, prefix, m.group(4)) : new Plan(prefix: prefix, clauses: [])
        plan?.required = required
        plan
    }

    private static Plan parseClauses(DomainClass d, String method, String prefix, String expr) {
        boolean hasAnd = expr =~ /(?<=[a-z0-9])And(?=[A-Z])/
        boolean hasOr = expr =~ /(?<=[a-z0-9])Or(?=[A-Z])/
        List<String> parts = [expr]
        boolean or = false
        if (hasAnd && hasOr) throw new UnsupportedOperationException("${method}: não é possível misturar And e Or")
        if (hasAnd) parts = expr.split(/(?<=[a-z0-9])And(?=[A-Z])/).toList()
        if (hasOr) {
            parts = expr.split(/(?<=[a-z0-9])Or(?=[A-Z])/).toList()
            or = true
        }
        List<Clause> clauses = []
        for (String part : parts) {
            Clause c = clause(d, part)
            if (c == null) return null
            clauses << c
        }
        new Plan(prefix: prefix, clauses: clauses, or: or)
    }

    private static Clause clause(DomainClass d, String part) {
        for (String op : OPERATORS) {
            if (part.endsWith(op) && part.length() > op.length()) {
                String prop = part.substring(0, part.length() - op.length()).uncapitalize()
                if (d.hasPersistentProperty(prop)) return new Clause(property: prop, operator: op, arity: ARITY.containsKey(op) ? ARITY[op] : 1)
            }
        }
        String prop = part.uncapitalize()
        d.hasPersistentProperty(prop) ? new Clause(property: prop, operator: 'Equal', arity: 1) : null
    }

    static Object execute(GormStaticApi api, Plan plan, Object[] args) {
        int needed = plan.arity
        if (args.length < needed) throw new IllegalArgumentException("${plan.prefix}: esperados ${needed} argumento(s), recebidos ${args.length}")
        Map pagination = args.length > needed && args[needed] instanceof Map ? (Map) args[needed] : [:]
        api.query(pagination, plan.prefix == 'countBy', plan.prefix == 'findBy') { CriteriaBuilder cb, Root root ->
            int i = 0
            List<Predicate> predicates = plan.clauses.collect { Clause c ->
                Object[] values = (0..<c.arity).collect { convert(api.domain, c.property, args[i++]) } as Object[]
                predicate(cb, root, c, values)
            }
            Predicate combined = plan.or ? cb.or(predicates as Predicate[]) : cb.and(predicates as Predicate[])
            if (plan.required == null) return combined
            Predicate flag = predicate(cb, root, plan.required, new Object[0])
            predicates ? cb.and(flag, combined) : flag
        }
    }

    static Object convert(DomainClass d, String prop, Object value) {
        DomainProperty p = d[prop]
        if (value instanceof Range) return value
        if (value instanceof CharSequence && p.type != String && !p.association && DataBinder.isSimple(p.type)) {
            return DataBinder.convert(value.toString(), p.type)
        }
        if (value instanceof Collection && !p.association && p.type != String && DataBinder.isSimple(p.type)) {
            return ((Collection) value).collect { it instanceof CharSequence ? DataBinder.convert(it.toString(), p.type) : it }
        }
        value
    }

    static Predicate predicate(CriteriaBuilder cb, Root root, Clause c, Object[] v) {
        Path path = root.get(c.property)
        Expression<Comparable> cmp = (Expression<Comparable>) path
        return switch (c.operator) {
            case 'Equal' -> v[0] == null ? cb.isNull(path) : cb.equal(path, v[0])
            case 'NotEqual' -> v[0] == null ? cb.isNotNull(path) : cb.notEqual(path, v[0])
            case 'LessThan' -> cb.lessThan(cmp, (Comparable) v[0])
            case 'LessThanEquals' -> cb.lessThanOrEqualTo(cmp, (Comparable) v[0])
            case 'GreaterThan' -> cb.greaterThan(cmp, (Comparable) v[0])
            case 'GreaterThanEquals' -> cb.greaterThanOrEqualTo(cmp, (Comparable) v[0])
            case 'Like' -> cb.like((Expression<String>) path, v[0].toString())
            case 'NotLike' -> cb.notLike((Expression<String>) path, v[0].toString())
            case 'Ilike' -> cb.like(cb.lower((Expression<String>) path), v[0].toString().toLowerCase())
            case 'InList' -> ((Collection) v[0]) ? path.in((Collection) v[0]) : cb.disjunction()
            case 'NotInList' -> ((Collection) v[0]) ? cb.not(path.in((Collection) v[0])) : cb.conjunction()
            case 'Between' -> cb.between(cmp, (Comparable) v[0], (Comparable) v[1])
            case 'InRange' -> cb.between(cmp, (Comparable) ((Range) v[0]).from, (Comparable) ((Range) v[0]).to)
            case 'IsTrue' -> cb.isTrue((Expression<Boolean>) path)
            case 'IsFalse' -> cb.isFalse((Expression<Boolean>) path)
            case 'IsNull' -> cb.isNull(path)
            case 'IsNotNull' -> cb.isNotNull(path)
            case 'IsEmpty' -> cb.isEmpty((Expression<Collection>) path)
            case 'IsNotEmpty' -> cb.isNotEmpty((Expression<Collection>) path)
            default -> throw new IllegalStateException(c.operator)
        }
    }
}
