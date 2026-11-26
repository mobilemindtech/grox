package org.grox.dsl

import org.grox.data.Persistence
import org.grox.http.CurrentRequest
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.security.LoginView

/** Página de login de uma aplicação do {@code grox-dsl}: a {@code views/login/auth.gsp}, com o modelo da segurança. */
class DslLoginView implements LoginView {

    final DslEnvironment env

    DslLoginView(DslEnvironment env) {
        this.env = env
    }

    @Override
    GroxResponse render(GroxRequest req, Map<String, Object> model) {
        def route = new Route(group: new RouteGroup('login', null), action: 'auth', httpMethod: 'GET', path: req.path)
        def ctx = new RouteContext(req, route, [:], env)
        Map<String, Object> m = new LinkedHashMap(model)
        m.errorMessage = model.errorCode ? env.messages.getMessage(model.errorCode.toString(), null, null, ctx.locale) : null
        ctx.response.setHeader('Cache-Control', 'no-store')
        // sessão de dados aberta como nas rotas: layout e TagLibs da página podem consultar o banco
        CurrentRequest.with(ctx) { Persistence.withSessionIfAvailable { ctx.render(view: '/login/auth', model: m) } }
        ctx.response
    }
}
