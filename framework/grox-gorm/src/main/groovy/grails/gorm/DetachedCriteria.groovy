package grails.gorm

import org.grox.data.query.Criterion

/**
 * Compatibilidade com {@code grails.gorm.DetachedCriteria}: o critério do Grox mais
 * {@code distinct('prop')} (lista os valores distintos da propriedade).
 */
class DetachedCriteria<T> extends org.grox.data.query.DetachedCriteria<T> {

    private String distinctProperty

    DetachedCriteria(Class<T> type, List<Criterion> criteria = [], Map<String, Object> options = [:]) {
        super(type, criteria, options)
    }

    DetachedCriteria<T> distinct(String property) {
        def c = new DetachedCriteria<T>(type, criteria, options)
        c.distinctProperty = property
        c
    }

    @Override
    List list(Map params = [:]) {
        List result = super.list(params)
        distinctProperty ? result.collect { it[distinctProperty] }.unique() : result
    }
}
