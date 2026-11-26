package org.grox.data

import grails.gorm.multitenancy.Tenants
import org.grox.core.ContextProvider

/** Sessão de dados (só para services-ator) e tenant; cada tarefa assíncrona abre a sua sessão sob demanda. */
class GormContextProvider implements ContextProvider {

    @Override
    List<ScopedValue> actorValues() { [Persistence.CURRENT, Tenants.CURRENT] }

    @Override
    List<ScopedValue> taskValues() { [Tenants.CURRENT] }

    @Override
    <T> Closure<T> wrapTask(Closure<T> body) {
        return { -> Persistence.withSessionIfAvailable(body) }
    }
}
