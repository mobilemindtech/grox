package org.grox.web

/**
 * Requisição corrente, vinculada via {@link ScopedValue} durante a execução da
 * action dentro do ator do controller. O valor é o mesmo do core ({@link org.grox.http.CurrentRequest}):
 * o {@link WebRequest} é a {@link org.grox.http.RequestScope} do módulo Grails.
 */
@groovy.transform.CompileStatic
final class RequestContext {

    static final ScopedValue<org.grox.http.RequestScope> CURRENT = org.grox.http.CurrentRequest.VALUE

    private RequestContext() {}

    static WebRequest current() {
        if (!CURRENT.bound) throw new IllegalStateException('Nenhuma requisição ativa nesta thread')
        (WebRequest) CURRENT.get()
    }

    static WebRequest currentOrNull() {
        CURRENT.bound ? (WebRequest) CURRENT.get() : null
    }

    static <T> T with(WebRequest request, Closure<T> body) {
        org.grox.core.Scoped.where(CURRENT, request, body)
    }
}
