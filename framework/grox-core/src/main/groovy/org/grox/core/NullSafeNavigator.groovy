package org.grox.core

/**
 * Como o {@code NavigableMap.NullSafeNavigator} do Grails: o que {@code config.chaveInexistente} devolve.
 * É um Map vazio que vale false ({@code if (config.x)}, {@code config.x ?: padrao}), e navegar nele
 * ({@code config.x.y}) devolve outro navegador, em vez de NullPointerException. {@code == null} é false,
 * como no Grails sobre o Groovy 6.
 */
class NullSafeNavigator extends AbstractMap<String, Object> {
    final List<String> path

    NullSafeNavigator(List<String> path) { this.path = path.asImmutable() }

    @Override
    Set<Map.Entry<String, Object>> entrySet() { Collections.emptySet() }

    Object getProperty(String name) { new NullSafeNavigator(path + [name]) }

    Object getAt(Object key) { getProperty(String.valueOf(key)) }

    @Override
    Object get(Object key) { getAt(key) }

    boolean asBoolean() { false }

    @Override
    boolean equals(Object o) { o == null || o.is(this) }

    @Override
    int hashCode() { 0 }

    @Override
    String toString() { '' }
}
