package org.grox.core

/**
 * Visão de um mapa da configuração para a navegação por pontos da aplicação ({@code config.app.build.at}):
 * chave inexistente em qualquer nível devolve um {@link NullSafeNavigator}, como o {@code NavigableMap} do
 * Grails. {@code get()}/{@code [chave]} e a iteração devolvem os valores originais (o mapa guardado não muda).
 */
class ConfigView extends AbstractMap<String, Object> {
    final Map<String, Object> target

    ConfigView(Map<String, Object> target) { this.target = target }

    static Object wrap(Object v) {
        v instanceof Map && !(v instanceof ConfigView) ? new ConfigView((Map<String, Object>) v) : v
    }

    Object getProperty(String name) {
        target.containsKey(name) ? wrap(target.get(name)) : new NullSafeNavigator([name])
    }

    void setProperty(String name, Object value) { target.put(name, value) }

    @Override
    Set<Map.Entry<String, Object>> entrySet() { target.entrySet() }

    @Override
    Object get(Object key) { target.get(key) }

    @Override
    boolean containsKey(Object key) { target.containsKey(key) }

    @Override
    Object put(String key, Object value) { target.put(key, value) }

    @Override
    int size() { target.size() }
}
