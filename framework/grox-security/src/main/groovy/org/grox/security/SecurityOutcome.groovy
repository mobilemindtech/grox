package org.grox.security

import org.grox.http.GroxResponse

/** Decisão do filtro de segurança quando a requisição não segue para o controller. */
class SecurityOutcome {

    GroxResponse response
    Integer errorStatus
    String message
    Map<String, String> headers = [:]
    /** Página de login a renderizar (GSP da aplicação ou padrão). */
    Map<String, Object> loginModel

    static SecurityOutcome redirect(String url) {
        def r = new GroxResponse()
        r.sendRedirect(url)
        new SecurityOutcome(response: r)
    }

    static SecurityOutcome error(int status, String message, Map<String, String> headers = [:]) {
        new SecurityOutcome(errorStatus: status, message: message, headers: headers)
    }

    static SecurityOutcome json(int status, String json) {
        def r = new GroxResponse(status: status, contentType: 'application/json')
        r.setHeader('Cache-Control', 'no-store')
        r.writer.write(json)
        r.committed = true
        new SecurityOutcome(response: r)
    }

    GroxResponse errorResponse() {
        def r = new GroxResponse()
        headers.each { k, v -> r.setHeader(k, v) }
        r.sendError(errorStatus, message)
        r
    }
}
