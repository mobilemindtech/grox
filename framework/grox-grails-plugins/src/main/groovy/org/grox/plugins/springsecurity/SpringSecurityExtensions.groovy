package org.grox.plugins.springsecurity

import grails.plugin.springsecurity.userdetails.GrailsUser
import org.grox.security.GroxUser

/** Extensões do Groovy para a API do spring-security-core (registradas em {@code META-INF/groovy}). */
class SpringSecurityExtensions {

    /**
     * {@code principal as GrailsUser} (ou {@code as UserDetails}/{@code User}), como no spring-security-core;
     * a senha não acompanha (vazia).
     */
    static Object asType(GroxUser self, Class type) {
        if (type.isInstance(self)) return self
        if (type.isAssignableFrom(GrailsUser)) {
            return new GrailsUser(self.username, self.password ?: '', self.enabled, !self.accountExpired,
                    !self.passwordExpired, !self.accountLocked, self.authorities, self.id)
        }
        org.codehaus.groovy.runtime.DefaultGroovyMethods.asType((Object) self, type)
    }

    /** {@code authenticatedUser} dos controllers: a domain class do usuário logado, como no spring-security-core. */
    static Object getAuthenticatedUser(org.grox.web.api.ControllerApi self) {
        SpringSecurityModule.current?.currentUser
    }
}
