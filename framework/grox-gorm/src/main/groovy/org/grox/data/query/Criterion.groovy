package org.grox.data.query

import jakarta.persistence.criteria.Predicate

/** Nó da árvore de critérios produzida por {@code where { }} e {@code createCriteria()}. */
interface Criterion {
    Predicate toPredicate(QueryContext qc)
}
