package org.grox.web.json

/**
 * Closure de JSON no estilo do JSONBuilder do Grails, usado por
 * {@code render(contentType: 'application/json') { erro = 'x'; itens = [1, 2]; dados { a = 1 } }}.
 * Atribuições viram campos; {@code nome { ... }} vira objeto aninhado; {@code nome(valor)} também é aceito.
 */
class JsonClosureBuilder {

    static Map build(Closure body) {
        def d = new ObjectDelegate()
        Closure c = (Closure) body.clone()
        c.delegate = d
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
        d.map
    }

    private static Object value(Object v) {
        v instanceof Closure ? build((Closure) v) : v
    }

    static class ObjectDelegate {
        final Map<String, Object> map = [:]

        void propertyMissing(String name, Object value) {
            map[name] = JsonClosureBuilder.value(value)
        }

        Object propertyMissing(String name) {
            map[name]
        }

        def methodMissing(String name, Object args) {
            Object[] a = (Object[]) args
            map[name] = a.length == 1 ? JsonClosureBuilder.value(a[0]) : a.collect { JsonClosureBuilder.value(it) }
            null
        }
    }
}
