package grails.plugin.springsecurity.web

import org.grox.web.RequestContext

/** Compatibilidade com o {@code SecurityRequestHolder} do spring-security-core: requisição e resposta correntes. */
class SecurityRequestHolder {
    static Object getRequest() { RequestContext.currentOrNull()?.request }

    static Object getResponse() { RequestContext.currentOrNull()?.response }
}
