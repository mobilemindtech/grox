package org.grox.view.taglib

import org.grox.http.Html
import org.grox.i18n.MessageResolvable

/**
 * Tags {@code g:} de erros de validação. Os erros seguem a API {@code Errors} do Spring ({@code hasErrors},
 * {@code allErrors}, {@code getFieldErrors(campo)}, {@code getFieldError(campo)}), que o GORM implementa;
 * a TagLib os lê dinamicamente e traduz cada erro como {@link MessageResolvable}, sem depender do GORM.
 */
class ValidationTagLib implements TagLibApi {

    /** {@code <g:hasErrors bean="${u}" field="nome">...</g:hasErrors>} */
    def hasErrors = { Map attrs, body ->
        if (errorsOf(attrs)) out << body()
    }

    /** {@code <g:eachError bean="${u}" var="erro"><li><g:message error="${erro}"/></li></g:eachError>} */
    def eachError = { Map attrs, body ->
        String var = attrs.var?.toString() ?: 'it'
        errorsOf(attrs).each { Object e -> out << body([(var): e]) }
    }

    /** {@code <g:renderErrors bean="${u}"/>} → {@code <ul class="errors"><li>...</li></ul>} */
    def renderErrors = { Map attrs ->
        List errors = errorsOf(attrs)
        if (!errors) return
        out << '<ul class="errors">'
        errors.each { Object e -> out << '<li>' << Html.escape(resolve(e)) << '</li>' }
        out << '</ul>'
    }

    /** Valor do campo (o valor rejeitado, se houver erro), codificado em HTML. */
    def fieldValue = { Map attrs ->
        Object bean = attrs.bean
        String field = attrs.field?.toString()
        if (bean == null || !field) return
        Object fe = errorsObject(bean)?.getFieldError(field)
        Object value = fe != null ? fe.rejectedValue : field.tokenize('.').inject(bean) { Object o, String p -> o?."$p" }
        out << Html.escape(value)
    }

    private List errorsOf(Map attrs) {
        List beans = attrs.containsKey('bean') ? [attrs.bean] : (attrs.model instanceof Map ? ((Map) attrs.model).values().toList() : [])
        String field = attrs.field?.toString()
        List result = []
        beans.each { Object b ->
            Object errors = isErrors(b) ? b : errorsObject(b)
            if (errors?.hasErrors()) result.addAll(field ? errors.getFieldErrors(field) : errors.allErrors)
        }
        result
    }

    /** O próprio objeto de erros ({@code bean="${livro.errors}"}). */
    private static boolean isErrors(Object o) {
        o != null && o.respondsTo('getAllErrors') && o.respondsTo('getFieldErrors', String)
    }

    private static Object errorsObject(Object bean) {
        bean != null && bean.respondsTo('hasErrors') && bean.hasErrors() ? bean.errors : null
    }

    private String resolve(Object e) {
        e instanceof MessageResolvable ? webRequest.messages.getMessage((MessageResolvable) e, webRequest.locale) : e?.toString()
    }
}
