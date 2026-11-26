package org.grox.security


import org.grox.http.FlashScope

import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.grox.http.GroxRequest
import org.grox.http.GroxSession
import org.grox.routing.RouteMatch
import org.grox.session.RequestSession
import org.grox.session.SessionIds
import org.grox.http.Endpoint
import org.grox.i18n.MessageSource
import org.grox.session.SessionRepository

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Cadeia de segurança (equivalente aos filtros do Spring Security), executada antes do
 * controller e fora do event loop:
 * <ol>
 *   <li>autenticação: token REST ({@code Authorization: Bearer} ou o cabeçalho de
 *       {@code rest.token.validation.headerName}; JWT ou GORM), Basic ou sessão;</li>
 *   <li>endpoints: login por formulário, login REST, logout, página de login;</li>
 *   <li>CSRF (requisições com sessão);</li>
 *   <li>autorização: {@code @Secured} da action/controller, senão {@code interceptUrlMap},
 *       senão {@code rejectIfNoRule}.</li>
 * </ol>
 */
@Slf4j
class SecurityFilter {

    static final String SESSION_AUTH = 'grox.security.authentication'
    static final String SAVED_REQUEST = 'grox.security.savedRequest'
    static final String LAST_ERROR = 'grox.security.lastError'
    static final String CSRF_KEY = 'grox.security.csrf'
    static final String CSRF_PARAM = '_csrf'
    static final String CSRF_HEADER = 'X-CSRF-TOKEN'
    static final Set<String> UNSAFE = ['POST', 'PUT', 'PATCH', 'DELETE'] as Set

    final SecurityConfig cfg
    /** Tokens REST em JWT (padrão); null com {@code useGorm} ou sem REST. */
    final Jwt jwt
    /** Tokens REST numa domain class ({@code rest.token.storage.useGorm}); criado com as classes da app. */
    /** Tokens opacos ({@code rest.token.storage.useGorm}), no lugar do JWT. */
    TokenStore tokenStore
    UserDetailsService users
    /** Preenchidos pelo módulo web: endpoints especiais, destinos das rotas, sessões e mensagens. */
    List<Endpoint> endpoints = []
    /** Se a aplicação tem um destino de rota com o nome (controllers {@code login}/{@code logout} próprios). */
    java.util.function.Predicate<String> hasTarget = { String name -> false } as java.util.function.Predicate<String>
    SessionRepository sessions
    MessageSource messages
    /** {@code grox.security.loginThrottle.*}; null desliga. */
    LoginThrottle throttle
    /**
     * Por nome, para o módulo de cada geração (recarga leve) trocar o seu: o filtro continua o mesmo,
     * mas os listeners e as fontes de regras são da geração nova.
     */
    final Map<String, SecurityListener> listeners = new java.util.concurrent.ConcurrentHashMap<>()
    final Map<String, AccessRuleSource> ruleSources = new java.util.concurrent.ConcurrentHashMap<>()

    SecurityFilter(SecurityConfig cfg) {
        this.cfg = cfg
        this.jwt = cfg.restActive && !cfg.gormTokenStorage ? new Jwt(cfg.jwtSecret, cfg.jwtExpiration) : null
        jwt?.refreshExpirationSeconds = cfg.jwtRefreshExpiration
    }

    /**
     * Requisições que dispensam toda a cadeia (e, com isso, a leitura da sessão):
     * {@code chainMap} com {@code filters: 'none'}; assets, como o AssetPipelineFilter do Grails,
     * que roda antes do Spring Security; e estáticos em GET/HEAD cuja regra libera anônimos
     * (o arquivo não depende de quem está logado, então o resultado é o mesmo).
     */
    boolean bypasses(GroxRequest req, boolean asset, boolean staticFile) {
        // console e actuator nunca saem da segurança (o acesso deles não vem do interceptUrlMap)
        if (cfg.chains && chain(req).none && !protectedEndpoint(req.path)) return true
        if (asset) return true
        if (!staticFile) return false
        List<String> access = cfg.rules.find { it.matches(req.path, req.method) }?.access
        access == null ? !cfg.rejectIfNoRule : AccessExpression.anyGranted(access, null)
    }

