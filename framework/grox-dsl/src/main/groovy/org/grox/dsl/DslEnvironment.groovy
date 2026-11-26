package org.grox.dsl

import org.grox.http.StaticResources
import org.grox.i18n.MessageSource
import org.grox.routing.LinkGenerator
import org.grox.session.SessionRepository
import org.grox.view.GspViews

/** O que as rotas usam da aplicação, montado uma vez pelo {@link DslModule}. */
class DslEnvironment {
    SessionRepository sessions
    MessageSource messages
    StaticResources statics
    GspViews views
    LinkGenerator links
    /** A {@code GroxApplication}. */
    Object application
    /** A configuração da aplicação ({@code config.getProperty('x', Integer)} nos handlers). */
    org.grox.core.Config config
    /** Services e beans pelo nome: os handlers os acessam como propriedades. */
    Map<String, Object> beans = [:]
}
