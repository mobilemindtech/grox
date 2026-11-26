package org.grox

import org.grox.security.AccessExpression
import org.grox.security.AccessRule
import org.grox.security.FilterChainRule
import org.grox.security.Authentication
import org.grox.security.GroxUser
import org.grox.security.Jwt
import org.grox.security.Passwords
import org.grox.security.SecurityConfig
import org.grox.core.Config
import org.grox.security.SecurityFilter
import org.grox.security.SecurityContextHolder
import grails.plugin.springsecurity.SpringSecurityService
import org.grox.http.GroxRequest
import org.junit.jupiter.api.Test
import org.springframework.security.core.userdetails.UserDetailsPasswordService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder

import java.security.MessageDigest

import static org.junit.jupiter.api.Assertions.assertThrows

class SecurityTest {

    static class ComService { def springSecurityService }
    static class SemService { def usuarioService }

    @Test
    void 'aviso na subida para quem declara springSecurityService sem o modulo do plugin'() {
        def classes = [ComService, SemService, ComService] as List<Class<?>>
        assert org.grox.security.SecurityModule.declaringSpringSecurityService(classes, [:]) == ['ComService']
        // com o grox-grails-plugins, o bean existe e não há aviso
        assert org.grox.security.SecurityModule.declaringSpringSecurityService(classes, [springSecurityService: new Object()]).empty
    }

    static Authentication auth(String... roles) {
        def u = new GroxUser(username: 'ana', tenantId: 'acme', id: 7L)
        u.roles = roles.toList()
        new Authentication(principal: u, method: 'form')
    }

    @Test
    void 'passwordEncoder da aplicacao (DelegatingPasswordEncoder) e upgrade da senha no login'() {
        // como o CustomPasswordEncoder do app C: bcrypt por padrão, {sha256} legado sem sal
        def sha = new MessageDigestPasswordEncoder('SHA-256').tap { iterations = 1 }
        def encoder = new DelegatingPasswordEncoder('bcrypt', [bcrypt: new BCryptPasswordEncoder(), sha256: sha])
        String legado = '{sha256}' + MessageDigest.getInstance('SHA-256').digest('segredo'.bytes).encodeHex()
        assert encoder.matches('segredo', legado) && !encoder.matches('outra', legado) && encoder.upgradeEncoding(legado)
        String novo = encoder.encode('segredo')
        assert novo.startsWith('{bcrypt}$2a$') && encoder.matches('segredo', novo) && !encoder.upgradeEncoding(novo)
        assertThrows(IllegalArgumentException) { encoder.matches('x', '{md4}abc') }
        String salgado = sha.encode('abc')          // {sal}hash, formato do Spring
        assert salgado.startsWith('{') && sha.matches('abc', salgado) && !sha.matches('abd', salgado)

        def user = new GroxUser(username: 'ana', password: legado, enabled: true)
        user.roles = ['ROLE_USER']
        def f = new SecurityFilter(SecurityConfig.from(new Config([:])))
        f.users = { String n -> n == 'ana' ? user : null } as org.grox.security.UserDetailsService
        List<String> regravadas = []
        // como o SpringSecurityModule faz com os beans passwordEncoder e userDetailsPasswordService da app
        Passwords.custom = new org.grox.plugins.springsecurity.SpringPasswords.Codec(encoder)
        Passwords.passwordService = new org.grox.plugins.springsecurity.SpringPasswords.Updater(
                { u, String p -> regravadas << "${u.username}:${p}".toString(); u } as UserDetailsPasswordService)
        try {
            assert f.authenticate('ana', 'errada', 'form')[1] == 'springSecurity.errors.login.fail' && !regravadas
            def r = f.authenticate('ana', 'segredo', 'form')
            assert r[0] && r[1] == null
            assert regravadas.size() == 1 && regravadas[0].startsWith('ana:{bcrypt}$2a$')
            assert Passwords.encode('x').startsWith('{bcrypt}')   // encodePassword usa o encoder da app
        } finally {
            Passwords.custom = null
            Passwords.passwordService = null
        }
    }

