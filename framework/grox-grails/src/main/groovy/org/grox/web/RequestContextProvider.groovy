package org.grox.web

import org.grox.core.ContextProvider

/** A requisição de quem criou a tarefa assíncrona ({@code params}, {@code session}, render no fim). */
class RequestContextProvider implements ContextProvider {

    @Override
    List<ScopedValue> taskValues() { [RequestContext.CURRENT] }
}