    static final String CHAIN_ATTR = 'grox.security.chain'

    /** Entrada do chainMap da requisição, calculada uma vez ({@code bypasses} e {@code process}). */
    @groovy.transform.CompileStatic
    private FilterChainRule chain(GroxRequest req) {
        FilterChainRule c = (FilterChainRule) req.getAttribute(CHAIN_ATTR)
        if (c == null) {
            c = cfg.chainFor(req.path)
            req.setAttribute(CHAIN_ATTR, c)
        }
        c
    }

    @groovy.transform.CompileStatic
    private boolean protectedEndpoint(String path) {
        for (Endpoint e : endpoints) if (e.handles(path)) return true
        false
    }

    /**
     * Estático: roda em toda requisição (perfil JFR de uma rota de texto: ~25% do custo, quase tudo na ligação
     * das chamadas dinâmicas).
     *
     * @return null para seguir ao controller, ou a resposta/erro
     */
    @groovy.transform.CompileStatic
    SecurityOutcome process(GroxRequest req, RouteMatch route) {
        // filtros do chainMap: sem securityContextPersistenceFilter não há sessão; sem restTokenValidationFilter, o token é ignorado
        FilterChainRule chain = chain(req)
        if (chain.none) chain = FilterChainRule.DEFAULT   // só chega aqui com 'none' o console ou o actuator (ver bypasses)
        boolean stateless = cfg.isStateless(req.path) || !chain.session
        String header = req.getHeader('Authorization')
        String token = chain.token ? restToken(req) : null
        Authentication auth = null
        if (token != null) {
            auth = tokenAuthentication(token)
            if (auth == null) return unauthorized('Token inválido ou expirado')
        } else if (header != null && header.regionMatches(true, 0, 'Basic ', 0, 6) && cfg.basicAuth && chain.basic) {
            List basicResult = basic(header.substring(6).trim(), req.clientAddr)
            Authentication basicAuth = (Authentication) basicResult[0]
            String basicError = (String) basicResult[1], basicUser = (String) basicResult[2]
            if (basicError == THROTTLED) return tooManyAttempts(req, basicUser)
            auth = basicAuth
            if (auth == null) return unauthorized('Credenciais inválidas')
        } else if (!stateless) {
            auth = (Authentication) session(req, false)?.getAttribute(SESSION_AUTH)
        }
        if (auth != null) req.setAttribute(SecurityContextHolder.REQUEST_ATTR, auth)

        String path = req.path
        if (path == cfg.loginProcessingUrl && req.method == 'POST') return formLogin(req)
        if (cfg.restActive && path == cfg.restLoginUrl) return req.method == 'POST' ? restLogin(req) : SecurityOutcome.error(405, 'Use POST')
        if (jwt != null && path == cfg.restRefreshUrl) return req.method == 'POST' ? refresh(req) : SecurityOutcome.error(405, 'Use POST')
        if (cfg.restActive && path == cfg.restLogoutUrl) return req.method == 'POST' ? restLogout(req, auth) : SecurityOutcome.error(405, 'Use POST')
        if (path == cfg.logoutUrl) return logout(req)
        // LogoutController do spring-security-core (quando a app não tem o seu): vai para o filterProcessesUrl
        // (logout direto: um redirect 302 viraria GET no filterProcessesUrl, recusado com postOnly)
        if (SecurityConfig.isLogoutControllerUrl(path) && !hasTarget.test('logout')) return logout(req)
        boolean builtinLogin = !hasTarget.test(controllerOf(cfg.loginFormUrl))
        if (builtinLogin && req.method == 'GET') {
            if (path == cfg.loginFormUrl) return new SecurityOutcome(loginModel: loginModel(req))
            // como o LoginController do spring-security-core: /login vai para o formulário (ou para a home, se já logado)
            String base = '/' + controllerOf(cfg.loginFormUrl)
            if (path in [base, base + '/', base + '/index']) return SecurityOutcome.redirect(auth != null ? cfg.defaultTargetUrl : cfg.loginFormUrl)
            if (path == base + '/authfail') {
                flashLoginError(req, (String) session(req, false)?.getAttribute(LAST_ERROR) ?: 'springSecurity.errors.login.fail')
                return SecurityOutcome.redirect(cfg.loginFormUrl + '?login_error=1')
            }
        }

        if (cfg.csrfEnabled && req.method in UNSAFE && csrfRequired(req, auth, stateless) && !validCsrf(req)) {
            return SecurityOutcome.error(403, 'Token CSRF ausente ou inválido')
        }

        // sem filterInvocationInterceptor: sem autorização (console e actuator conferem o acesso deles mesmo assim)
        if (!chain.authorize && !protectedEndpoint(path)) return null
        List<String> access = accessFor(req, route)
        boolean framework = cfg.isFrameworkUrl(path)
        if (access == null) {
            if (!framework && !cfg.rejectIfNoRule) return null   // sem regra: pública, como no Spring
            access = framework ? ['permitAll'] : ['denyAll']
        }
        // sem anonymousAuthenticationFilter, o FilterSecurityInterceptor recusa quem não tem autenticação, mesmo em permitAll
        if (auth == null && !chain.anonymous && !framework) return unauthenticated(req, stateless, chain)
        if (AccessExpression.anyGranted(access, auth)) return null
        if (auth == null) return unauthenticated(req, stateless, chain)
        SecurityOutcome.error(403, 'Acesso negado')
    }

