package org.grox.data.validation

import org.grox.core.ClassUtils

import java.util.concurrent.ConcurrentHashMap

/**
 * Avalia {@code static constraints = { nome blank: false, size: 2..100 }} (inclusive das
 * superclasses) em {@code Map<propriedade, Map<constraint, valor>>}, com cache por classe.
 */
class ConstraintsBuilder {

    private static final Map<Class, Map<String, Map<String, Object>>> CACHE = new ConcurrentHashMap<>()

    final Map<String, Map<String, Object>> constraints = [:]

    def methodMissing(String name, Object args) {
        Object[] a = (Object[]) args
        Map rules = a.length && a[0] instanceof Map ? (Map) a[0] : [:]
        constraints.computeIfAbsent(name) { [:] }.putAll(rules)
        null
    }

    /** Referências a propriedades estáticas/classes dentro do closure (ex.: inList: Status.values()). */
    def propertyMissing(String name) {
        throw new MissingPropertyException(name, ConstraintsBuilder)
    }

    static Map<String, Map<String, Object>> declared(Class<?> type) {
        CACHE.computeIfAbsent(type) {
            def builder = new ConstraintsBuilder()
            List<Class> hierarchy = []
            for (Class k = type; k != null && k != Object; k = k.superclass) hierarchy.add(0, k)
            for (Class k : hierarchy) {
                Object c = ClassUtils.staticProperty(k, 'constraints')
                if (c instanceof Closure) {
                    Closure cl = (Closure) ((Closure) c).clone()
                    cl.delegate = builder
                    cl.resolveStrategy = Closure.DELEGATE_FIRST
                    cl.call()
                }
            }
            builder.constraints
        }
    }

    static void clearCache() {
        CACHE.clear()
    }
}
