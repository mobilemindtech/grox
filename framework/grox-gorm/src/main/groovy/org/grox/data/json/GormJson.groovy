package org.grox.data.json

import groovy.json.JsonGenerator
import org.grox.data.DomainJson
import org.grox.data.GormEntity
import org.grox.data.validation.FieldError
import org.grox.data.validation.ValidationErrors
import org.grox.http.CurrentRequest
import org.grox.i18n.MessageSource

import java.time.temporal.TemporalAccessor
import java.util.function.Function

/**
 * JSON das aplicações: entidades rasas (associações como {@code {id: x}}), erros de validação no formato
 * do Grails com as mensagens traduzidas no locale da requisição, datas e {@code java.time}. Usado pelo
 * {@code render json:}/{@code as JSON} do módulo Grails e pelas rotas do {@code grox-dsl}.
 */
class GormJson {

    /** As mensagens da aplicação ({@code messages*.properties}); definidas pelo {@code GormModule}. */
    static volatile MessageSource messages

    /** Mensagem de um erro de validação no locale da requisição corrente (sem requisição, a padrão do erro). */
    static final Function<FieldError, String> REQUEST_MESSAGES = { FieldError fe ->
        def scope = CurrentRequest.orNull()
        MessageSource m = messages
        m != null && scope != null ? m.getMessage(fe, scope.locale) : null
    } as Function<FieldError, String>

    static final JsonGenerator GENERATOR = new JsonGenerator.Options()
            .disableUnicodeEscaping()
            .dateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            .excludeFieldsByName('metaClass', 'class', 'log')
            .addConverter(TemporalAccessor) { TemporalAccessor t -> t.toString() }
            .addConverter(UUID) { UUID u -> u.toString() }
            .addConverter(new JsonGenerator.Converter() {
                boolean handles(Class<?> type) { GormEntity.isAssignableFrom(type) }

                Object convert(Object value, String key) { DomainJson.entity(value) }
            })
            .addConverter(ValidationErrors) { ValidationErrors e -> DomainJson.errors(e, REQUEST_MESSAGES) }
            .build()

    /**
     * JSON seguro também dentro de {@code <script>}: {@code </} vira {@code <\/} (senão um dado com
     * {@code </script>} fecha a tag) e U+2028/U+2029 viram escapes (quebram JavaScript antigo).
     * Os dois são escapes válidos de JSON: quem consome a API não percebe diferença.
     */
    static String toJson(Object value) {
        safeForScript(GENERATOR.toJson(value))
    }

    static String safeForScript(String json) {
        if (json.indexOf('</') < 0 && json.indexOf('\u2028' as String) < 0 && json.indexOf('\u2029' as String) < 0) return json
        json.replace('</', '<\\/').replace('\u2028', '\\u2028').replace('\u2029', '\\u2029')
    }
}
