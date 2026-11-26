package grails.plugin.springsecurity.rest

import grails.plugin.springsecurity.rest.token.AccessToken
import org.springframework.context.ApplicationEvent

/** Publicado a cada token criado no login REST ({@code /api/login}), como no spring-security-rest. */
class RestTokenCreationEvent extends ApplicationEvent {

    RestTokenCreationEvent(AccessToken accessToken) {
        super(accessToken)
    }

    AccessToken getAccessToken() { (AccessToken) source }
}
