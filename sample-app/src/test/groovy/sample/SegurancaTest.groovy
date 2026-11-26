package sample

import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test


/** Segurança (form, Basic, JWT, CSRF, regras, @Secured) e multi-tenancy pelo usuário logado. */
class SegurancaTest {

    static GroxRuntime app
    static String base

    @BeforeAll
    static void start() {
        System.setProperty('grails.env', 'test')
        app = GroxApp.run(Application)
        base = "http://localhost:${app.port}"
    }

    @AfterAll
    static void stop() {
        app?.close()
    }

    static TestClient client() {
        new TestClient(base)
    }

    @Test
    void 'form login: redirect para o login, URL salva, session fixation e mensagens de erro'() {
        def c = client()
        def denied = c.page('/usuario/create')
        assert denied.status() == 302 && denied.headers().firstValue('Location').get() == '/login/auth'
        String anonymousSession = c.sessionId()

        def bad = c.login('admin', 'errada')
        assert bad.headers().firstValue('Location').get() == '/login/auth?login_error=1'
        assert c.page('/login/auth?login_error=1').body().contains('Usuário ou senha inválidos.')
        assert c.login('inativo', 'inativo').headers().firstValue('Location').get().contains('login_error')
        assert c.page('/login/auth?login_error=1').body().contains('Sua conta está desativada.')

        def ok = c.login('admin', 'admin')
        assert ok.status() == 302 && ok.headers().firstValue('Location').get() == '/usuario/create'
        assert c.sessionId() != anonymousSession
        assert c.page('/usuario/create').status() == 200
        def home = c.page('/').body()
        assert home.contains('<span class="usuario">admin</span>') && !home.contains('/login/auth')
    }

    @Test
    void 'pagina de login com sessao de dados aberta: coleção lazy no layout ou TagLib'() {
        // ler a coleção lazy, mesmo vazia, já exige a sessão
        Long id = Grupo.withTransaction { new Grupo(nome: 'Login').save(failOnError: true).id }
        try {
            def page = client().page('/login/auth')
            assert page.status() == 200 && page.body().contains('Login: 0')
            // params.controller/action e controllerName/actionName como no LoginController.auth do plugin
            assert page.body().contains('<p class="login-rota">login/auth|login/auth</p>')
        } finally {
            Grupo.withTransaction { Grupo.get(id).delete(flush: true) }
        }
    }

    @Test
    void 'cabecalhos de seguranca padrao e cookie Secure atras de HTTPS'() {
        def page = client().page('/login/auth')
        assert page.headers().firstValue('X-Content-Type-Options').get() == 'nosniff'
        assert page.headers().firstValue('X-Frame-Options').get() == 'SAMEORIGIN'
        assert page.headers().firstValue('Referrer-Policy').get() == 'strict-origin-when-cross-origin'
        String plain = page.headers().allValues('Set-Cookie').find { it.startsWith('GROXSESSIONID=') }
        assert plain && !plain.contains('Secure')
        def https = client().send('GET', '/login/auth', null, ['X-Forwarded-Proto': 'https'])
        assert https.headers().allValues('Set-Cookie').find { it.startsWith('GROXSESSIONID=') }.contains('; Secure')
    }

    @Test
    void 'limite de tentativas de login'() {
        // usuário exclusivo deste teste (o limite é por usuário e por IP+usuário)
        5.times { assert client().send('POST', '/api/login', [username: 'forca-bruta', password: "x${it}"]).status() == 401 }
        def blocked = client().send('POST', '/api/login', [username: 'forca-bruta', password: 'y'])
        assert blocked.status() == 429 && blocked.headers().firstValue('Retry-After').get().toInteger() > 0
        def basic = client().send('GET', '/api/usuarios', null, [Authorization: 'Basic ' + 'forca-bruta:z'.bytes.encodeBase64()])
        assert basic.status() == 429
        // formulário: mensagem no flash
        def c = client()
        assert c.login('forca-bruta', 'w').headers().firstValue('Location').get().contains('login_error')
        assert c.page('/login/auth?login_error=1').body().contains('Muitas tentativas de login')
        // outros usuários não são afetados
        assert client().send('POST', '/api/login', [username: 'maria', password: 'maria']).status() == 200
    }

    @Test
    void 'metodos HTTP desconhecidos nao chegam as actions'() {
        assert client().send('FOO', '/').status() == 501
        assert client().send('TRACE', '/').status() == 405
        assert client().send('GET', '/').status() == 200
    }

    @Test
    void 'URL salva nao redireciona para outro site'() {
        def c = client()
        def denied = c.page('//evil.example/x')
        assert denied.status() == 302 && denied.headers().firstValue('Location').get() == '/login/auth'
        def ok = c.login('admin', 'admin')
        assert ok.headers().firstValue('Location').get() == '/'      // defaultTargetUrl, não //evil.example/x
        assert org.grox.security.SecurityFilter.isLocalUrl('/usuario/show/1?x=1')
        assert !org.grox.security.SecurityFilter.isLocalUrl('//evil.example') && !org.grox.security.SecurityFilter.isLocalUrl('/\\evil.example')
        assert !org.grox.security.SecurityFilter.isLocalUrl('http://evil.example') && !org.grox.security.SecurityFilter.isLocalUrl('/a\r\nb')
    }

