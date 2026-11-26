package grails.plugin.springsecurity

import org.grox.security.AccessExpression
import org.grox.security.SecurityContextHolder

/** Utilitários estáticos do spring-security-core. */
class SpringSecurityUtils {

    /** Role dada a quem não tem nenhuma (alguns votantes do Spring exigem ao menos uma). */
    static final String NO_ROLE = 'ROLE_NO_ROLES'
    /** Na sessão depois de um login recusado: o username digitado. */
    static final String SPRING_SECURITY_LAST_USERNAME_KEY = 'SPRING_SECURITY_LAST_USERNAME'

    /** Configuração vigente (definida pelo Grox ao subir a segurança). */
    static volatile org.grox.security.SecurityConfig current

    /**
     * {@code SpringSecurityUtils.securityConfig}: {@code grails.plugin.springsecurity.*} com os valores efetivos do
     * Grox ({@code apf.filterProcessesUrl}, {@code successHandler.defaultTargetUrl}, {@code logout.filterProcessesUrl},
     * {@code auth.loginFormUrl}, {@code failureHandler.defaultFailureUrl}...). É um {@code ConfigObject}: chaves
     * inexistentes navegam sem NullPointerException, como no plugin.
     */
    static ConfigObject getSecurityConfig() {
        // montado uma vez por configuração (o app C lê em toda requisição de login/home), como o cache do plugin
        def app = grails.util.Holders.grailsApplication
        List key = cachedKey
        if (cached != null && key != null && key[0].is(current) && key[1].is(app)) return cached
        ConfigObject co = build(app)
        cached = co
        cachedKey = [current, app]
        co
    }

    private static volatile ConfigObject cached
    private static volatile List cachedKey

    private static ConfigObject build(Object app) {
        def co = new ConfigObject()
        def raw = app?.config?.getProperty('grails.plugin.springsecurity')
        if (raw instanceof Map) fill(co, (Map) raw)
        def c = current
        if (c != null) {
            co.apf.filterProcessesUrl = c.loginProcessingUrl
            co.apf.usernameParameter = c.usernameParameter
            co.apf.passwordParameter = c.passwordParameter
            co.auth.loginFormUrl = c.loginFormUrl
            co.successHandler.defaultTargetUrl = c.defaultTargetUrl
            co.successHandler.alwaysUseDefault = c.alwaysUseDefault
            co.failureHandler.defaultFailureUrl = c.failureUrl
            co.logout.filterProcessesUrl = c.logoutUrl
            co.logout.afterLogoutUrl = c.afterLogoutUrl
            co.logout.postOnly = c.logoutPostOnly
        }
        if (!co.rememberMe.parameter) co.rememberMe.parameter = 'remember-me'
        co
    }

    private static void fill(ConfigObject target, Map source) {
        source.each { k, v ->
            if (v instanceof Map) fill((ConfigObject) target.getProperty(k.toString()), (Map) v)
            else target.put(k.toString(), v)
        }
    }

    /** Como no plugin: as {@code GrantedAuthority} do usuário ({@code principalAuthorities*.authority}). */
    static Collection<org.springframework.security.core.GrantedAuthority> getPrincipalAuthorities() {
        (SecurityContextHolder.authentication?.authorities ?: []) as Collection<org.springframework.security.core.GrantedAuthority>
    }

    private static Set<String> roleNames() {
        (SecurityContextHolder.authentication?.principal?.roles ?: []) as Set<String>
    }

    static boolean ifAllGranted(String roles) {
        Set<String> mine = roleNames()
        split(roles).every { mine.contains(it) }
    }

    static boolean ifAnyGranted(String roles) {
        Set<String> mine = roleNames()
        split(roles).any { mine.contains(it) }
    }

    static boolean ifNotGranted(String roles) {
        !ifAnyGranted(roles)
    }

    static boolean evaluate(String expression) {
        AccessExpression.evaluate(expression, SecurityContextHolder.authentication)
    }

    private static List<String> split(String roles) {
        roles.split(',')*.trim().findAll()
    }
}
