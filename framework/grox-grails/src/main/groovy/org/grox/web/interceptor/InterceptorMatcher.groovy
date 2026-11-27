package org.grox.web.interceptor

import org.grox.security.AccessRule
import org.grox.web.WebRequest

import java.util.regex.Pattern

/**
 * Critério de um interceptor: {@code match(controller: 'livro', action: '*')},
 * {@code match(controller: ~/(autor|editora)/)}, {@code match(uri: '/api/**')},
 * {@code match(method: 'POST')}, com {@code .excludes(action: 'index')}.
 */
@groovy.transform.CompileStatic
class InterceptorMatcher {

    final Map<String, Object> criteria
    final List<Map<String, Object>> exclusions = []

    InterceptorMatcher(Map<String, Object> criteria) {
        for (String k : criteria.keySet()) {
            if (!(k in ['controller', 'action', 'uri', 'method', 'namespace'])) throw new IllegalArgumentException("critério de interceptor desconhecido: ${k}")
        }
        this.criteria = compiled(criteria)
    }

    InterceptorMatcher excludes(Map<String, Object> exclusion) {
        exclusions << compiled(exclusion)
        this
    }

    /** {@code uri: '/api/**'} vira Pattern uma vez, e não a cada requisição. */
    private static Map<String, Object> compiled(Map<String, Object> c) {
        Object uri = c.get('uri')
        if (uri == null || uri instanceof Pattern || uri instanceof Collection || uri == '*') return c
        Map<String, Object> copy = new LinkedHashMap<>(c)
        copy.put('uri', Pattern.compile(AccessRule.antToRegex(uri.toString())))
        copy
    }

    boolean matches(WebRequest wr) {
        if (!test(criteria, wr)) return false
        for (Map<String, Object> e : exclusions) {
            if (test(e, wr)) return false
        }
        true
    }

    private static boolean test(Map<String, Object> c, WebRequest wr) {
        for (Map.Entry<String, Object> e : c.entrySet()) {
            String key = e.key
            String actual = switch (key) {
                case 'controller' -> wr.controllerName
                case 'action' -> wr.actionName
                case 'method' -> wr.request.method
                case 'uri' -> wr.request.path
                default -> null
            }
            if (!matchValue(e.value, actual, key == 'uri')) return false
        }
        true
    }

    private static boolean matchValue(Object expected, String actual, boolean ant) {
        if (expected == null || expected == '*') return true
        if (actual == null) return false
        if (expected instanceof Pattern) return ((Pattern) expected).matcher(actual).matches()
        if (expected instanceof Collection) return ((Collection) expected)*.toString().contains(actual)
        if (ant) return actual ==~ AccessRule.antToRegex(expected.toString())
        expected.toString().equalsIgnoreCase(actual)
    }
}
