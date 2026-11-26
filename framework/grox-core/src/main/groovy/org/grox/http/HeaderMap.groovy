package org.grox.http

/** Cabeçalhos HTTP com nomes case-insensitive e múltiplos valores. */
class HeaderMap extends TreeMap<String, List<String>> {

    HeaderMap() {
        super(String.CASE_INSENSITIVE_ORDER)
    }

    String getFirst(String name) {
        List<String> v = super.get(name)
        v ? v[0] : null
    }

    /** Sem closure: roda para cada cabeçalho de cada requisição. */
    void add(String name, String value) {
        List<String> v = super.get(name)
        if (v == null) {
            v = new ArrayList<String>(1)
            put(name, v)
        }
        v.add(value)
    }

    void set(String name, String value) {
        if (value == null) remove(name)
        else put(name, [value])
    }
}
