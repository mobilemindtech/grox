package org.grox.data.query

import groovy.transform.CompileStatic
import org.grox.data.DomainClass
import org.grox.data.DomainProperty
import org.grox.data.Gorm

/**
 * Monta o HQL de uma consulta a partir da árvore de critérios: aliases dos joins (internos nas restrições, LEFT na
 * ordenação) e os valores como parâmetros nomeados ({@code :p1}, {@code :p2}...). A mesma forma de consulta gera a
 * mesma string, e o Hibernate reaproveita o plano traduzido (com a JPA Criteria, a tradução era refeita a cada vez).
 */
@CompileStatic
class HqlQuery {

    static final String ROOT = 'e'

    final DomainClass domain
    /** Conversão de valores e validação de caminhos (sem CriteriaBuilder). */
    final QueryContext qc
    final Map<String, Object> params = new LinkedHashMap<>()
    private final Map<String, String> joins = new LinkedHashMap<>()
    private final Map<String, String> orderJoins = new LinkedHashMap<>()
    private final StringBuilder joinClause = new StringBuilder()
    private int aliases

    HqlQuery(DomainClass domain) {
        this.domain = domain
        this.qc = new QueryContext(null, null, domain)
    }

    /** {@code autor.nome} → {@code j1.nome} (com {@code join e.autor j1}). */
    String path(String dotted) {
        resolve(dotted, joins, 'join')
    }

    /** Caminho para {@code order by}: joins LEFT, como na ordenação da Criteria. */
    String orderPath(String dotted) {
        resolve(dotted, orderJoins, 'left join')
    }

    private String resolve(String dotted, Map<String, String> cache, String kind) {
        List<String> parts = dotted.tokenize('.')
        String from = ROOT
        DomainClass d = domain
        String prefix = ''
        for (int i = 0; i < parts.size() - 1; i++) {
            String p = parts[i]
            DomainProperty prop = d?.getAt(p)
            if (prop == null || !prop.association) throw new IllegalArgumentException("${d?.name}.${p} não é uma associação (caminho ${dotted})")
            prefix += (prefix ? '.' : '') + p
            String alias = cache.get(prefix)
            if (alias == null) {
                alias = 'j' + (++aliases)
                joinClause.append(' ').append(kind).append(' ').append(from).append('.').append(p).append(' ').append(alias)
                cache.put(prefix, alias)
            }
            from = alias
            d = Gorm.domain(prop.referencedType)
        }
        String last = parts.last()
        if (d != null && !d.hasPersistentProperty(last)) throw new IllegalArgumentException("propriedade desconhecida em ${d.name}: ${last} (caminho ${dotted})")
        from + '.' + last
    }

    /** Um parâmetro nomeado com o valor já convertido para o tipo da propriedade. */
    String param(String dotted, Object value) {
        param(qc.convert(dotted, value))
    }

    String param(Object value) {
        String name = 'p' + (params.size() + 1)
        params.put(name, value)
        ':' + name
    }

    String getJoinClause() {
        joinClause.toString()
    }

    /** Liga os parâmetros nomeados (coleções com {@code setParameterList}, para {@code in (:p1)}). */
    void bind(org.hibernate.query.SelectionQuery q) {
        for (Map.Entry<String, Object> p : params.entrySet()) {
            if (p.value instanceof Collection) q.setParameterList(p.key, (Collection) p.value)
            else q.setParameter(p.key, p.value)
        }
    }

    /** O nome da entidade no HQL (o do metamodelo, que segue o mapeamento). */
    static String entityName(org.hibernate.Session s, Class type) {
        s.metamodel.entity(type).name
    }
}
