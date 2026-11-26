package org.grox.dsl

import org.grox.http.CurrentRequest
import org.grox.http.FlashScope
import org.grox.http.GroxParams
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.GroxSession

/** API das classes {@link Handler} (aplicada pela anotação): a requisição corrente e as respostas do {@link RouteContext}. */
trait HandlerApi {

    RouteContext getRouteContext() {
        Object scope = CurrentRequest.orNull()
        if (!(scope instanceof RouteContext)) throw new IllegalStateException('Nenhuma rota do grox-dsl em atendimento nesta thread')
        (RouteContext) scope
    }

    GroxParams getParams() { routeContext.params }

    GroxRequest getRequest() { routeContext.request }

    GroxResponse getResponse() { routeContext.response }

    GroxSession getSession() { routeContext.session }

    FlashScope getFlash() { routeContext.flash }

    Locale getLocale() { routeContext.locale }

    org.grox.core.Config getConfig() { routeContext.config }

    void render(Map args) { routeContext.render(args) }

    void render(CharSequence text) { routeContext.render(text) }

    void render(Map args, Closure body) { routeContext.render(args, body) }

    void render(Closure body) { routeContext.render(body) }

    void respond(Object value, Map args = [:]) { routeContext.respond(value, args) }

    void respond(Map args, Object value) { routeContext.respond(value, args) }

    void redirect(Map args) { routeContext.redirect(args) }

    void redirect(CharSequence url) { routeContext.redirect(url) }
}
