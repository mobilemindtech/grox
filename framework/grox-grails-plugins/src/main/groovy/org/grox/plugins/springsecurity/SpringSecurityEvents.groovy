package org.grox.plugins.springsecurity

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.rest.RestTokenCreationEvent
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.core.GrailsApplication
import org.grox.http.GroxSession
import org.grox.security.GroxUser
import org.grox.security.SecurityListener
import org.springframework.security.web.WebAttributes

/**
 * Como o spring-security-core e o rest: a exceção do login recusado e o username na sessão (para o
 * LoginController/authfail da app) e o {@code RestTokenCreationEvent} aos {@code ApplicationListener}.
 */
class SpringSecurityEvents implements SecurityListener {

    /** Null sem o módulo Grails (sem eventos no mainContext). */
    final GrailsApplication application

    SpringSecurityEvents(GrailsApplication application) {
        this.application = application
    }

    @Override
    void loginFailed(GroxSession session, String error, String username) {
        session.setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION, exception(error))
        session.setAttribute(SpringSecurityUtils.SPRING_SECURITY_LAST_USERNAME_KEY, username)
    }

    @Override
    void loginSucceeded(GroxSession session) {
        session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION)
    }

    @Override
    void tokenIssued(GroxUser user, String accessToken, String refreshToken, Integer expiration) {
        def main = application?.mainContext
        if (main == null) return
        main.publishEvent(new RestTokenCreationEvent(new AccessToken(accessToken: accessToken, refreshToken: refreshToken,
                expiration: expiration, principal: user)))
    }

    static RuntimeException exception(String error) {
        switch (error) {
            case 'springSecurity.errors.login.disabled': return new org.springframework.security.authentication.DisabledException(error)
            case 'springSecurity.errors.login.locked': return new org.springframework.security.authentication.LockedException(error)
            case 'springSecurity.errors.login.expired': return new org.springframework.security.authentication.AccountExpiredException(error)
            case 'springSecurity.errors.login.passwordExpired': return new org.springframework.security.authentication.CredentialsExpiredException(error)
            default: return new org.springframework.security.authentication.BadCredentialsException(error ?: 'springSecurity.errors.login.fail')
        }
    }
}
