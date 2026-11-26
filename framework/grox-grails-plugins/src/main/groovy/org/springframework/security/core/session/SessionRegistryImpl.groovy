package org.springframework.security.core.session

import org.grox.http.GroxSession
import org.grox.security.SecurityFilter
import org.grox.session.SessionRepository

/**
 * Compatibilidade com o {@code SessionRegistryImpl} do Spring Security sobre as sessões do Grox (memória ou
 * Redis): {@code allPrincipals} são os usuários das sessões autenticadas ativas. O registro é implícito (não é
 * preciso chamar {@code registerNewSession}).
 */
class SessionRegistryImpl {

    /** Repositório de sessões da aplicação, definido pelo Grox ao subir. */
    static volatile SessionRepository repository

    List<Object> getAllPrincipals() {
        authenticated().collect { it.principal }.unique { it.toString() }
    }

    List<SessionInformation> getAllSessions(Object principal, boolean includeExpiredSessions) {
        authenticated().findAll { it.principal == principal || it.principal?.toString() == principal?.toString() }
    }

    SessionInformation getSessionInformation(String sessionId) {
        authenticated().find { it.sessionId == sessionId }
    }

    void registerNewSession(String sessionId, Object principal) {}

    void removeSessionInformation(String sessionId) {}

    void refreshLastRequest(String sessionId) {}

    private List<SessionInformation> authenticated() {
        (repository?.listActive() ?: []).findResults { GroxSession s ->
            def auth = s.getAttribute(SecurityFilter.SESSION_AUTH)
            auth?.principal ? new SessionInformation(auth.principal, s.id, new Date(s.lastAccessedTime)) : null
        }
    }
}
