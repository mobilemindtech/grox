package org.grox.http

/**
 * A requisição em atendimento, vinculada à thread por {@link CurrentRequest} enquanto o código da
 * aplicação roda (action, view, endpoint). No módulo Grails é o {@code WebRequest}.
 */
interface RequestScope {
    GroxRequest getRequest()

    GroxResponse getResponse()

    /** Locale da requisição (parâmetro {@code lang}, sessão ou {@code Accept-Language}). */
    Locale getLocale()

    GroxSession getSession(boolean create)
}
