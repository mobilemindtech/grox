package org.grox.security

import org.grox.core.Config

/**
 * Configuração em {@code grails.plugin.springsecurity} (mesmas chaves do spring-security-core
 * e do spring-security-rest; extensões Grox: {@code users}, {@code stateless},
 * {@code csrf.enabled}, {@code userLookup.tenantPropertyName}).
 */
class SecurityConfig {

    static final String PREFIX = 'grails.plugin.springsecurity'

    boolean active
    boolean rejectIfNoRule
    List<AccessRule> rules = []
    List<AccessRule> statelessRules = []
    /** {@code filterChain.chainMap}, na ordem (vale a primeira que casa). */
    List<FilterChainRule> chains = []
    List<Map> users = []

    String userDomainClassName
    String authorityJoinClassName
    String authorityClassName
    String authorityNameField
    String usernamePropertyName
    String passwordPropertyName
    String enabledPropertyName
    String accountExpiredPropertyName
    String accountLockedPropertyName
    String passwordExpiredPropertyName
    String authoritiesPropertyName
    String tenantPropertyName

    String loginFormUrl
    String loginProcessingUrl
    String usernameParameter
    String passwordParameter
    String defaultTargetUrl
    boolean alwaysUseDefault
    String failureUrl
    String logoutUrl
    String afterLogoutUrl
    boolean logoutPostOnly

    /** {@code password.algorithm}, {@code password.hash.iterations}, {@code password.encodeHashAsBase64}, {@code password.bcrypt.logrounds}. */
    String passwordAlgorithm
    int passwordIterations
    boolean passwordBase64
    int bcryptLogrounds

    boolean basicAuth
    String realmName

    boolean csrfEnabled

    boolean restActive
    String restLoginUrl
    String jwtSecret
    int jwtExpiration
    int jwtRefreshExpiration
    String restRefreshUrl
    String restLogoutUrl
    /** {@code rest.token.storage.useGorm}: token opaco numa domain class, no lugar do JWT. */
    boolean gormTokenStorage
    String tokenDomainClassName
    String tokenValuePropertyName
    String tokenUsernamePropertyName
    /** Grox: segundos de cache do usuário de um token GORM (0 desliga). */
    int tokenCacheSeconds
    /** {@code rest.token.validation.useBearerToken}: {@code Authorization: Bearer}; senão o cabeçalho {@link #tokenHeaderName}. */
    boolean useBearerToken
    String tokenHeaderName

