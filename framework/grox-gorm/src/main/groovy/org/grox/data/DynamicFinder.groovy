package org.grox.data

import org.grox.data.query.Criterion
import org.grox.data.query.Where

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
        int i = 0
        List<Criterion> criteria = plan.clauses.collect { Clause c ->
            Object[] values = (0..<c.arity).collect { args[i++] } as Object[]
            criterion(c, values)
        }
        Criterion combined = plan.or ? Where.or(criteria as Criterion[]) : Where.and(criteria as Criterion[])
        if (plan.required != null) combined = criteria ? Where.and(criterion(plan.required, new Object[0]), combined) : criterion(plan.required, new Object[0])
        api.query(pagination, plan.prefix == 'countBy', plan.prefix == 'findBy', combined)
    }

    /** O critério de uma cláusula (os valores são convertidos para o tipo da propriedade no HQL). */
    static Criterion criterion(Clause c, Object[] v) {
        String p = c.property
        return switch (c.operator) {
            case 'Equal' -> Where.eq(p, v[0])
            case 'NotEqual' -> Where.ne(p, v[0])
            case 'LessThan' -> Where.lt(p, v[0])
            case 'LessThanEquals' -> Where.le(p, v[0])
            case 'GreaterThan' -> Where.gt(p, v[0])
            case 'GreaterThanEquals' -> Where.ge(p, v[0])
            case 'Like' -> Where.like(p, v[0].toString())
            case 'NotLike' -> Where.not(Where.like(p, v[0].toString()))
            case 'Ilike' -> Where.ilike(p, v[0].toString())
            case 'InList' -> Where.restriction('in', p, v[0])
            case 'NotInList' -> Where.not(Where.restriction('in', p, v[0]))
            case 'Between' -> Where.between(p, v[0], v[1])
            case 'InRange' -> Where.between(p, ((Range) v[0]).from, ((Range) v[0]).to)
            case 'IsTrue' -> Where.eq(p, true)
            case 'IsFalse' -> Where.eq(p, false)
            case 'IsNull' -> Where.isNull(p)
            case 'IsNotNull' -> Where.isNotNull(p)
            case 'IsEmpty' -> Where.isEmpty(p)
            case 'IsNotEmpty' -> Where.isNotEmpty(p)
            default -> throw new IllegalStateException(c.operator)
        }
    }
}
