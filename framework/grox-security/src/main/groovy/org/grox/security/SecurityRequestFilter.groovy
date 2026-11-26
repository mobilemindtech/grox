package org.grox.security

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.RequestFilter
import org.grox.http.ResourceHandler
import org.grox.i18n.MessageSource
import org.grox.routing.RouteMatch

/** O {@link SecurityFilter} no pipeline HTTP do core. */
class SecurityRequestFilter implements RequestFilter {

    final SecurityFilter security
    /** Página de login da aplicação (módulo Grails); null: a padrão. */
    LoginView loginView

    SecurityRequestFilter(SecurityFilter security) {
        this.security = security
    }

    @Override
    boolean bypasses(GroxRequest req, ResourceHandler resource) {
        security.bypasses(req, resource?.name == 'assets', resource?.name == 'static')
    }

    @Override
    GroxResponse process(GroxRequest req, RouteMatch match) {
        SecurityOutcome o = security.process(req, match)
        if (o == null) return null
        if (o.response != null) return o.response
        if (o.loginModel != null) return loginView != null ? loginView.render(req, o.loginModel) : defaultLogin(req, o.loginModel)
        o.errorResponse()
    }

    @Override
    boolean ownsPath(String path) {
        security.cfg.isFrameworkUrl(path)
    }

    /** {@link LoginPage} no locale do {@code Accept-Language}. */
    private GroxResponse defaultLogin(GroxRequest req, Map<String, Object> model) {
        MessageSource messages = security.messages
        Locale locale = req.locale
        Map<String, Object> m = new LinkedHashMap(model)
        m.errorMessage = model.errorCode && messages ? messages.getMessage(model.errorCode.toString(), null, null, locale) : null
        def resp = new GroxResponse(contentType: 'text/html')
        resp.setHeader('Cache-Control', 'no-store')
        resp.writer.write(LoginPage.render(m, messages, locale))
        resp
    }
}
