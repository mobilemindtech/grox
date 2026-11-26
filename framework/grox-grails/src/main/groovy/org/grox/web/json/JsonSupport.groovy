package org.grox.web.json

import groovy.json.JsonGenerator
import org.grox.data.json.GormJson
import org.grox.data.validation.FieldError

/** JSON do módulo Grails ({@code render json:}, {@code as JSON}): o mesmo do GORM ({@link GormJson}). */
class JsonSupport {

    /** Mensagens dos erros de validação pelo {@code messages*.properties} e o locale da requisição. */
    static final java.util.function.Function<FieldError, String> REQUEST_MESSAGES = GormJson.REQUEST_MESSAGES

    static final JsonGenerator GENERATOR = GormJson.GENERATOR

    static String toJson(Object value) {
        GormJson.toJson(value)
    }

    static String safeForScript(String json) {
        GormJson.safeForScript(json)
    }
}