    @Test
    void 'principalAuthorities devolve GrantedAuthority, como no plugin'() {
        SecurityContextHolder.with(auth('ROLE_USER', 'ROLE_ADMIN')) {
            def auths = grails.plugin.springsecurity.SpringSecurityUtils.principalAuthorities
            assert auths*.authority as Set == ['ROLE_USER', 'ROLE_ADMIN'] as Set
            assert auths.any { it.authority == 'ROLE_ADMIN' }
            assert grails.plugin.springsecurity.SpringSecurityUtils.ifAllGranted('ROLE_USER,ROLE_ADMIN')
            assert grails.plugin.springsecurity.SpringSecurityUtils.ifNotGranted('ROLE_ROOT')
        }
        assert grails.plugin.springsecurity.SpringSecurityUtils.principalAuthorities.isEmpty()
    }

    @Test
    void 'URLs do LogoutController embutido'() {
        assert ['/logout', '/logout/', '/logout/index', '/logout/index/1'].every { SecurityConfig.isLogoutControllerUrl(it) }
        assert !SecurityConfig.isLogoutControllerUrl('/logoutx') && !SecurityConfig.isLogoutControllerUrl('/logout/outra')
    }

    @Test
    void 'principal as GrailsUser, como no spring-security-core'() {
        def u = auth('ROLE_CLIENTE').principal
        def g = u as grails.plugin.springsecurity.userdetails.GrailsUser
        assert g.username == 'ana' && g.id == 7L && g.authorities*.authority == ['ROLE_CLIENTE'] && g.enabled && g.accountNonLocked
        assert (u as org.springframework.security.core.userdetails.UserDetails).username == 'ana'
        assert (u as Object).is(u) && (u as String) == u.toString()
    }

    @Test
    void 'principal anonimo como no Spring Security'() {
        def service = new SpringSecurityService()
        assert service.principal == 'anonymousUser' && !service.loggedIn
        assert service.currentUserId == null && service.currentUser == null
        SecurityContextHolder.with(auth('ROLE_USER')) {
            assert service.principal.username == 'ana' && service.loggedIn && service.currentUserId == 7L
        }
    }

    @Test
    void 'expressoes de acesso'() {
        def admin = auth('ROLE_ADMIN')
        def user = auth('ROLE_USER')
        assert AccessExpression.evaluate('permitAll', null)
        assert !AccessExpression.evaluate('denyAll', admin)
        assert AccessExpression.evaluate('isAuthenticated()', user) && !AccessExpression.evaluate('isAuthenticated()', null)
        assert AccessExpression.evaluate('isAnonymous()', null) && !AccessExpression.evaluate('isAnonymous()', user)
        assert AccessExpression.evaluate('ROLE_ADMIN', admin) && !AccessExpression.evaluate('ROLE_ADMIN', user)
        assert AccessExpression.evaluate("hasRole('ADMIN')", admin) && AccessExpression.evaluate("hasRole('ROLE_ADMIN')", admin)
        assert AccessExpression.evaluate("hasAnyRole('ROLE_X', 'USER')", user)
        assert AccessExpression.evaluate("hasAuthority('ROLE_USER')", user)
        assert AccessExpression.evaluate("hasRole('ADMIN') or hasRole('USER')", user)
        assert !AccessExpression.evaluate("hasRole('ADMIN') and isAuthenticated()", user)
        assert AccessExpression.evaluate("isAuthenticated() and (hasRole('ADMIN') or hasRole('USER'))", user)
        assert AccessExpression.evaluate("!hasRole('ADMIN')", user)
        assert AccessExpression.anyGranted(['ROLE_ADMIN', 'ROLE_USER'], user)
        assertThrows(IllegalArgumentException) { AccessExpression.validate('hasRola(x)') }
        assertThrows(IllegalArgumentException) { new AccessRule('/x', null, ['isAutenticado()']) }
    }

    @Test
    void 'padroes Ant das regras'() {
        def admin = new AccessRule('/admin/**', null, ['ROLE_ADMIN'])
        assert admin.matches('/admin', 'GET') && admin.matches('/admin/x/y', 'POST') && !admin.matches('/administrador', 'GET')
        def one = new AccessRule('/usuario/*', 'POST', ['ROLE_ADMIN'])
        assert one.matches('/usuario/save', 'POST') && !one.matches('/usuario/save', 'GET') && !one.matches('/usuario/a/b', 'POST')
        assert new AccessRule('/**/*.css', null, ['permitAll']).matches('/static/css/app.css', 'GET')
        assert new AccessRule('/**', null, ['permitAll']).matches('/qualquer/coisa', 'DELETE')
        assert new AccessRule('/api/**', 'GET', ['permitAll']).matches('/api/x', 'HEAD')
    }

