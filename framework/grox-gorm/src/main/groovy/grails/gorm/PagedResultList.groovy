package grails.gorm

/**
 * Como no GORM: o resultado de {@code list(max: n)} (estático, {@code createCriteria().list(params) { }},
 * {@code where { }.list(params)}) é a página pedida mais o {@code totalCount} de todos os registros que
 * satisfazem os critérios, calculado com um {@code count} na primeira leitura.
 */
class PagedResultList<E> implements List<E> {

    @Delegate
    private final List<E> results
    private final Closure<Number> counter
    private Integer total

    PagedResultList(List<E> results, Closure<Number> counter) {
        this.results = results
        this.counter = counter
    }

    int getTotalCount() {
        if (total == null) total = counter.call().intValue()
        total
    }

    @Override
    String toString() {
        results.toString()
    }

    @Override
    boolean equals(Object o) {
        results == o
    }

    @Override
    int hashCode() {
        results.hashCode()
    }
}
