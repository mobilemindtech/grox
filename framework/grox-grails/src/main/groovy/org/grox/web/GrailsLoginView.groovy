package org.grox.web

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.security.LoginPage
import org.grox.security.LoginView

/** Página de login: {@code views/login/auth.gsp} da aplicação, se existir; senão a padrão. */
class GrailsLoginView implements LoginView {

    final WebContext ctx

    GrailsLoginView(WebContext ctx) {
        this.ctx = ctx
    }

    @Override
    GroxResponse render(GroxRequest req, Map<String, Object> model) {
        // como o LoginController do plugin: /login/auth chega com controller=login e action=auth
        List<String> segs = req.path.tokenize('/')
        def wr = new WebRequest(req, ctx, null, segs.size() > 1 ? segs[1] : 'auth', [:], segs ? segs[0] : 'login')
        Map<String, Object> m = new LinkedHashMap(model)
        m.errorMessage = model.errorCode ? ctx.messages.getMessage(model.errorCode.toString(), null, null, wr.locale) : null
        if (ctx.views?.exists('/login/auth')) {
            // sessão de dados aberta como nas actions: layout e TagLibs da página de login podem consultar o banco
            RequestContext.with(wr) { ControllerRouter.inView(req) { ctx.viewRenderer.render('/login/auth', m, wr) } }
            return wr.response
        }
        wr.response.contentType = 'text/html'
        wr.response.setHeader('Cache-Control', 'no-store')
        wr.response.writer.write(LoginPage.render(m, ctx.messages, wr.locale))
        wr.response
    }
}
