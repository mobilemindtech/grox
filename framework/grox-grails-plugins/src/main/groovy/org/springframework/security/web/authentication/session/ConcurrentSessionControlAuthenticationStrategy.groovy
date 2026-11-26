package org.springframework.security.web.authentication.session

import org.slf4j.LoggerFactory

/**
 * Compatibilidade com o {@code ConcurrentSessionControlAuthenticationStrategy}: aceito como configuração
 * (resources.groovy). {@code maximumSessions = -1} (sem limite) é o comportamento do Grox; limite de sessões
 * por usuário ainda não é aplicado.
 */
class ConcurrentSessionControlAuthenticationStrategy {
    final Object sessionRegistry
    int maximumSessions = 1
    boolean exceptionIfMaximumExceeded

    ConcurrentSessionControlAuthenticationStrategy(Object sessionRegistry) { this.sessionRegistry = sessionRegistry }

    void setMaximumSessions(int max) {
        this.maximumSessions = max
        if (max > 0) LoggerFactory.getLogger(ConcurrentSessionControlAuthenticationStrategy).warn("maximumSessions = ${max}: limite de sessões por usuário ainda não é aplicado no Grox")
    }
}
