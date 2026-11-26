package org.grox.http

/**
 * Escopo flash: valores definidos numa requisição ficam disponíveis também na
 * seguinte ({@code flash.message = '...'} antes de um redirect).
 */
class FlashScope implements Serializable, org.grox.session.SessionValue {

    /** Atributo da sessão com o flash. */
    static final String KEY = 'grox.flash'

    private static final long serialVersionUID = 1L

    private Map<String, Object> now = [:]
    private Map<String, Object> next = [:]
    /** Mudou desde a carga (não serializado): um flash vazio que só avança não é regravado. */
    private transient boolean modified

    synchronized void advance() {
        if (now.isEmpty() && next.isEmpty()) return
        now = next
        next = [:]
        modified = true
    }

    @Override
    synchronized boolean isModified() {
        modified
    }

    synchronized Object get(String key) {
        next.containsKey(key) ? next[key] : now[key]
    }

    synchronized void put(String key, Object value) {
        next[key] = value
        modified = true
    }

    synchronized boolean containsKey(String key) {
        next.containsKey(key) || now.containsKey(key)
    }

    synchronized boolean isEmpty() {
        next.isEmpty() && now.isEmpty()
    }

    synchronized Map<String, Object> asMap() {
        now + next
    }

    Object getAt(String key) { get(key) }

    void putAt(String key, Object value) { put(key, value) }

    Object propertyMissing(String name) { get(name) }

    void propertyMissing(String name, Object value) { put(name, value) }
}
