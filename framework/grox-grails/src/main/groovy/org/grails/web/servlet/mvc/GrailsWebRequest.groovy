package org.grails.web.servlet.mvc

import groovy.transform.CompileStatic
import org.grox.web.RequestContext
import org.grox.web.WebRequest
import org.springframework.web.context.request.ServletWebRequest

/** Compatibilidade com {@code GrailsWebRequest}: a requisição corrente no estilo Grails. */
@CompileStatic
class GrailsWebRequest extends ServletWebRequest {

    GrailsWebRequest(WebRequest context) {
        super(context)
    }

    /** A requisição corrente, ou null fora de uma requisição. */
    static GrailsWebRequest lookup() {
        WebRequest wr = RequestContext.currentOrNull()
        wr == null ? null : new GrailsWebRequest(wr)
    }

    String getControllerName() { context.controllerName }

    String getActionName() { context.actionName }
}