    @Test
    void 'senhas bcrypt e prefixos'() {
        String hash = Passwords.encode('segredo')
        assert hash.startsWith('{bcrypt}$2a$10$') && Passwords.isEncoded(hash)   // formato do DelegatingPasswordEncoder do Grails
        assert Passwords.matches('segredo', hash) && !Passwords.matches('outra', hash)
        assert Passwords.matches('segredo', hash.substring(8))   // sem prefixo, como o Grox gravava antes
        assert Passwords.matches('abc', '{noop}abc') && !Passwords.matches('abd', '{noop}abc')
        assert !Passwords.matches('x', null) && !Passwords.matches('x', 'texto-puro')
        // isEncoded só reconhece hashes: senha escolhida pelo usuário com prefixo não escapa da codificação
        assert !Passwords.isEncoded('{noop}segredo') && !Passwords.isEncoded('{MD5}qualquer') && !Passwords.isEncoded('{SHA-256}')
        assert !Passwords.isEncoded('segredo') && !Passwords.isEncoded(null)
        assert Passwords.isEncoded('{MD5}' + 'a' * 32) && Passwords.isEncoded('{SHA-256}{sal}' + 'b' * 64)
        assert Passwords.isEncoded('{SHA-1}' + 'QUJDREVGR0hJSktMTU5PUFFSU1Q=') && !Passwords.isEncoded('{SHA-1}' + 'x' * 39)
    }

    @Test
    void 'senhas SHA-256 no formato do Grails 8'() {
        try {
            Passwords.configure('SHA-256', 1, false, 10)
            // {SHA-256} + hex de 64: 73 caracteres, como no banco do app B
            String sha = '{SHA-256}' + java.security.MessageDigest.getInstance('SHA-256').digest('segredo'.bytes).encodeHex()
            assert Passwords.encode('segredo') == sha && sha.length() == 73
            assert Passwords.matches('segredo', sha) && !Passwords.matches('outra', sha)
            assert Passwords.isEncoded(sha)
            // com sal ({SHA-256}{sal}hash, MessageDigestPasswordEncoder do Spring) e sem prefixo também são lidos
            String salted = '{abc}' + java.security.MessageDigest.getInstance('SHA-256').digest('segredo{abc}'.bytes).encodeHex()
            assert Passwords.matches('segredo', '{SHA-256}' + salted) && Passwords.matches('segredo', sha.substring(9))
            // bcrypt continua sendo lido com outro algoritmo configurado
            assert Passwords.matches('x', '{bcrypt}' + at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(4, 'x'.toCharArray()))
        } finally {
            Passwords.configure('bcrypt', 10000, false, 10)
        }
    }

    @Test
    void 'JWT assinado, com expiracao e adulteracao detectada'() {
        def jwt = new Jwt('0123456789abcdef0123456789abcdef', 60)
        String token = jwt.create(auth('ROLE_USER').principal)
        def a = jwt.authenticate(token)
        assert a.name == 'ana' && a.principal.roles == ['ROLE_USER'] as Set && a.principal.tenantId == 'acme' && a.principal.id == 7
        assert a.method == 'bearer' && a.stateless
        String[] p = token.split(/\./)
        String forged = p[0] + '.' + Base64.urlEncoder.withoutPadding().encodeToString('{"sub":"root","roles":["ROLE_ADMIN"],"iss":"grox","exp":9999999999}'.bytes) + '.' + p[2]
        assert jwt.authenticate(forged) == null
        assert new Jwt('outro-segredo-com-pelo-menos-32-bytes!', 60).authenticate(token) == null
        assert new Jwt('0123456789abcdef0123456789abcdef', -5).with { authenticate(create(auth().principal)) } == null
        assert jwt.authenticate('lixo') == null
        assertThrows(IllegalArgumentException) { new Jwt('curto', 60) }
        // cabeçalho que não é objeto JSON (de qualquer anônimo): null, sem exceção (antes: 500 com stack trace)
        ['W10.e30.AAAA', 'bnVsbA.e30.AAAA', 'IjEi.e30.AA', '!!!.e30.AA', 'e30.e30.%%%'].each { assert jwt.authenticate(it) == null }
        // mesmo com a assinatura certa, cabeçalho ou claims que não são objetos são recusados
        Closure<String> b64 = { String v -> Base64.urlEncoder.withoutPadding().encodeToString(v.bytes) }
        Closure<String> assinado = { String header, String claims ->
            String semAssinatura = b64(header) + '.' + b64(claims)
            semAssinatura + '.' + Base64.urlEncoder.withoutPadding().encodeToString(jwt.sign(semAssinatura))
        }
        assert jwt.authenticate(assinado('[]', '{"sub":"x","iss":"grox","exp":9999999999}')) == null
        assert jwt.authenticate(assinado('{"alg":"HS256"}', '[1]')) == null
        assert jwt.authenticate(assinado('{"alg":"none"}', '{"sub":"x","iss":"grox","exp":9999999999}')) == null
        assert jwt.authenticate(assinado('{"alg":"HS256"}', '{"sub":"x","iss":"grox","exp":9999999999}')).name == 'x'
    }