    /**
     * Quando o POST/PUT/PATCH/DELETE precisa do token CSRF:
     * <ul>
     *   <li>com sessão: sempre, exceto Bearer e Basic de API ({@link #csrfExempt});</li>
     *   <li>sem autenticação nem cookie de sessão: só se vier de outro site pelo navegador (não há estado do usuário
     *       a forjar, mas a action poderia criar a sessão e logar a vítima numa conta do atacante, o login CSRF);</li>
     *   <li>stateless: só Basic vindo de outro site (o navegador reenvia as credenciais guardadas).</li>
     * </ul>
     */
    @groovy.transform.CompileStatic
    private boolean csrfRequired(GroxRequest req, Authentication auth, boolean stateless) {
        if (stateless) return auth?.method == 'basic' && crossSite(req)
        if (csrfExempt(req, auth)) return false
        auth != null || session(req, false) != null || crossSite(req)
    }

    /**
     * Bearer dispensa CSRF: o navegador não o envia sozinho. Basic também, exceto em requisição
     * de navegador vinda de outro site ({@code Sec-Fetch-Site}/{@code Origin}): o navegador reenvia
     * credenciais Basic guardadas em POSTs cross-site, como faz com cookies.
     */
    @groovy.transform.CompileStatic
    private static boolean csrfExempt(GroxRequest req, Authentication auth) {
        if (auth?.method == 'bearer') return true
        auth?.method == 'basic' && !crossSite(req)
    }

    /**
     * URL relativa ao próprio site: começa com {@code /}, mas não com {@code //} nem {@code /\}
     * (o navegador trata esses como outro host: {@code //evil.com/x}).
     */
    static boolean isLocalUrl(String url) {
        url != null && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\') &&
                !url.any { String c -> c < ' ' }
    }

    /** Sinais do navegador de que a requisição veio de outra origem. */
    @groovy.transform.CompileStatic
    static boolean crossSite(GroxRequest req) {
        String site = req.getHeader('Sec-Fetch-Site')
        if (site != null) return site == 'cross-site' || site == 'same-site'
        String origin = req.getHeader('Origin')
        if (origin == null) return false
        String host = req.hostHeader
        int i = origin.indexOf('://')
        !(host != null && i > 0 && origin.substring(i + 3).equalsIgnoreCase(host))
    }

