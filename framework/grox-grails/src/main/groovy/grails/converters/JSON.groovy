package grails.converters

import groovy.json.JsonSlurper
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.web.json.JsonSupport

/** Emulação de {@code grails.converters.JSON}: {@code render usuario as JSON}. */
class JSON {

    final Object target

    JSON(Object target) {
        this.target = target
    }

    @Override
    String toString() {
        JsonSupport.toJson(target)
    }

    /** Como no Grails, fecha o writer depois de escrever: um segundo render na mesma resposta é descartado. */
    void render(GroxResponse response) {
        response.contentType = 'application/json'
        response.writer.write(toString())
        response.closeWriter()
        response.committed = true
    }

    /** Como no Grails: objetos viram {@code JSONObject}, arrays {@code JSONArray}. */
    static Object parse(String text) {
        org.grails.web.json.JSONObject.wrap(new JsonSlurper().parseText(text))
    }

    static Object parse(Reader reader) {
        org.grails.web.json.JSONObject.wrap(new JsonSlurper().parse(reader))
    }

    static Object parse(GroxRequest request) {
        request.JSON
    }
}
