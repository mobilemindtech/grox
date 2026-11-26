package org.grox.view

import org.grox.http.FlashScope
import org.grox.http.GroxParams
import org.grox.http.GroxSession
import org.grox.http.RequestScope
import org.grox.http.StaticResources
import org.grox.i18n.MessageSource
import org.grox.routing.LinkGenerator

/**
 * A requisição vista pelas GSPs e TagLibs: o {@link RequestScope} do core mais o que a renderização
 * usa (parâmetros, flash, links, mensagens, estáticos). No módulo Grails é o {@code WebRequest}.
 */
interface ViewRequest extends RequestScope {
    GroxParams getParams()

    /** A sessão, criada se preciso. */
    GroxSession getSession()

    FlashScope getFlash()

    String getControllerName()

    String getActionName()

    /** Classe do controller da action ({@code static layout}), ou null. */
    Class<?> getControllerClass()

    LinkGenerator getLinks()

    MessageSource getMessages()

    StaticResources getStatics()

    GspViews getViews()

    /** A aplicação ({@code grailsApplication} nas GSPs), ou null. */
    Object getApplication()
}