    /** Regras do código ({@link AccessRuleSource}, como o {@code @Secured}) têm precedência sobre o interceptUrlMap. */
    @groovy.transform.CompileStatic
    List<String> accessFor(GroxRequest req, RouteMatch route) {
        for (Endpoint endpoint : endpoints) if (endpoint.handles(req.path)) return endpoint.accessFor(req.path)
        for (AccessRuleSource source : ruleSources.values()) {
            List<String> access = source.accessFor(req, route)
            if (access != null) return access
        }
        ruleAccess(req.path, req.method, route?.controller != null)
    }

    /**
     * Primeira regra do interceptUrlMap que casa. Numa action, {@code /admin.json} e {@code /admin/} roteiam como
     * {@code /admin}: vale a regra que vier primeiro entre a do caminho e a do caminho canônico (sem formato e sem
     * barra final). Assim {@code /admin.json} não escapa de {@code /admin/**} caindo no {@code /**}, e uma regra
     * explícita para {@code /x.json} continua valendo.
     */
    @groovy.transform.CompileStatic
    private List<String> ruleAccess(String path, String method, boolean action) {
        List<AccessRule> rules = cfg.rules
        String canonical = action ? canonicalPath(path) : path
        boolean both = canonical != path
        for (int i = 0; i < rules.size(); i++) {
            AccessRule r = rules[i]
            if (r.matches(path, method) || (both && r.matches(canonical, method))) return r.access
        }
        null
    }

    /** Sem a barra final e sem a extensão de formato no último segmento. */
    static String canonicalPath(String path) {
        String p = path.length() > 1 && path.endsWith('/') ? path.substring(0, path.length() - 1) : path
        int slash = p.lastIndexOf('/'), dot = p.lastIndexOf('.')
        dot > slash + 1 ? p.substring(0, dot) : p
    }

    boolean isGranted(String url, String method, Authentication auth) {
        List<String> access = cfg.rules.find { it.matches(url, method) }?.access
        access == null ? !cfg.rejectIfNoRule : AccessExpression.anyGranted(access, auth)
    }

    /** Navegador vai para o login; API (ou cadeia sem {@code exceptionTranslationFilter}) recebe 401. */
    private SecurityOutcome unauthenticated(GroxRequest req, boolean stateless, FilterChainRule chain) {
        String fmt = org.grox.http.MimeTypes.fromAccept(req.getHeader('Accept'))
        boolean browser = chain.redirectToLogin && !stateless && req.getHeader('Authorization') == null && restToken(req) == null && !req.xhr && !req.jsonRequest && fmt in ['html', 'all']
        if (browser) {
            if (req.method == 'GET' && isLocalUrl(req.uri)) session(req, true).setAttribute(SAVED_REQUEST, req.uri)
            return SecurityOutcome.redirect(cfg.loginFormUrl)
        }
        unauthorized('Autenticação necessária')
    }

    private SecurityOutcome unauthorized(String message) {
        Map<String, String> h = [:]
        if (cfg.basicAuth) h['WWW-Authenticate'] = "Basic realm=\"${cfg.realmName}\"".toString()
        else if (cfg.restActive && cfg.useBearerToken) h['WWW-Authenticate'] = 'Bearer'
        SecurityOutcome.error(401, message, h)
    }

    // --- autenticação ---

    /** Token REST da requisição: {@code Authorization: Bearer} ou o cabeçalho configurado (null se não houver). */
    private String restToken(GroxRequest req) {
        if (!cfg.restActive) return null
        if (cfg.useBearerToken) {
            String header = req.getHeader('Authorization')
            return header?.startsWith('Bearer ') ? header.substring(7).trim() ?: null : null
        }
        req.getHeader(cfg.tokenHeaderName)?.trim() ?: null
    }

    /** JWT: usuário vem das claims. GORM: token procurado no banco e usuário recarregado, como no plugin. */
    private Authentication tokenAuthentication(String token) {
        if (jwt != null) return jwt.authenticate(token)
        GroxUser principal = tokenStore.load(token, users)
        if (principal == null) return null
        new Authentication(principal: principal, method: 'bearer', credentials: [token: token])
    }