    static SecurityConfig from(Config config) {
        def c = new SecurityConfig()
        Closure<Object> get = { String key, Object fallback -> config.getProperty("${PREFIX}.${key}", fallback == null ? String : fallback.getClass(), fallback) }
        c.active = config.get(PREFIX) != null && get('active', true)
        c.rejectIfNoRule = get('rejectIfNoRule', true)
        // interceptUrlMap e, depois, controllerAnnotations.staticRules (como gera o s2-quickstart no application.groovy)
        c.rules = rules(config.get("${PREFIX}.interceptUrlMap"), 'interceptUrlMap') +
                rules(config.get("${PREFIX}.controllerAnnotations.staticRules"), 'controllerAnnotations.staticRules') +
                rules(config.get('grox.security.rules'), 'grox.security.rules')   // as do security { } da DSL de projeto
        c.statelessRules = ((config.get("${PREFIX}.stateless") ?: []) as List).collect { new AccessRule(it.toString(), null, ['permitAll']) }
        c.chains = FilterChainRule.parse(config.get("${PREFIX}.filterChain.chainMap"))
        c.users = (((config.get("${PREFIX}.users") ?: []) as List<Map>) + ((config.get('grox.security.users') ?: []) as List<Map>))

        c.userDomainClassName = get('userLookup.userDomainClassName', null)
        c.authorityJoinClassName = get('userLookup.authorityJoinClassName', null)
        c.authorityClassName = get('authority.className', null)
        c.authorityNameField = get('authority.nameField', 'authority')
        c.usernamePropertyName = get('userLookup.usernamePropertyName', 'username')
        c.passwordPropertyName = get('userLookup.passwordPropertyName', 'password')
        c.enabledPropertyName = get('userLookup.enabledPropertyName', 'enabled')
        c.accountExpiredPropertyName = get('userLookup.accountExpiredPropertyName', 'accountExpired')
        c.accountLockedPropertyName = get('userLookup.accountLockedPropertyName', 'accountLocked')
        c.passwordExpiredPropertyName = get('userLookup.passwordExpiredPropertyName', 'passwordExpired')
        c.authoritiesPropertyName = get('userLookup.authoritiesPropertyName', 'authorities')
        c.tenantPropertyName = get('userLookup.tenantPropertyName', 'tenantId')

        c.loginFormUrl = get('auth.loginFormUrl', '/login/auth')
        c.loginProcessingUrl = get('apf.filterProcessesUrl', '/login/authenticate')
        c.usernameParameter = get('apf.usernameParameter', 'username')
        c.passwordParameter = get('apf.passwordParameter', 'password')
        c.defaultTargetUrl = get('successHandler.defaultTargetUrl', '/')
        c.alwaysUseDefault = get('successHandler.alwaysUseDefault', false)
        c.failureUrl = get('failureHandler.defaultFailureUrl', c.loginFormUrl + '?login_error=1')
        c.logoutUrl = get('logout.filterProcessesUrl', '/logoff')
        c.afterLogoutUrl = get('logout.afterLogoutUrl', '/')
        c.logoutPostOnly = get('logout.postOnly', true)

        c.passwordAlgorithm = get('password.algorithm', 'bcrypt')
        c.passwordIterations = get('password.hash.iterations', 10000)
        c.passwordBase64 = get('password.encodeHashAsBase64', false)
        c.bcryptLogrounds = get('password.bcrypt.logrounds', 10)
        c.basicAuth = get('useBasicAuth', false)
        c.realmName = get('basic.realmName', 'Grox')
        c.csrfEnabled = get('csrf.enabled', true)

        c.restActive = get('rest.active', config.get("${PREFIX}.rest") != null)
        c.restLoginUrl = get('rest.login.endpointUrl', '/api/login')
        c.jwtSecret = get('rest.token.storage.jwt.secret', null)
        c.jwtExpiration = get('rest.token.storage.jwt.expiration', 3600)
        c.jwtRefreshExpiration = get('rest.token.storage.jwt.refreshExpiration', 30 * 24 * 3600)
        c.restRefreshUrl = get('rest.token.generation.refreshEndpointUrl', '/oauth/access_token')
        c.restLogoutUrl = get('rest.logout.endpointUrl', '/api/logout')
        c.gormTokenStorage = get('rest.token.storage.useGorm', false)
        c.tokenDomainClassName = get('rest.token.storage.gorm.tokenDomainClassName', null)
        c.tokenValuePropertyName = get('rest.token.storage.gorm.tokenValuePropertyName', 'tokenValue')
        c.tokenUsernamePropertyName = get('rest.token.storage.gorm.usernamePropertyName', 'username')
        c.tokenCacheSeconds = get('rest.token.storage.gorm.cacheSeconds', 30)
        c.useBearerToken = get('rest.token.validation.useBearerToken', true)
        c.tokenHeaderName = get('rest.token.validation.headerName', 'X-Auth-Token')
        c
    }

    /** Lista de {@code [pattern:, access:, httpMethod:]} ou, na forma antiga do plugin, mapa {@code caminho: regras}. */
    private static List<AccessRule> rules(Object value, String key) {
        if (value == null) return []
        if (value instanceof Map) value = ((Map) value).collect { k, v -> [pattern: k, access: v] }
        if (!(value instanceof List)) throw new IllegalArgumentException("${PREFIX}.${key} deve ser uma lista de {pattern, access, httpMethod}")
        ((List) value).collect { Object r ->
            Map m = (Map) r
            if (!m.pattern || m.access == null) throw new IllegalArgumentException("regra inválida em ${key}: ${m}")
            List<String> access = (m.access instanceof Collection ? m.access : [m.access]).collect { it.toString() }
            new AccessRule(m.pattern.toString(), m.get('httpMethod')?.toString(), access)
        }
    }

    @groovy.transform.CompileStatic
    boolean isStateless(String path) {
        for (AccessRule r : statelessRules) if (r.regex.matcher(path).matches()) return true
        false
    }

    FilterChainRule chainFor(String path) {
        FilterChainRule.find(chains, path)
    }

    /** {@code /login}, {@code /login/index}, {@code /login/authfail} (LoginController embutido). */
    List<String> loginControllerUrls() {
        String base = '/' + (loginFormUrl.tokenize('/')[0] ?: 'login')
        [base, base + '/', base + '/index', base + '/authfail']
    }

    /**
     * {@code /logout}, {@code /logout/index} (e com id, como gera {@code <g:link controller="logout" id="1">}):
     * o LogoutController embutido do spring-security-core.
     */
    static boolean isLogoutControllerUrl(String path) {
        path in ['/logout', '/logout/', '/logout/index'] || path.startsWith('/logout/index/')
    }

    /** URLs do próprio mecanismo de login, sempre liberadas. */
    boolean isFrameworkUrl(String path) {
        path == loginFormUrl || path == loginProcessingUrl || path == logoutUrl || path == failureUrl.tokenize('?')[0] ||
                path in loginControllerUrls() ||
                (restActive && path in [restLoginUrl, restLogoutUrl]) ||
                (restActive && !gormTokenStorage && path == restRefreshUrl)   // refresh só existe com JWT
    }
}
