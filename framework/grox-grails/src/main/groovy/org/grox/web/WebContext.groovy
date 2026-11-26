package org.grox.web

import org.grox.session.SessionRepository
import org.grox.http.StaticResources
import org.grox.assets.AssetServer
import org.grox.i18n.MessageSource
import org.grox.security.SecurityFilter
import org.grox.management.AccessLog
import org.grox.management.Management
import org.grox.http.Endpoint
import org.grox.web.interceptor.InterceptorRegistry
import org.grox.view.GspViews
import org.grox.routing.LinkGenerator
import org.grox.routing.UrlMappingsHolder
import org.grox.view.ViewRenderer

/** Componentes compartilhados pela camada web. */
class WebContext {
    GroxGrailsApplication application
    SessionRepository sessions
    UrlMappingsHolder urlMappings
    LinkGenerator links
    ViewRenderer viewRenderer
    GspViews views
    MessageSource messages
    StaticResources statics
    AssetServer assets
    SecurityFilter security
    InterceptorRegistry interceptors
    Management management
    /** grox.async.timeout: limite das actions assíncronas. */
    int asyncTimeoutSeconds = 60
    /** Cabeçalhos de segurança acrescentados às respostas que não os definem ({@code grox.http.securityHeaders}). */
    Map<String, String> securityHeaders = [:]
    /** Endpoints especiais (management, console), atendidos antes das rotas. */
    List<Endpoint> endpoints = []
    AccessLog accessLog
    Map<String, ControllerArtefact> controllers = [:]

    /** O endpoint especial do caminho, ou null. */
    Endpoint endpointFor(String path) {
        for (Endpoint e : endpoints) if (e.handles(path)) return e
        null
    }

    boolean isDevelopment() {
        application.application.development
    }
}