    static final String THROTTLED = 'springSecurity.errors.login.throttled'

    /** @return [Authentication, código de erro] */
    /** @param ip endereço do cliente, sem porta ({@code GroxRequest.clientAddr}) */
    List authenticate(String username, String password, String method, String ip = null) {
        if (!username || password == null) return [null, 'springSecurity.errors.login.fail']
        String host = ip
        if (throttle != null && throttle.retryAfter(host, username) > 0) return [null, THROTTLED]
        GroxUser user = users.loadUserByUsername(username)
        if (!Passwords.matches(password, user?.password)) {
            throttle?.failed(host, username)
            return [null, 'springSecurity.errors.login.fail']
        }
        throttle?.succeeded(host, username)
        if (!user.enabled) return [null, 'springSecurity.errors.login.disabled']
        if (user.accountLocked) return [null, 'springSecurity.errors.login.locked']
        if (user.accountExpired) return [null, 'springSecurity.errors.login.expired']
        if (user.passwordExpired) return [null, 'springSecurity.errors.login.passwordExpired']
        // como o DaoAuthenticationProvider: só depois das checagens de conta
        upgradePassword(user, password)
        [new Authentication(principal: user.withoutPassword(), method: method), null]
    }

    /**
     * Como o {@code DaoAuthenticationProvider}: depois do login, se o {@code passwordEncoder} da app pede
     * {@code upgradeEncoding} (hash antigo), regrava a senha pelo {@code userDetailsPasswordService}.
     */
    private static void upgradePassword(GroxUser user, String raw) {
        def encoder = Passwords.custom, service = Passwords.passwordService
        if (encoder == null || service == null || !encoder.upgradeEncoding(user.password)) return
        try {
            service.updatePassword(user, encoder.encode(raw))
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(SecurityFilter).warn("Falha ao atualizar o hash da senha de ${user.username}: ${e.message}")
        }
    }

    /** @return [Authentication, código de erro, username] */
    private List basic(String encoded, String ip) {
        String decoded
        try {
            decoded = new String(Base64.decoder.decode(encoded), StandardCharsets.UTF_8)
        } catch (IllegalArgumentException ignored) {
            return [null, 'springSecurity.errors.login.fail', null]
        }
        int i = decoded.indexOf(':')
        if (i < 0) return [null, 'springSecurity.errors.login.fail', null]
        String username = decoded.substring(0, i)
        List r = authenticate(username, decoded.substring(i + 1), 'basic', ip)
        [r[0], r[1], username]
    }

    /** 429 com Retry-After (login REST e Basic bloqueados pelo limite de tentativas). */
    private SecurityOutcome tooManyAttempts(GroxRequest req, String username) {
        long wait = throttle?.retryAfter(req.clientAddr, username) ?: 60L
        SecurityOutcome o = SecurityOutcome.json(429, JsonOutput.toJson([status: 429, error: 'Too Many Requests', message: THROTTLED]))
        o.response.setHeader('Retry-After', wait.toString())
        o
    }

    private SecurityOutcome formLogin(GroxRequest req) {
        if (cfg.csrfEnabled && !validCsrf(req)) return SecurityOutcome.error(403, 'Token CSRF ausente ou inválido')
        def (Authentication auth, String error) = authenticate(req.getParameter(cfg.usernameParameter)?.trim(),
                req.getParameter(cfg.passwordParameter), 'form', req.clientAddr)
        GroxSession s = session(req, true)
        if (auth == null) {
            log.debug("Login recusado para '${req.getParameter(cfg.usernameParameter)}': ${error}")
            s.setAttribute(LAST_ERROR, error)
            String username = req.getParameter(cfg.usernameParameter)
            listeners.values().each { it.loginFailed(s, error, username) }
            flashLoginError(req, error)
            return SecurityOutcome.redirect(cfg.failureUrl)
        }
        String saved = (String) s.getAttribute(SAVED_REQUEST)
        if (!isLocalUrl(saved)) saved = null
        s.removeAttribute(SAVED_REQUEST)
        s.removeAttribute(LAST_ERROR)
        listeners.values().each { it.loginSucceeded(s) }
        establish(s, auth)
        req.setAttribute(SecurityContextHolder.REQUEST_ATTR, auth)
        SecurityOutcome.redirect(!cfg.alwaysUseDefault && saved ? saved : cfg.defaultTargetUrl)
    }