    @Test
    void 'estaticos, assets e chainMap none dispensam a seguranca e a sessao'() {
        def cfg = SecurityConfig.from(new Config([grails: [plugin: [springsecurity: [
                filterChain    : [chainMap: [[pattern: '/public/**', filters: 'none'], [pattern: '/**', filters: 'JOINED_FILTERS']]],
                interceptUrlMap: [[pattern: '/static/privado/**', access: ['isAuthenticated()']],
                                  [pattern: '/static/**', access: ['permitAll']],
                                  [pattern: '/**', access: ['isAuthenticated()']]]]]]]))
        def f = new SecurityFilter(cfg)
        Closure<GroxRequest> get = { String p -> new GroxRequest(method: 'GET', path: p, uri: p) }
        assert f.bypasses(get('/public/x'), false, false)                 // chainMap none
        assert f.bypasses(new GroxRequest(method: 'POST', path: '/public/x', uri: '/public/x'), false, false)
        assert f.bypasses(get('/assets/app.css'), true, false)            // assets, como no Grails
        assert f.bypasses(get('/static/logo.png'), false, true)           // estático liberado para anônimos
        assert !f.bypasses(get('/static/privado/a.pdf'), false, true)     // estático protegido segue a regra
        assert !f.bypasses(get('/usuario/index'), false, false)           // controller: sempre a cadeia
    }

