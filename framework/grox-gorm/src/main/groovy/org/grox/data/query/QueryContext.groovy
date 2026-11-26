package org.grox.data.query

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Root
import org.grox.data.DomainClass
import org.grox.data.DomainProperty
import org.grox.data.Gorm
import org.grox.data.binding.DataBinder

/**
 * Resolve caminhos ({@code grupo.nome}) em joins (reaproveitados por consulta) e converte
 * valores para o tipo da propriedade.
 */
class QueryContext {

    final CriteriaBuilder cb
    final Root root
    final DomainClass domain
    private final Map<String, From> joins = [:]
    /** Joins da ordenação: LEFT, para não esconder quem não tem a associação (separados dos das restrições). */
    private final Map<String, From> orderJoins = [:]

    QueryContext(CriteriaBuilder cb, Root root, DomainClass domain) {
        this.cb = cb
        this.root = root
        this.domain = domain
    }

    Path path(String dotted) {
        resolve(dotted, joins, JoinType.INNER)
    }

    /** Caminho para {@code order by}: joins LEFT (um livro sem autor continua na lista ordenada por {@code autor.nome}). */
    Path orderPath(String dotted) {
        resolve(dotted, orderJoins, JoinType.LEFT)
    }

    private Path resolve(String dotted, Map<String, From> cache, JoinType joinType) {
        List<String> parts = dotted.tokenize('.')
        From from = root
        DomainClass d = domain
        String prefix = ''
        for (int i = 0; i < parts.size() - 1; i++) {
            String p = parts[i]
            DomainProperty prop = d?.getAt(p)
            if (prop == null || !prop.association) throw new IllegalArgumentException("${d?.name}.${p} não é uma associação (caminho ${dotted})")
            prefix += (prefix ? '.' : '') + p
            From current = from
            from = cache.computeIfAbsent(prefix) { current.join(p, joinType) }
            d = Gorm.domain(prop.referencedType)
        }
        String last = parts.last()
        if (d != null && !d.hasPersistentProperty(last)) throw new IllegalArgumentException("propriedade desconhecida em ${d.name}: ${last} (caminho ${dotted})")
        from.get(last)
    }

    /** Converte Strings (ex. params) para o tipo da propriedade do caminho. */
    Object convert(String dotted, Object value) {
        DomainProperty p = property(dotted)
        if (value instanceof GString) value = value.toString()
        if (p == null || p.association || value == null || value instanceof Range) return value
        if (value instanceof Collection) return ((Collection) value).collect { convertOne(p, it) }
        convertOne(p, value)
    }

    private static Object convertOne(DomainProperty p, Object v) {
        if (v instanceof CharSequence && p.type != String && DataBinder.isSimple(p.type)) return DataBinder.convert(v.toString(), p.type)
        v instanceof GString ? v.toString() : v   // o Hibernate 7 recusa GString (o GORM converte)
    }

    /** O caminho existe ({@code titulo}, {@code autor.nome}): para validar um {@code sort} vindo de {@code params}. */
    static boolean isValidPath(DomainClass domain, String dotted) {
        if (!dotted) return false
        List<String> parts = dotted.tokenize('.')
        DomainClass d = domain
        for (int i = 0; i < parts.size() - 1; i++) {
            DomainProperty prop = d?.getAt(parts[i])
            if (prop == null || !prop.association || prop.referencedType == null) return false
            d = Gorm.domain(prop.referencedType)
        }
        d != null && d.hasPersistentProperty(parts.last())
    }

    DomainProperty property(String dotted) {
        List<String> parts = dotted.tokenize('.')
        DomainClass d = domain
        for (int i = 0; i < parts.size() - 1; i++) {
            DomainProperty prop = d?.getAt(parts[i])
            if (prop?.referencedType == null) return null
            d = Gorm.domain(prop.referencedType)
        }
        d?.getAt(parts.last())
    }
}
