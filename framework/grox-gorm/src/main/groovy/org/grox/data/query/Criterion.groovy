package org.grox.data.query

import jakarta.persistence.criteria.Predicate

/** Nó da árvore de critérios produzida por {@code where { }} e {@code createCriteria()}. */
interface Criterion {
    Predicate toPredicate(QueryContext qc)

    /** O mesmo critério em HQL, com os valores como parâmetros de {@code h} (o Hibernate guarda o plano pela string). */
    String toHql(HqlQuery h)
}
