package org.grails.web.json

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

/**
 * Compatibilidade com o {@code JSONObject} do Grails: um {@link LinkedHashMap} cujo
 * {@code toString()} é o JSON. É o tipo devolvido por {@code request.JSON} e
 * {@code JSON.parse} para objetos.
 */
@CompileStatic
class JSONObject extends LinkedHashMap<String, Object> {

    JSONObject() {}

    JSONObject(Map map) {
        map?.each { k, v -> put(String.valueOf(k), wrap(v)) }
    }

    JSONObject(String json) {
        this((Map) new JsonSlurper().parseText(json))
    }

    JSONObject(Reader reader) {
        this((Map) new JsonSlurper().parse(reader))
    }

    JSONObject accumulate(String key, Object value) {
        Object old = get(key)
        if (old == null) put(key, wrap(value))
        else if (old instanceof JSONArray) ((JSONArray) old) << wrap(value)
        else put(key, new JSONArray([old, value]))
        this
    }

    JSONObject element(String key, Object value) { put(key, wrap(value)); this }

    boolean has(String key) { containsKey(key) }

    boolean isNull(String key) { get(key) == null }

    String optString(String key, String defaultValue = '') {
        Object v = get(key)
        v == null ? defaultValue : v.toString()
    }

    String getString(String key) { get(key)?.toString() }

    // opt*/get* numéricos e booleanos do org.json, como no JSONObject do Grails: aceitam número ou texto;
    // opt* devolve o padrão se a chave falta ou o valor não converte, get* lança JSONException

    int optInt(String key, int defaultValue = 0) { Number n = number(key); n == null ? defaultValue : n.intValue() }

    long optLong(String key, long defaultValue = 0L) { Number n = number(key); n == null ? defaultValue : n.longValue() }

    double optDouble(String key, double defaultValue = Double.NaN) { Number n = number(key); n == null ? defaultValue : n.doubleValue() }

    boolean optBoolean(String key, boolean defaultValue = false) { Boolean b = bool(key); b == null ? defaultValue : b }

    JSONObject optJSONObject(String key) { Object v = get(key); v instanceof JSONObject ? (JSONObject) v : null }

    JSONArray optJSONArray(String key) { Object v = get(key); v instanceof JSONArray ? (JSONArray) v : null }

    int getInt(String key) { required(key, number(key)).intValue() }

    long getLong(String key) { required(key, number(key)).longValue() }

    double getDouble(String key) { required(key, number(key)).doubleValue() }

    boolean getBoolean(String key) { (boolean) required(key, bool(key)) }

    private Number number(String key) {
        Object v = get(key)
        if (v instanceof Number) return (Number) v
        if (v instanceof CharSequence) {
            String t = v.toString().trim()
            if (t.isNumber()) return t.toBigDecimal()
        }
        null
    }

    private Boolean bool(String key) {
        Object v = get(key)
        if (v instanceof Boolean) return (Boolean) v
        String t = v?.toString()?.trim()?.toLowerCase()
        t == 'true' ? Boolean.TRUE : t == 'false' ? Boolean.FALSE : null
    }

    private <T> T required(String key, T value) {
        if (value == null) throw new JSONException("JSONObject[\"${key}\"] ausente ou de tipo inválido: ${get(key)}")
        value
    }

    Object opt(String key) { get(key) }

    JSONObject getJSONObject(String key) { (JSONObject) get(key) }

    JSONArray getJSONArray(String key) { (JSONArray) get(key) }

    int length() { size() }

    String toString(int indent) { JsonOutput.prettyPrint(toString()) }

    @Override
    String toString() { JsonOutput.toJson((Map) this) }

    /** Converte recursivamente Maps/Lists (ex.: saída do JsonSlurper) em JSONObject/JSONArray. */
    static Object wrap(Object value) {
        if (value instanceof JSONObject || value instanceof JSONArray) return value
        if (value instanceof Map) return new JSONObject((Map) value)
        if (value instanceof Collection) return new JSONArray((Collection) value)
        value
    }
}
