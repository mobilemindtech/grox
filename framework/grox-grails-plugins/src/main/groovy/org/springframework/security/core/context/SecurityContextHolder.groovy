package org.springframework.security.core.context

import org.grox.security.Authentication
import org.grox.security.SecurityFilter
import org.grox.web.RequestContext

/**
 * Compatibilidade com o {@code SecurityContextHolder} do Spring Security sobre a autenticação do Grox:
 * {@code getContext().authentication} é a autenticação corrente; {@code setAuthentication(auth)} a grava na
 * sessão da requisição (como um login); {@code clearContext()} a remove (logout).
 */
class SecurityContextHolder {

    static SecurityContext getContext() { new GroxSecurityContext() }

    static void clearContext() {
        def wr = RequestContext.currentOrNull()
        if (wr == null) return
        wr.getSession(false)?.removeAttribute(SecurityFilter.SESSION_AUTH)
        wr.request.removeAttribute(org.grox.security.SecurityContextHolder.REQUEST_ATTR)
    }

    static class GroxSecurityContext implements SecurityContext {
        @Override
        Object getAuthentication() { org.grox.security.SecurityContextHolder.authentication }

        @Override
        void setAuthentication(Object authentication) {
            if (authentication == null) {
                clearContext()
                return
            }
            def wr = RequestContext.currentOrNull()
            if (wr == null || !(authentication instanceof Authentication)) return
            def s = wr.getSession(true)
            def before = (Authentication) s.getAttribute(SecurityFilter.SESSION_AUTH)
            // outro usuário na sessão: id e token CSRF novos, como no login (session fixation)
            if (before?.principal?.username != ((Authentication) authentication).principal?.username) {
                SecurityFilter.establish(s, (Authentication) authentication)
            } else {
                s.setAttribute(SecurityFilter.SESSION_AUTH, authentication)
            }
            wr.request.setAttribute(org.grox.security.SecurityContextHolder.REQUEST_ATTR, authentication)
        }
    }
}
