package org.grails.web.json

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

/** Compatibilidade com o {@code JSONArray} do Grails: um {@link ArrayList} cujo {@code toString()} é o JSON. */
@CompileStatic
class JSONArray extends ArrayList<Object> {

    JSONArray() {}

    JSONArray(Collection values) {
        values?.each { add(JSONObject.wrap(it)) }
    }

    JSONArray(String json) {
        this((Collection) new JsonSlurper().parseText(json))
    }

    JSONArray(Reader reader) {
        this((Collection) new JsonSlurper().parse(reader))
    }

    JSONObject getJSONObject(int i) { (JSONObject) get(i) }

    JSONArray getJSONArray(int i) { (JSONArray) get(i) }

    String getString(int i) { get(i)?.toString() }

    Object opt(int i) { i >= 0 && i < size() ? get(i) : null }

    JSONArray put(Object value) { add(JSONObject.wrap(value)); this }

    int length() { size() }

    String toString(int indent) { JsonOutput.prettyPrint(toString()) }

    @Override
    String toString() { JsonOutput.toJson((Collection) this) }
}
