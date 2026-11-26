package org.grox.security

import org.grox.core.ContextProvider

/** Autenticação de quem chama o service-ator ou cria a tarefa. */
class SecurityContextProvider implements ContextProvider {

    @Override
    List<ScopedValue> actorValues() { [SecurityContextHolder.CURRENT] }

    @Override
    List<ScopedValue> taskValues() { [SecurityContextHolder.CURRENT] }

    /** Autenticação da requisição, mesmo se ainda não vinculada em ScopedValue. */
    @Override
    void completeTask(Map<ScopedValue, Object> snapshot) {
        if (!snapshot.containsKey(SecurityContextHolder.CURRENT) && SecurityContextHolder.authentication != null) {
            snapshot[SecurityContextHolder.CURRENT] = SecurityContextHolder.authentication
        }
    }
}
