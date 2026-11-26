package org.springframework.security.core.session

/** Uma sessão autenticada (Spring Security): id, principal e último acesso. */
class SessionInformation implements Serializable {
    final Object principal
    final String sessionId
    final Date lastRequest
    boolean expired

    SessionInformation(Object principal, String sessionId, Date lastRequest) {
        this.principal = principal
        this.sessionId = sessionId
        this.lastRequest = lastRequest
    }

    /** Encerra a sessão (no Spring, o {@code ConcurrentSessionFilter} desloga na próxima requisição). */
    void expireNow() {
        expired = true
        SessionRegistryImpl.repository?.delete(sessionId)
    }
}
