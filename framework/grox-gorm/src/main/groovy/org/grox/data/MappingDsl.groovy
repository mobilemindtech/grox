package org.grox.data

/**
 * Avalia {@code static mapping = { table 'x'; version false; sort 'nome'; id generator: 'uuid';
 * nome column: 'nm', length: 80 }}.
 */
class MappingDsl {

    /** Configurações da entidade (table, version, sort, autoTimestamp, id...). */
    final Map<String, Object> settings = [:]
    /** Configuração por propriedade (column, length, sqlType, unique). */
    final Map<String, Map<String, Object>> columns = [:]

    static final Set<String> ENTITY_SETTINGS = ['table', 'version', 'sort', 'autoTimestamp', 'cache', 'order', 'tenantId'] as Set

    def methodMissing(String name, Object args) {
        Object[] a = (Object[]) args
        Object value = a.length == 1 ? a[0] : (a.length ? a.toList() : null)
        if (name == 'id' && value instanceof Map) settings.id = value
        else if (name in ENTITY_SETTINGS) settings[name] = value
        else if (value instanceof Map) columns.computeIfAbsent(name) { [:] }.putAll((Map) value)
        else settings[name] = value
        null
    }

    static MappingDsl evaluate(Object closure) {
        def dsl = new MappingDsl()
        if (closure instanceof Closure) {
            Closure c = (Closure) ((Closure) closure).clone()
            c.delegate = dsl
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.call()
        }
        dsl
    }
}
