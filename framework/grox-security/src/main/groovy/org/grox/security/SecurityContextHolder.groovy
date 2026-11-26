package org.grox.security

import org.grox.http.CurrentRequest

/** Autenticação corrente: vinculada na execução da action e propagada a services-ator. */
class SecurityContextHolder {

    static final ScopedValue<Authentication> CURRENT = ScopedValue.newInstance()
    /** A autenticação na requisição; também o {@code request.userPrincipal}. */
    static final String REQUEST_ATTR = org.grox.http.GroxRequest.PRINCIPAL_ATTRIBUTE

    /**
     * Na thread da requisição vale o atributo da requisição, que acompanha login/logout feitos durante a action
     * ({@code reauthenticate}, {@code SecurityContextHolder.clearContext()} do Spring); fora dela (services-ator,
     * tarefas), a autenticação propagada no ScopedValue.
     */
    static Authentication getAuthentication() {
        def scope = CurrentRequest.orNull()
        if (scope != null) return (Authentication) scope.request?.getAttribute(REQUEST_ATTR)
        CURRENT.bound ? CURRENT.get() : null
    }

    static <T> T with(Authentication auth, Closure<T> body) {
        auth == null ? body.call() : org.grox.core.Scoped.where(CURRENT, auth, body)
    }
}