    /** spring-security-rest: {"username", "password"} → {"username", "roles", "token_type", "access_token", "expires_in"}. */
    private SecurityOutcome restLogin(GroxRequest req) {
        Object body = null
        try {
            body = req.jsonRequest ? req.JSON : null
        } catch (Exception ignored) {
        }
        String username = body instanceof Map ? ((Map) body).username?.toString() : req.getParameter('username')
        String password = body instanceof Map ? ((Map) body).password?.toString() : req.getParameter('password')
        def (Authentication auth, String error) = authenticate(username, password, 'bearer', req.clientAddr)
        if (error == THROTTLED) return tooManyAttempts(req, username)
        if (auth == null) return SecurityOutcome.json(401, JsonOutput.toJson([status: 401, error: 'Unauthorized', message: error]))
        GroxUser user = auth.principal
        if (jwt == null) {
            // GORM: token opaco, sem expiração nem refresh (como o AccessToken do plugin)
            String token = tokenStore.store(user.username)
            tokenIssued(user, token, null, null)
            return SecurityOutcome.json(200, JsonOutput.toJson([username    : user.username, roles: user.roles as List, token_type: 'Bearer',
                                                                access_token: token]))
        }
        String refreshToken = jwt.createRefresh(user)
        String accessToken = jwt.create(user)
        tokenIssued(user, accessToken, refreshToken, jwt.expirationSeconds)
        tokens(user, refreshToken, accessToken)
    }

    /**
     * Avisa os {@link SecurityListener} (o plugin publica o {@code RestTokenCreationEvent}). Eles já veem o usuário
     * autenticado (e, com isso, o tenant dele): no Grails o tenant ausente não impedia a carga de associações por
     * id, que no Grox é filtrada pelo {@code @TenantId}.
     */
    private void tokenIssued(GroxUser user, String accessToken, String refreshToken, Integer expiration) {
        if (listeners.isEmpty()) return
        SecurityContextHolder.with(new Authentication(principal: user, method: 'bearer')) {
            listeners.values().each { it.tokenIssued(user, accessToken, refreshToken, expiration) }
        }
    }

    private SecurityOutcome tokens(GroxUser user, String refreshToken, String accessToken = jwt.create(user)) {
        SecurityOutcome.json(200, JsonOutput.toJson([username    : user.username, roles: user.roles as List, token_type: 'Bearer',
                                                     access_token: accessToken, expires_in: jwt.expirationSeconds,
                                                     refresh_token: refreshToken]))
    }

    /** {@code grant_type=refresh_token&refresh_token=...}: novo access token com o usuário recarregado. */
    private SecurityOutcome refresh(GroxRequest req) {
        Object body = null
        try {
            body = req.jsonRequest ? req.JSON : null
        } catch (Exception ignored) {
        }
        String grant = body instanceof Map ? ((Map) body).grant_type : req.getParameter('grant_type')
        String token = body instanceof Map ? ((Map) body).refresh_token : req.getParameter('refresh_token')
        if (grant != 'refresh_token' || !token) return SecurityOutcome.json(400, JsonOutput.toJson([error: 'invalid_request']))
        Map claims = jwt.verifyRefresh(token)
        GroxUser user = claims ? users.loadUserByUsername(claims.sub.toString()) : null
        if (user == null || !user.enabled || user.accountLocked || user.accountExpired) {
            return SecurityOutcome.json(403, JsonOutput.toJson([error: 'invalid_grant']))
        }
        tokens(user.withoutPassword(), token)
    }

