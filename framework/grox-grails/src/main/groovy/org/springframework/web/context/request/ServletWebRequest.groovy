package org.springframework.web.context.request

import groovy.transform.CompileStatic
import org.grox.web.WebRequest

/** Vista da requisição corrente no estilo Spring ({@code request}, {@code response}, {@code session}). */
@CompileStatic
class ServletWebRequest implements RequestAttributes {
    final WebRequest context

    ServletWebRequest(WebRequest context) { this.context = context }

    Object getRequest() { context.request }
    Object getResponse() { context.response }
    Object getCurrentRequest() { context.request }
    Object getCurrentResponse() { context.response }
    Object getSession() { context.getSession(true) }
    Object getParams() { context.params }
    Object getParameterMap() { context.params }
    Object getFlashScope() { context.flash }
    Locale getLocale() { context.locale }

    Object getAttribute(String name, int scope) {
        scope == SCOPE_SESSION ? context.getSession(true).getAttribute(name) : context.request.getAttribute(name)
    }

    void setAttribute(String name, Object value, int scope) {
        if (scope == SCOPE_SESSION) context.getSession(true).setAttribute(name, value) else context.request.setAttribute(name, value)
    }

    void removeAttribute(String name, int scope) {
        if (scope == SCOPE_SESSION) context.getSession(true).removeAttribute(name) else context.request.removeAttribute(name)
    }
}
