package org.grox.plugins.springsecurity

import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.rest.JwtService
import org.grox.core.GroxModule
import org.grox.security.Passwords
import org.grox.security.SecurityModule
import org.grox.web.GrailsModule

/**
 * API dos plugins spring-security-core e rest sobre a segurança do Grox: beans
 * {@code springSecurityService}, {@code authenticationTrustResolver}, {@code jwtService},
 * e {@code passwordEncoder}/{@code userDetailsPasswordService} da aplicação.
 */
class SpringSecurityModule extends GroxModule implements org.grox.view.GspContributor {

    SpringSecurityService springSecurityService
    /** O service da geração corrente com a segurança ativa ({@code authenticatedUser} dos controllers), ou null. */
    static volatile SpringSecurityService current

    int getOrder() { 600 }

    /** Tags {@code sec:}. */
    List<Class<?>> getTagLibClasses() { [SecurityTagLib] }

    @Override
    void initialize() {
        // userDetailsService da aplicação no estilo do Spring Security (UserDetails/GrailsUser)
        def securityModule = runtime.module(SecurityModule)
        securityModule?.userDetailsAdapter = { Object bean ->
            bean instanceof org.springframework.security.core.userdetails.UserDetailsService ?
                    new SpringUserDetailsAdapter((org.springframework.security.core.userdetails.UserDetailsService) bean) : null
        }
        // a cada geração (o filtro pode vir da anterior): exceção do login na sessão, RestTokenCreationEvent e @Secured
        securityModule?.security?.listeners?.put('springSecurity', new SpringSecurityEvents(runtime.module(org.grox.web.GrailsModule)?.grailsApplication))
        securityModule?.security?.ruleSources?.put('secured', new SecuredRules())
        if (runtime.handoff?.keepData) {
            springSecurityService = previous(SpringSecurityModule)?.springSecurityService
            return
        }
        def security = runtime.module(SecurityModule)?.security
        if (!security) return
        String userClass = security.cfg.userDomainClassName
        springSecurityService = new SpringSecurityService(securityFilter: security,
                userClass: userClass ? Class.forName(userClass, true, runtime.application.classLoader) : null)
    }

    @Override
    void contributeBeans(Map<String, Object> beans) {
        def security = runtime.module(SecurityModule)?.security
        if (springSecurityService) {
            beans.springSecurityService = springSecurityService
            beans.authenticationTrustResolver = new org.springframework.security.authentication.AuthenticationTrustResolver()
            grails.plugin.springsecurity.SpringSecurityUtils.current = security?.cfg
        }
        if (security?.jwt) beans.jwtService = new JwtService(jwt: security.jwt)
    }

    /**
     * {@code passwordEncoder} e {@code userDetailsPasswordService} da aplicação (Spring Security), direto
     * ou como propriedades de um {@code DaoAuthenticationProvider}.
     */
    @Override
    void beansReady(Map<String, Object> beans) {
        def dao = beans.values().find { it instanceof org.springframework.security.authentication.dao.DaoAuthenticationProvider }
        def encoder = beans.passwordEncoder ?: dao?.passwordEncoder
        def service = beans.userDetailsPasswordService ?: dao?.userDetailsPasswordService
        Passwords.custom = encoder instanceof org.springframework.security.crypto.password.PasswordEncoder ?
                new SpringPasswords.Codec((org.springframework.security.crypto.password.PasswordEncoder) encoder) : null
        Passwords.passwordService = service instanceof org.springframework.security.core.userdetails.UserDetailsPasswordService ?
                new SpringPasswords.Updater((org.springframework.security.core.userdetails.UserDetailsPasswordService) service) : null
    }

    @Override
    void configureWeb() {
        SecurityTagLib.filter = runtime.module(org.grox.security.SecurityModule)?.security
        def web = runtime.module(GrailsModule)?.webContext
        if (web == null) return
        org.springframework.security.core.session.SessionRegistryImpl.repository = web.sessions
        current = web.security ? springSecurityService : null
    }
}
