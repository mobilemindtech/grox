package org.grox.security

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse

/**
 * Renderiza a página de login (fora do event loop). O módulo Grails usa a {@code views/login/auth.gsp}
 * da aplicação, se houver; sem implementação, vale a página padrão ({@link LoginPage}).
 */
interface LoginView {
    /** @param model {@code postUrl}, {@code csrfToken}, {@code errorCode}... ({@link SecurityFilter#loginModel}) */
    GroxResponse render(GroxRequest req, Map<String, Object> model)
}