    @Test
    void 'chainMap: primeira entrada vale, JOINED_FILTERS com exclusoes'() {
        def chains = FilterChainRule.parse([[pattern: '/static/**', filters: 'none'],
                                            [pattern: '/api/**', filters: 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-securityContextPersistenceFilter'],
                                            [pattern: '/**', filters: 'JOINED_FILTERS,-restTokenValidationFilter']])
        assert FilterChainRule.find(chains, '/static/a.png').none
        def api = FilterChainRule.find(chains, '/api/x')
        assert !api.none && api.token && !api.session && !api.anonymous && !api.redirectToLogin && api.authorize
        def site = FilterChainRule.find(chains, '/usuario/index')
        assert site.session && !site.token && site.anonymous && site.redirectToLogin
        assert FilterChainRule.find([], '/x').is(FilterChainRule.DEFAULT) && FilterChainRule.DEFAULT.anonymous
        // nomes explícitos (sem JOINED_FILTERS)
        assert new FilterChainRule('/x/**', 'restTokenValidationFilter,filterInvocationInterceptor').filters == ['restTokenValidationFilter', 'filterInvocationInterceptor'] as Set
    }

    @Test
    void 'chainMap do spring-security-rest: 401 sem token, token ignorado fora da cadeia, sem autorizacao'() {
        String secret = '0123456789abcdef0123456789abcdef'
        def cfg = SecurityConfig.from(new Config([grails: [plugin: [springsecurity: [
                rest           : [token: [storage: [jwt: [secret: secret]]]],
                filterChain    : [chainMap: [
                        [pattern: '/api/**', filters: 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter'],
                        [pattern: '/public/**', filters: 'JOINED_FILTERS,-exceptionTranslationFilter,-securityContextPersistenceFilter,-filterInvocationInterceptor'],
                        [pattern: '/**', filters: 'JOINED_FILTERS,-restTokenValidationFilter,-restExceptionTranslationFilter']]],
                interceptUrlMap: [[pattern: '/public/**', access: ['denyAll']],
                                  [pattern: '/admin/**', access: ['ROLE_ADMIN']],
                                  [pattern: '/**', access: ['permitAll']]]]]]]))
        def f = new SecurityFilter(cfg)
        f.sessions = new org.grox.session.MemorySessionRepository('SESSION', 60)
        String token = f.jwt.create(auth('ROLE_ADMIN').principal)
        Closure<GroxRequest> get = { String p, String bearer = null ->
            def r = new GroxRequest(method: 'GET', path: p, uri: p)
            r.headers.set('Accept', 'text/html')
            if (bearer) r.headers.set('Authorization', 'Bearer ' + bearer)
            r
        }
        // /api/** sem anonymousAuthenticationFilter: 401 mesmo com permitAll, e sem redirecionar o navegador
        assert f.process(get('/api/recado'), null).errorStatus == 401
        assert f.process(get('/api/recado', token), null) == null
        assert f.process(get('/api/recado', 'invalido'), null).errorStatus == 401
        // /** sem restTokenValidationFilter: o token é ignorado (anônimo); permitAll passa, ROLE_ADMIN não
        assert f.process(get('/home', 'invalido'), null) == null
        assert f.process(get('/admin/x', token), null).errorStatus == 401
        assert f.process(get('/admin/x'), null).response?.getHeader('Location') == '/login/auth'
        // /public/** sem filterInvocationInterceptor: nenhuma autorização (o denyAll não se aplica)
        assert f.process(get('/public/x'), null) == null
        // login REST continua no /api/**
        assert cfg.isFrameworkUrl('/api/login')
    }

    static class PainelController {
        def index() { 'ok' }
        def lista = { 'lista' }
        private ajudante = { 'interno' }
    }

    @Test
    void 'actions closure: so propriedades, campo private nao vira action'() {
        def c = new org.grox.web.ControllerArtefact(PainelController)
        assert 'lista' in c.actions.keySet() && 'index' in c.actions.keySet() && !('ajudante' in c.actions.keySet())
    }

    @Test
    void 'refresh recusado para conta desabilitada, bloqueada ou com senha expirada'() {
        def cfg = SecurityConfig.from(new Config([grails: [plugin: [springsecurity: [
                rest: [token: [storage: [jwt: [secret: '0123456789abcdef0123456789abcdef']]]]]]]]))
        def f = new SecurityFilter(cfg)
        f.sessions = new org.grox.session.MemorySessionRepository('SESSION', 60)
        def user = new GroxUser(username: 'ana', password: '{noop}x', enabled: true)
        user.roles = ['ROLE_USER']
        f.users = { String n -> n == 'ana' ? user : null } as org.grox.security.UserDetailsService
        String refresh = f.jwt.createRefresh(user)
        Closure<Integer> renovar = {
            def r = new GroxRequest(method: 'POST', path: '/oauth/access_token', uri: '/oauth/access_token')
            r.formParameters = [grant_type: ['refresh_token'], refresh_token: [refresh]]
            f.process(r, null).response.status
        }
        assert renovar() == 200
        user.passwordExpired = true
        assert renovar() == 403   // antes: renovava até o refresh vencer (30 dias)
        user.passwordExpired = false
        user.accountLocked = true
        assert renovar() == 403
    }

    @grails.plugin.springsecurity.annotation.Secured('ROLE_ADMIN')
    static class RelatorioController {
        @grails.plugin.springsecurity.annotation.Secured(value = 'ROLE_USER', httpMethod = 'POST')
        def gerar() { 'ok' }
        def index() { 'ok' }
    }

    @Test
    void '@Secured da action com outro httpMethod: vale o do controller'() {
        def rules = new org.grox.plugins.springsecurity.SecuredRules()
        def route = { String a -> new org.grox.routing.RouteMatch(controller: new org.grox.web.ControllerArtefact(RelatorioController), actionName: a) }
        Closure<GroxRequest> req = { String m -> new GroxRequest(method: m, path: '/relatorio/gerar', uri: '/relatorio/gerar') }
        assert rules.accessFor(req('POST'), route('gerar')) == ['ROLE_USER']
        // antes: null, e a decisão caía no interceptUrlMap (um '/**' isAuthenticated() deixava passar)
        assert rules.accessFor(req('GET'), route('gerar')) == ['ROLE_ADMIN']
        assert rules.accessFor(req('GET'), route('index')) == ['ROLE_ADMIN']
    }

    @Test
    void 'controller so com actions closure: metodos do ControllerApi nao viram actions'() {
        def gcl = new GroovyClassLoader(SecurityTest.classLoader)
        def src = 'class VelhoController {\n    def lista = { render \'x\' }\n}'
        Class<?> type = gcl.parseClass(new GroovyCodeSource(src, '/app/grails-app/controllers/VelhoController.groovy', '/groovy/script'))
        assert org.grox.web.api.ControllerApi.isAssignableFrom(type)   // a AST aplicou o trait
        // antes: redirect, render, respond, bindData... viravam actions (GET /velho/redirect?url=... era open redirect)
        assert new org.grox.web.ControllerArtefact(type).actions.keySet() == ['lista'] as Set
    }

    @Test
    void 'caminho canonico: formato e barra final nao escapam da regra da action'() {
        assert SecurityFilter.canonicalPath('/painel.json') == '/painel' && SecurityFilter.canonicalPath('/painel/') == '/painel'
        assert SecurityFilter.canonicalPath('/a/b.c/d') == '/a/b.c/d' && SecurityFilter.canonicalPath('/') == '/'
        def cfg = SecurityConfig.from(new Config([grails: [plugin: [springsecurity: [
                interceptUrlMap: [[pattern: '/painel/x.json', access: ['permitAll']],
                                  [pattern: '/painel/**', access: ['ROLE_ADMIN']],
                                  [pattern: '/**', access: ['isAuthenticated()']]]]]]]))
        def f = new SecurityFilter(cfg)
        f.sessions = new org.grox.session.MemorySessionRepository('SESSION', 60)
        def route = new org.grox.routing.RouteMatch(controller: new org.grox.web.ControllerArtefact(PainelController), actionName: 'index')
        Closure<GroxRequest> get = { String p -> new GroxRequest(method: 'GET', path: p, uri: p) }
        Authentication user = auth('ROLE_USER')
        Closure<Object> logado = { String p ->
            def r = get(p)
            def s = f.sessions.create()
            s.setAttribute(SecurityFilter.SESSION_AUTH, user)
            f.sessions.save(s)
            r.cookieValues['SESSION'] = s.id
            f.process(r, route)
        }
        assert logado('/painel').errorStatus == 403
        assert logado('/painel.json').errorStatus == 403      // antes caía no /** (isAuthenticated) e passava
        assert logado('/painel/').errorStatus == 403
        assert logado('/painel/x.json') == null               // regra explícita para o .json continua valendo
    }

    @Test
    void 'actuator e console conferem o acesso mesmo em cadeia sem autorizacao ou none'() {
        def cfg = SecurityConfig.from(new Config([grails: [plugin: [springsecurity: [
                filterChain    : [chainMap: [[pattern: '/actuator/**', filters: 'none'],
                                             [pattern: '/**', filters: 'JOINED_FILTERS,-filterInvocationInterceptor']]],
                interceptUrlMap: [[pattern: '/**', access: ['permitAll']]]]]]]))
        def f = new SecurityFilter(cfg)
        f.sessions = new org.grox.session.MemorySessionRepository('SESSION', 60)
        f.endpoints = [new org.grox.management.Management(new Config([:]), null, true)]
        Closure<GroxRequest> get = { String p -> new GroxRequest(method: 'GET', path: p, uri: p, headers: new org.grox.http.HeaderMap().tap { set('Accept', 'application/json') }) }
        assert !f.bypasses(get('/actuator/metrics'), false, false)           // filters: none não tira a segurança do actuator
        assert f.process(get('/actuator/metrics'), null).errorStatus == 401  // metricsAccess: ROLE_ADMIN
        assert f.process(get('/actuator/health'), null) == null
        assert f.process(get('/qualquer'), null) == null                     // sem filterInvocationInterceptor: sem autorização
    }

    @Test
    void 'Set-Cookie sem injecao de atributos'() {
        def r = new org.grox.http.GroxResponse()
        r.addCookie(new jakarta.servlet.http.Cookie('lang', 'pt'))
        assert r.headers.getFirst('Set-Cookie').startsWith('lang=pt')
        assertThrows(IllegalArgumentException) { r.addCookie(new jakarta.servlet.http.Cookie('lang', 'pt; Domain=.evil.com')) }
    }

    @Test
    void 'expireNow do SessionInformation encerra a sessao'() {
        def repo = new org.grox.session.MemorySessionRepository('SESSION', 60)
        def s = repo.create()
        def logado = auth('ROLE_USER')
        s.setAttribute(SecurityFilter.SESSION_AUTH, logado)
        repo.save(s)
        def old = org.springframework.security.core.session.SessionRegistryImpl.repository
        org.springframework.security.core.session.SessionRegistryImpl.repository = repo
        try {
            def infos = new org.springframework.security.core.session.SessionRegistryImpl().getAllSessions(logado.principal, false)
            assert infos.size() == 1
            infos*.expireNow()
            assert repo.find(s.id) == null
        } finally {
            org.springframework.security.core.session.SessionRegistryImpl.repository = old
        }
    }

    @Test
    void 'rest: JWT com Bearer por padrao, GORM e cabecalho X-Auth-Token configuraveis'() {
        def jwtBearer = SecurityConfig.from(new Config([grails: [plugin: [springsecurity: [rest: [active: true]]]]]))
        assert jwtBearer.restActive && !jwtBearer.gormTokenStorage && jwtBearer.useBearerToken && jwtBearer.tokenHeaderName == 'X-Auth-Token'
        assert new SecurityFilter(jwtBearer).jwt != null
        assert jwtBearer.isFrameworkUrl('/oauth/access_token')

        def gorm = SecurityConfig.from(new Config([grails: [plugin: [springsecurity: [rest: [token: [
                storage   : [useGorm: true, gorm: [tokenDomainClassName: 'x.AuthenticationToken']],
                validation: [useBearerToken: 'false', headerName: 'X-Token']]]]]]]))
        assert gorm.restActive && gorm.gormTokenStorage && gorm.tokenDomainClassName == 'x.AuthenticationToken'
        assert gorm.tokenValuePropertyName == 'tokenValue' && gorm.tokenUsernamePropertyName == 'username'
        assert !gorm.useBearerToken && gorm.tokenHeaderName == 'X-Token'
        assert new SecurityFilter(gorm).jwt == null
        assert gorm.isFrameworkUrl('/api/login') && !gorm.isFrameworkUrl('/oauth/access_token')
    }

    @Test
    void 'refresh token nao acessa API e revogacao invalida o token'() {
        def jwt = new Jwt('0123456789abcdef0123456789abcdef', 60)
        def user = auth('ROLE_USER').principal
        String refresh = jwt.createRefresh(user)
        assert jwt.authenticate(refresh) == null
        assert jwt.verifyRefresh(refresh).sub == 'ana'
        String access = jwt.create(user)
        def a = jwt.authenticate(access)
        assert a.credentials.jti
        assert jwt.verifyRefresh(access) == null
        jwt.revoke(a.credentials)
        assert jwt.authenticate(access) == null
        jwt.revoke(jwt.verifyRefresh(refresh))
        assert jwt.verifyRefresh(refresh) == null
    }

    @Test
    void 'controllerAnnotations staticRules somadas ao interceptUrlMap, em lista ou na forma de mapa'() {
        def lista = SecurityConfig.from(new Config([grails: [plugin: [springsecurity: [
                interceptUrlMap      : [[pattern: '/admin/**', access: ['ROLE_ADMIN']]],
                controllerAnnotations: [staticRules: [[pattern: '/', access: ['permitAll']], [pattern: '/api/**', httpMethod: 'GET', access: ['isAuthenticated()']]]]]]]]))
        assert lista.rules*.pattern == ['/admin/**', '/', '/api/**']
        assert lista.rules.find { it.matches('/api/x', 'GET') }.access == ['isAuthenticated()']
        assert lista.rules.find { it.matches('/api/x', 'POST') } == null
        def mapa = SecurityConfig.from(new Config([grails: [plugin: [springsecurity: [
                controllerAnnotations: [staticRules: ['/': ['permitAll'], '/assets/**': 'permitAll']]]]]]))
        assert mapa.rules*.pattern == ['/', '/assets/**'] && mapa.rules*.access == [['permitAll'], ['permitAll']]
    }
}
