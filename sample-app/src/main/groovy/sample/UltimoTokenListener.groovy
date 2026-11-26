package sample

import grails.plugin.springsecurity.rest.RestTokenCreationEvent
import org.springframework.context.ApplicationListener
import org.springframework.security.core.context.SecurityContextHolder

/** Como o TokenCreationListener do app-c-api: recebe o RestTokenCreationEvent a cada login REST. */
class UltimoTokenListener implements ApplicationListener<RestTokenCreationEvent> {
    volatile String username
    volatile String accessToken
    /** Usuário do contexto de segurança durante o evento (o resolver de tenant do app C depende dele). */
    volatile String autenticado

    @Override
    void onApplicationEvent(RestTokenCreationEvent event) {
        username = event.accessToken.principal.username
        accessToken = event.accessToken.accessToken
        autenticado = SecurityContextHolder.context.authentication?.name
    }
}
