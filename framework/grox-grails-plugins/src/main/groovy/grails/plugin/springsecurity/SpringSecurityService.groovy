package grails.plugin.springsecurity

import org.grox.data.Gorm
import org.grox.security.Authentication
import org.grox.security.GroxUser
import org.grox.security.Passwords
import org.grox.security.SecurityContextHolder
import org.grox.security.SecurityFilter
import org.grox.web.RequestContext

/** Bean {@code springSecurityService}, com a API do spring-security-core. */
class SpringSecurityService {

    SecurityFilter securityFilter
    Class<?> userClass

    Authentication getAuthentication() {
        SecurityContextHolder.authentication
    }

    /** Como no Spring Security: sem login, o principal é a String {@code 'anonymousUser'}. */
    Object getPrincipal() {
        authentication?.principal ?: ANONYMOUS_USER
    }

    static final String ANONYMOUS_USER = 'anonymousUser'

    boolean isLoggedIn() {
        authentication != null
    }

    Serializable getCurrentUserId() {
        authentication?.principal?.id
    }

    /** A domain class do usuário logado (ou o principal, se os usuários não vêm do GORM). */
    Object getCurrentUser() {
        GroxUser p = authentication?.principal
        if (p == null) return null
        if (userClass == null || p.id == null) return p
        Gorm.api(userClass).get(p.id)
    }

    Object loadCurrentUser() {
        getCurrentUser()
    }

    org.springframework.security.crypto.password.PasswordEncoder getPasswordEncoder() {
        org.grox.plugins.springsecurity.SpringPasswords.encoder(Passwords.custom)
    }

    String encodePassword(String raw) {
        Passwords.encode(raw)
    }

    /** Requisição AJAX: cabeçalho {@code X-Requested-With: XMLHttpRequest} ou parâmetro {@code ajax=true}, como no plugin. */
    boolean isAjax(Object request) {
        def r = request ?: RequestContext.currentOrNull()?.request
        r != null && (r.getHeader('X-Requested-With') == 'XMLHttpRequest' || r.getParameter('ajax') == 'true')
    }

    boolean passwordMatches(String raw, String encoded) {
        Passwords.matches(raw, encoded)
    }

    /** Autentica o usuário na sessão atual sem senha (ex.: logo após o cadastro). */
    void reauthenticate(String username) {
        GroxUser user = securityFilter.users.loadUserByUsername(username)
        if (user == null) throw new IllegalArgumentException("usuário inexistente: ${username}")
        def wr = RequestContext.current()
        def auth = new Authentication(principal: user.withoutPassword(), method: 'form')
        SecurityFilter.establish(wr.getSession(true), auth)
        wr.request.setAttribute(SecurityContextHolder.REQUEST_ATTR, auth)
    }
}