    /** Revoga o access token atual (GORM: apaga o registro) e, se enviado, o refresh token. */
    private SecurityOutcome restLogout(GroxRequest req, Authentication auth) {
        if (auth?.method != 'bearer') return unauthorized(cfg.useBearerToken ? 'Envie o token em Authorization: Bearer' : "Envie o token no cabeçalho ${cfg.tokenHeaderName}")
        if (jwt == null) {
            tokenStore.remove(auth.credentials.token.toString())
            return SecurityOutcome.json(200, JsonOutput.toJson([status: 'logged out']))
        }
        jwt.revoke(auth.credentials)
        Object body = null
        try {
            body = req.jsonRequest ? req.JSON : null
        } catch (Exception ignored) {
        }
        String refreshToken = body instanceof Map ? ((Map) body).refresh_token : req.getParameter('refresh_token')
        if (refreshToken) jwt.revoke(jwt.verifyRefresh(refreshToken))
        SecurityOutcome.json(200, JsonOutput.toJson([status: 'logged out']))
    }

    private SecurityOutcome logout(GroxRequest req) {
        if (cfg.logoutPostOnly && req.method != 'POST') return SecurityOutcome.error(405, 'Logout exige POST')
        if (cfg.csrfEnabled && req.method in UNSAFE && session(req, false) != null && !validCsrf(req)) {
            return SecurityOutcome.error(403, 'Token CSRF ausente ou inválido')
        }
        RequestSession.of(req, sessions).invalidate()
        req.removeAttribute(SecurityContextHolder.REQUEST_ATTR)
        SecurityOutcome.redirect(cfg.afterLogoutUrl)
    }

    // --- sessão e CSRF ---

    /** Login na sessão: id novo (session fixation) e token CSRF novo. */
    static void establish(GroxSession s, Authentication auth) {
        s.changeSessionId()
        s.setAttribute(SESSION_AUTH, auth)
        s.setAttribute(CSRF_KEY, SessionIds.next())
    }

    GroxSession session(GroxRequest req, boolean create) {
        RequestSession.of(req, sessions).get(create)
    }

    String csrfToken(GroxRequest req) {
        GroxSession s = session(req, true)
        String token = (String) s.getAttribute(CSRF_KEY)
        if (token == null) {
            token = SessionIds.next()
            s.setAttribute(CSRF_KEY, token)
        }
        token
    }

    private boolean validCsrf(GroxRequest req) {
        String expected = (String) session(req, false)?.getAttribute(CSRF_KEY)
        String given = req.formParameters?.get(CSRF_PARAM)?.getAt(0) ?: req.getHeader(CSRF_HEADER)
        expected != null && given != null && MessageDigest.isEqual(expected.getBytes('UTF-8'), given.getBytes('UTF-8'))
    }

    /**
     * Como o {@code LoginController.authfail} do plugin: a mensagem traduzida vai em {@code flash.message},
     * que a página de login da aplicação costuma exibir.
     */
    private void flashLoginError(GroxRequest req, String code) {
        GroxSession s = session(req, true)
        FlashScope flash = (FlashScope) s.getAttribute(FlashScope.KEY) ?: new FlashScope()
        String lang = req.getHeader('Accept-Language')?.tokenize(',;')?.find()?.trim()
        Locale locale = lang && lang != '*' ? Locale.forLanguageTag(lang) : null   // null: locale padrão da app
        flash.put('message', messages?.getMessage(code, null, code, locale) ?: code)
        s.setAttribute(FlashScope.KEY, flash)
    }

    Map<String, Object> loginModel(GroxRequest req) {
        GroxSession s = session(req, true)
        String error = req.getParameter('login_error') ? ((String) s.getAttribute(LAST_ERROR) ?: 'springSecurity.errors.login.fail') : null
        s.removeAttribute(LAST_ERROR)
        [postUrl: cfg.loginProcessingUrl, usernameParameter: cfg.usernameParameter, passwordParameter: cfg.passwordParameter,
         csrfParameter: CSRF_PARAM, csrfToken: cfg.csrfEnabled ? csrfToken(req) : null, errorCode: error]
    }

    private static String controllerOf(String url) {
        url.tokenize('/')[0]
    }
}
