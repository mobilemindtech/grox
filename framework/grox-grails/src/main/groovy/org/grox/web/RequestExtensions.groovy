package org.grox.web

import org.grox.http.GroxRequest

/** Métodos do Grails sobre a requisição do core (extensões do Groovy, registradas em {@code META-INF/groovy}). */
class RequestExtensions {

    /**
     * {@code request.withFormat { form { } '*' { } }}: como no Grails, o bloco é escolhido pelo Content-Type
     * da requisição ({@code form}, {@code multipartForm}, {@code json}...), não pelo Accept. Sem corpo
     * (GET), vale o primeiro bloco.
     */
    static Object withFormat(GroxRequest self, Closure blocks) {
        ControllerSupport.withFormat(RequestContext.current(), blocks, self.format)
    }
}