    @Test
    void 'LoginController embutido: login redireciona e a falha vai no flash'() {
        def c = client()
        def l = c.page('/login')
        assert l.status() == 302 && l.headers().firstValue('Location').get() == '/login/auth'
        assert c.page('/login/index').headers().firstValue('Location').get() == '/login/auth'
        c.login('admin', 'errada')
        // a página seguinte vê a mensagem no flash (como o authfail do plugin)
        String msg = c.page('/home/lerMensagem').body()
        assert msg in ['Usuário ou senha inválidos.', 'Sorry, we were not able to find a user with that username and password.']
        c.loggedIn('admin', 'admin')
        assert c.page('/login').headers().firstValue('Location').get() == '/'
    }

    @Test
    void 'CSRF obrigatorio em POST com sessao, inclusive no login'() {
        def c = client()
        assert c.form('/login/authenticate', [username: 'admin', password: 'admin'], false).status() == 403
        c.loggedIn('admin', 'admin')
        assert c.form('/usuario/save', [nome: 'Sem Token', email: 'st@x.com'], false).status() == 403
        assert c.form('/usuario/save', [nome: 'Com Token', email: 'ct@x.com']).status() == 302
        // o formulário gerado por <g:form> já traz o token
        assert c.page('/usuario/create').body() =~ /<input type="hidden" name="_csrf" value="[^"]+"\/>/
        // POST anônimo sem sessão (API pública): não há estado a forjar, dispensa o token
        assert client().form('/home/olaParams', [nome: 'x'], false).status() == 200
        // ... mas vindo de outro site pelo navegador exige (login CSRF: a action poderia logar a vítima)
        assert client().send('POST', '/home/olaParams', [nome: 'x'], ['Sec-Fetch-Site': 'cross-site']).status() == 403
        assert client().send('POST', '/home/olaParams', [nome: 'x'], [Origin: 'http://evil.example']).status() == 403
        def anonimo = client()
        anonimo.page('/login/auth')   // cria a sessão (token CSRF do formulário de login)
        assert anonimo.form('/home/olaParams', [nome: 'x'], false).status() == 403
    }

    @Test
    void 'roles, rejectIfNoRule, @Secured e pagina 403'() {
        def maria = client().loggedIn('maria', 'maria')
        def forbidden = maria.page('/usuario/create')
        assert forbidden.status() == 403 && forbidden.body().contains('Você não tem permissão')
        assert maria.page('/usuario/show/1').status() == 404 // autorizado (isAuthenticated), mas inexistente
        assert maria.page('/home/painel').status() == 403
        def admin = client().loggedIn('admin', 'admin')
        assert admin.page('/home/painel').body() == 'Painel de admin'
        assert client().page('/home/painel').status() == 302
        // <g:actionSubmit>: _action_painel numa URL permitAll roda o painel, então vale o @Secured do painel
        assert maria.form('/home/olaParams', [nome: 'x', _action_painel: 'x']).status() == 403
        assert admin.form('/home/olaParams', [nome: 'x', _action_painel: 'x']).body() == 'Painel de admin'
        // URL sem regra específica cai no '/**' (isAuthenticated())
        assert client().page('/nao/existe').status() == 302
        assert admin.page('/nao/existe').status() == 404
        assert client().page('/').status() == 200
        assert client().page('/static/css/app.css').status() == 200
    }

    @Test
    void 'logout exige POST com CSRF e invalida a sessao'() {
        def c = client().loggedIn('admin', 'admin')
        assert c.page('/logoff').status() == 405
        def out = c.form('/logoff', [:])
        assert out.status() == 302 && out.headers().firstValue('Location').get() == '/'
        assert out.headers().allValues('Set-Cookie').any { it.startsWith('GROXSESSIONID=;') && it.contains('Max-Age=0') }
        assert c.page('/usuario/create').status() == 302
    }

    @Test
    void 'LogoutController embutido: desloga direto, respeitando o postOnly e o CSRF'() {
        def c = client().loggedIn('admin', 'admin')
        assert c.page('/logout').status() == 405          // postOnly: true (padrão)
        assert c.page('/logout/index/1').status() == 405  // <g:link controller="logout" id="1">
        assert c.form('/logout', [:], false).status() == 403   // sem o token CSRF
        // antes: 302 para /logoff, que o navegador seguia com GET (405 com postOnly) e a sessão continuava
        def out = c.form('/logout', [:])
        assert out.status() == 302 && out.headers().firstValue('Location').get() == '/'
        assert c.page('/usuario/show/1').status() == 302    // deslogado: vai para o login
    }

    @Test
    void 'HTTP Basic e JWT em rotas stateless'() {
        def anon = client().send('GET', '/api/usuarios', null, [Accept: 'application/json'])
        assert anon.status() == 401 && anon.headers().firstValue('WWW-Authenticate').get() == 'Basic realm="Grox"'
        def basic = client().send('GET', '/api/usuarios', null, [Authorization: 'Basic ' + 'maria:maria'.bytes.encodeBase64()])
        assert basic.status() == 200 && !basic.headers().firstValue('Set-Cookie').present
        assert client().send('GET', '/api/usuarios', null, [Authorization: 'Basic ' + 'maria:x'.bytes.encodeBase64()]).status() == 401

        def login = client().send('POST', '/api/login', [username: 'maria', password: 'maria'])
        def body = TestClient.parse(login)
        assert body.username == 'maria' && body.roles == ['ROLE_USER'] && body.token_type == 'Bearer' && body.expires_in == 3600
        assert client().send('POST', '/api/login', [username: 'maria', password: 'x']).status() == 401

        def maria = client()
        maria.bearer = body.access_token
        assert maria.send('GET', '/api/usuarios').status() == 200
        assert maria.send('POST', '/api/usuarios', [nome: 'X', email: 'x@x.com']).status() == 403
        def admin = client().withToken('admin', 'admin')
        assert admin.send('POST', '/api/usuarios', [nome: 'Via Jwt', email: 'jwt@x.com']).status() == 201
        def forged = client()
        forged.bearer = body.access_token[0..-3] + 'xx'
        assert forged.send('GET', '/api/usuarios').status() == 401
        // usuário em memória (yml)
        assert client().withToken('suporte', 'suporte').send('DELETE', '/api/usuarios/999999').status() == 404
    }

    @Test
    void 'Basic exige CSRF quando o navegador envia de outro site'() {
        String basic = 'Basic ' + 'maria:maria'.bytes.encodeBase64()
        // o navegador reenvia Basic guardado num POST de outro site: sem token CSRF, recusa
        assert client().send('POST', '/tarefa/save', [titulo: 'CSRF'], [Authorization: basic, Origin: 'http://evil.example']).status() == 403
        assert client().send('POST', '/tarefa/save', [titulo: 'CSRF'], [Authorization: basic, 'Sec-Fetch-Site': 'cross-site']).status() == 403
        // cliente de API (sem Origin/Sec-Fetch-*) e mesma origem continuam sem CSRF
        assert client().send('POST', '/tarefa/save', [titulo: 'Via Basic'], [Authorization: basic]).status() == 201
        assert client().send('POST', '/tarefa/save', [titulo: 'Mesma origem'], [Authorization: basic, 'Sec-Fetch-Site': 'same-origin']).status() == 201
        // Bearer não é enviado sozinho pelo navegador: dispensa CSRF mesmo com Origin
        assert client().withToken('maria', 'maria').send('POST', '/tarefa/save', [titulo: 'Bearer'], [Origin: 'http://evil.example']).status() == 201
    }

    @Test
    void 'multi-tenancy: cada empresa ve so as proprias tarefas'() {
        def maria = client().withToken('maria', 'maria')     // acme
        def joao = client().withToken('joao', 'joao')        // globex
        def created = maria.send('POST', '/tarefa/save', [titulo: 'Da Maria', tenantId: 'globex'])
        assert created.status() == 201
        def tarefa = TestClient.parse(created)
        assert !tarefa.containsKey('tenantId')

        List mariaVe = TestClient.parse(maria.send('GET', '/tarefa/index.json'))*.titulo
        List joaoVe = TestClient.parse(joao.send('GET', '/tarefa/index.json'))*.titulo
        assert mariaVe.containsAll(['Revisar contrato', 'Da Maria']) && !mariaVe.contains('Planejar sprint')
        assert joaoVe.contains('Planejar sprint') && !joaoVe.contains('Da Maria')
        assert joao.send('GET', "/tarefa/show/${tarefa.id}.json").status() == 404
        assert Tarefa.withTenant('acme') { Tarefa.get(tarefa.id as Long).tenantId } == 'acme'

        def resumo = TestClient.parse(maria.send('GET', '/tarefa/resumo.json'))
        assert resumo.tenant == 'acme' && resumo.pendentes.contains('Da Maria') && resumo.totalGeral >= 3

        // sessão (form login): tenant vem do usuário; a página usa o layout e o CSRF
        def browser = client().loggedIn('joao', 'joao')
        def page = browser.page('/tarefa/index').body()
        assert page.contains('Planejar sprint') && !page.contains('Revisar contrato') && page.contains('>globex</span>')
        assert browser.form('/tarefa/save', [titulo: 'Pelo formulário']).status() == 302
        assert TestClient.parse(joao.send('GET', '/tarefa/index.json'))*.titulo.contains('Pelo formulário')

        // usuário sem tenant não acessa dados multi-tenant
        assert client().withToken('suporte', 'suporte').send('GET', '/tarefa/index.json').status() == 403
    }
}
