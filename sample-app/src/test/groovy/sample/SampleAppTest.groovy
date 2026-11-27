package sample

import groovy.json.JsonSlurper
import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import groovy.http.HttpResult

import java.util.concurrent.CompletableFuture

/** Sobe a aplicação real (ambiente test, porta aleatória) e testa via HTTP. */
class SampleAppTest {

    static GroxRuntime app
    /** Sessão de navegador autenticada como admin. */
    static TestClient client
    static String base
    /** Token JWT do admin, usado nas chamadas a /api/** (stateless). */
    static String adminToken

    @BeforeAll
    static void start() {
        System.setProperty('grails.env', 'test')
        app = GroxApp.run(Application)
        base = "http://localhost:${app.port}"
        client = new TestClient(base)
        adminToken = new TestClient(base).token('admin', 'admin')
        assert client.login('admin', 'admin').status() == 302
    }

    static String csrf() {
        client.csrfToken()
    }

    @AfterAll
    static void stop() {
        app?.close()
    }

    /** Requisição na sessão do admin; em /api/** vai o JWT do admin, se nenhum Authorization for dado. */
    static HttpResult call(String method, String path, Object json = null, Map<String, String> headers = [:]) {
        Map<String, String> all = new LinkedHashMap(headers)
        if (path.startsWith('/api/') && !all.containsKey('Authorization')) all.Authorization = "Bearer ${adminToken}".toString()
        client.send(method, path, json, all)
    }

    /** POST de formulário com o token CSRF da sessão. */
    static HttpResult form(String path, Map<String, Object> fields) {
        client.form(path, fields)
    }

    static HttpResult page(String path) {
        client.page(path)
    }

    static Object parse(HttpResult r) {
        TestClient.parse(r)
    }

    @Test
    void 'artefatos descobertos e configurados'() {
        assert app.controllers.keySet() == ['home', 'erro', 'usuario', 'tarefa', 'async', 'transacao', 'semPacote', 'grupo'] as Set
        assert app.controllers.home.dispatcher.class.simpleName == 'PerRequestControllerDispatcher'
        assert app.controllers.usuario.dispatcher.size == 16
        // proxies: transacional (UsuarioService) e ator (EstatisticaService)
        assert app.services.beans.usuarioService.class != UsuarioService
        assert app.services.beans.usuarioService instanceof UsuarioService
        assert app.services.beans.estatisticaService instanceof EstatisticaService
        assert app.services.actorCount == 1
        assert app.services.beans.saudacaoService.class == SaudacaoService
        assert app.services.beans.saudacaoService.iniciado   // @PostConstruct depois da injeção
        assert call('GET', '/home/olaParams?nome=Ana').body() == 'Olá, Ana!'   // params como GrailsParameterMap
        assert call('GET', '/home/fechamento?x=1').body() == 'closure 1 Olá, Bia!'   // action closure (def x = { })
        assert call('GET', '/semPacote/index').body() == 'sem pacote'   // controller no pacote padrão
        assert call('GET', '/semPacote/nulo').body() == 'null'          // render text: null, como no Grails
        assert call('GET', '/semPacote/jsonDuplo').body() == '{"a":1}'  // as JSON fecha o writer: 2º render descartado
        assert call('GET', '/home/mav').body().trim() == '1|do interceptor'   // modelAndView no after()
        assert call('GET', '/home/tagDireta').body() == '1970'                 // tag g: direto na action
        assert call('GET', '/home/classLoaderContexto').body() == 'true'       // context classloader = o da app
        assert app.datastore.domains.keySet()*.simpleName as Set == ['Usuario', 'Grupo', 'Tarefa', 'User', 'Role', 'UserRole', 'AuthenticationToken', 'Nota', 'Contrato', 'AuditTrail'] as Set
        assert app.security.cfg.rules.size() == 19
    }

    @Test
    void 'CRUD REST com GORM'() {
        def grupo = Grupo.withTransaction { new Grupo(nome: 'Rest').save(failOnError: true) }
        def created = call('POST', '/api/usuarios', [nome: 'Carla', email: 'carla@x.com', idade: '25', grupo: [id: grupo.id]])
        assert created.status() == 201
        def u = parse(created)
        assert u.nome == 'Carla' && u.idade == 25 && u.id && u.grupo == [id: grupo.id] && u.dateCreated

        assert parse(call('GET', "/api/usuarios/${u.id}")).email == 'carla@x.com'
        assert call('GET', '/api/usuarios').status() == 200

        def updated = parse(call('PUT', "/api/usuarios/${u.id}", [idade: 26, id: 999, grupo: null]))
        assert updated.id == u.id && updated.idade == 26 && updated.nome == 'Carla' && updated.grupo == null
        assert Usuario.get(u.id).version == 1

        assert call('DELETE', "/api/usuarios/${u.id}").status() == 204
        assert call('GET', "/api/usuarios/${u.id}").status() == 404
    }

    @Test
    void 'validacao, allowedMethods e metodo nao mapeado'() {
        def invalid = call('POST', '/api/usuarios', [nome: '', email: 'x'])
        assert invalid.status() == 422
        assert parse(invalid).errors*.field == ['nome', 'email']
        assert parse(invalid).errors*.message == ['Informe o nome', 'E-mail inválido: x']
        call('POST', '/api/usuarios', [nome: 'Único', email: 'unico@x.com'])
        // cliente sem cookies: o locale guardado na sessão (teste de i18n) não interfere
        def duplicate = new TestClient(base).send('POST', '/api/usuarios', [nome: 'Outro', email: 'unico@x.com'],
                ['Accept-Language': 'en', Authorization: "Bearer ${adminToken}".toString()])
        assert duplicate.status() == 422
        assert parse(duplicate).errors*.message == ['There is already a user with e-mail unico@x.com']
        assert call('PUT', '/api/usuarios/999999', [nome: 'x']).status() == 404
        assert call('GET', '/usuario/save').status() == 405
        assert call('PATCH', '/api/usuarios').status() == 405
    }

    @Test
    void 'render texto, as JSON, formato por extensao e HEAD'() {
        assert call('GET', '/ola/Ana').body() == 'Olá, Ana!'
        assert call('GET', '/ola').body() == 'Olá, mundo!'
        assert page('/').body().contains('VirtualThread')
        def id = parse(call('POST', '/api/usuarios', [nome: 'Duda', email: 'd@x.com'])).id
        def exported = call('GET', "/usuario/exportar/${id}")
        assert exported.headers().firstValue('Content-Type').get() == 'application/json;charset=UTF-8'
        assert parse(exported).nome == 'Duda'
        assert parse(call('GET', '/usuario/index.json?max=1')).size() == 1
        def head = call('HEAD', '/ola/x')
        assert head.status() == 200 && head.body() == ''
    }

    @Test
    void 'grailsAttributes no controller e na taglib'() {
        def body = page('/home/atributos').body()
        assert body.contains('<p id="controller">/home</p>')
        assert body.contains('<p id="tag">/home/atributos 30 anos</p>')
    }

    @Test
    void 'API servlet e Spring: RequestContextHolder, HttpServletRequest, sessao e SessionTenantResolver'() {
        def client = new TestClient(base)
        assert client.page('/home/servlet?tenant=acme').body() == 'acme|home/servlet|true'
        assert client.page('/home/servlet').body() == 'acme|home/servlet|true'   // continua na sessão
    }

    @Test
    void 'audit log: insert, update com valor antigo e novo, delete, mascara, truncamento e withoutAuditLog'() {
        def id = Contrato.withTransaction {
            new Contrato(numero: 'C-1', valor: 10, senha: 'segredo').save(failOnError: true, flush: true).id
        }
        // gravado depois do commit: nada antes dele
        Contrato.withTransaction {
            def c = Contrato.get(id)
            c.valor = 20
            c.numero = 'C-1 com um número bem comprido'
            c.save(flush: true)
            assert AuditTrail.withNewSession { AuditTrail.countByEventName('UPDATE') } == 0
        }
        Contrato.withTransaction { Contrato.get(id).delete(flush: true) }
        grails.plugins.orm.auditable.AuditLogContext.withoutAuditLog {
            Contrato.withTransaction { new Contrato(numero: 'C-2', valor: 1).save(failOnError: true, flush: true) }
        }
        def logs = AuditTrail.withTransaction { AuditTrail.list(sort: 'id').collect { a -> ['eventName', 'propertyName', 'oldValue', 'newValue', 'className', 'actor', 'persistedObjectId'].collectEntries { [it, a[it]] } } }
        assert logs.every { it.className == 'Contrato' && it.actor == 'SYS' && it.persistedObjectId == id.toString() }
        def ev = { String e -> logs.findAll { it.eventName == e }.collectEntries { [it.propertyName, [it.oldValue, it.newValue]] } }
        assert ev('INSERT') == [numero: [null, 'C-1'], valor: [null, '10'], senha: [null, '**********']]
        assert ev('UPDATE') == [valor: ['10.00', '20'], numero: ['C-1', 'C-1 com um número be']]   // antigo lido do banco; maxSize 20
        assert ev('DELETE') == [numero: ['C-1 com um número be', null], valor: ['20.00', null], senha: ['**********', null]]
    }

    @Test
    void 'doWithSpring da Application e do descritor de plugin da biblioteca'() {
        def ctx = app.grailsApplication.mainContext
        assert ctx.getBean('primeira').proxima.is(ctx.getBean('segunda')) && ctx.getBean('segunda').texto == 'b'
        assert ctx.getBean('terceira').texto == 'bc'   // nome de bean sem ref()
        assert ctx.getBean('formatadorNota').prefixo == 'Nota: '
        assert extra.ExtraGrailsPlugin.contextoPronto   // doWithApplicationContext, com o bean já injetado
        assert app.plugins*.getClass()*.simpleName == ['ExtraGrailsPlugin']
    }

    @Test
    void 'plugin rendering: PDF de template GSP na resposta, em bytes e por renderPdf'() {
        def r = new TestClient(base).binary('/home/pdf')
        byte[] pdf = TestClient.bytes(r)
        assert r.status() == 200 && r.headers().firstValue('Content-Type').get().startsWith('application/pdf')
        assert r.headers().firstValue('Content-Disposition').get().contains('relatorio.pdf')
        assert new String(pdf, 0, 4) == '%PDF' && pdf.length > 500
        assert call('GET', '/home/pdfBytes').body() == 'true'
        assert call('GET', '/home/pdfMetodo').headers().firstValue('Content-Type').get().startsWith('application/pdf')
    }

    @Test
    void 'biblioteca org_grox_lib: domain, service, TagLib e mensagens de outro pacote'() {
        assert app.datastore.domains.keySet()*.name.contains('extra.Nota')
        assert app.services.beans.notaService
        // a mensagem da biblioteca aparece; a que a aplicação redefine (home.titulo) prevalece
        assert call('GET', '/home/nota?texto=a').body() == '1 nota(s)|Bem-vindo ao Grox'
        assert call('GET', '/home/nota?texto=b').body() == '2 nota(s)|Bem-vindo ao Grox'
    }

    static List<String> grupos(String prefixo) {
        Grupo.withTransaction { Grupo.findAllByNomeLike("${prefixo}%")*.nome.sort() }
    }

    @Test
    void 'transacoes em actions: rollback, rollbackOnly, withNewTransaction, service e REQUIRES_NEW'() {
        assert call('GET', '/transacao/falha?nome=tx-falha').status() == 500
        assert grupos('tx-falha') == []

        assert call('GET', '/transacao/rollbackOnly?nome=tx-ro').body() == 'ok'
        assert grupos('tx-ro') == []

        assert call('GET', '/transacao/novaTransacao?nome=tx-nova').body() == 'ok'
        assert grupos('tx-nova') == ['tx-nova-dentro']

        assert call('GET', '/transacao/serviceFalha?nome=tx-svc').body() == 'ok'
        assert grupos('tx-svc') == []

        assert call('GET', '/transacao/requiresNew?nome=tx-rn').body() == 'ok'
        assert grupos('tx-rn') == ['tx-rn-isolado']

        assert call('GET', '/transacao/aninhada?nome=tx-an').body() == '2'
        assert grupos('tx-an') == ['tx-an-a', 'tx-an-b']
    }

    @Test
    void 'transacoes concorrentes em requisicoes simultaneas nao se misturam'() {
        def pool = java.util.concurrent.Executors.newFixedThreadPool(12)
        try {
            def futuros = (1..30).collect { int i ->
                pool.submit({ ->
                    // SampleAppTest.call: call() solto dentro de closure é o call() da closure (armadilha do Groovy 6)
                    i % 3 == 0 ? SampleAppTest.call('GET', "/transacao/falha?nome=tx-conc-f${i}").status()
                            : SampleAppTest.call('GET', "/transacao/criar?nome=tx-conc-ok${i}").status()
                } as java.util.concurrent.Callable)
            }
            assert futuros*.get().countBy { it } == [200: 20, 500: 10]
        } finally {
            pool.shutdown()
        }
        assert grupos('tx-conc-ok').size() == 20
        assert grupos('tx-conc-f') == []
    }

    @Test
    void 'cookies como na Servlet API'() {
        def r = call('GET', '/home/cookies', null, [Cookie: 'a=1'])
        assert r.body() == '1|1'
        assert r.headers().allValues('Set-Cookie').any { it.startsWith('b=2') && it.contains('Max-Age=60') && it.contains('HTTPOnly') }
    }

    @Test
    void 'spring-security-core para LoginController proprio: SCH, URL salva, falha de login, securityConfig'() {
        def c = new TestClient(base)
        // anônimo: a página protegida guarda a URL e manda para o login
        assert c.page('/usuario/show/1?aba=2').status() == 302
        def anon = c.send('GET', '/home/seguranca').body().split('\\|', -1)
        assert anon[0] == 'null' && anon[1] == 'true' && anon[2] == '/usuario/show/1' && anon[3] == 'aba=2'
        assert anon[6] == '/login/authenticate' && anon[7] == '/' && anon[8] == 'false' && anon[9] == 'vazio' && anon[10] == 'null'
        // login recusado: exceção e username na sessão, como no spring-security-core
        c.login('admin', 'errada')
        def falha = c.send('GET', '/home/seguranca', null, ['X-Requested-With': 'XMLHttpRequest']).body().split('\\|', -1)
        assert falha[4] == 'BadCredentialsException' && falha[5] == 'admin' && falha[8] == 'true'
        c.loggedIn('admin', 'admin')
        def logado = c.send('GET', '/home/seguranca').body().split('\\|', -1)
        assert logado[0] == 'admin' && logado[1] == 'false' && logado[4] == 'null' && logado[10] == 'admin'   // authenticatedUser
        assert 'admin' in c.send('GET', '/home/logados').body().split(',')
        assert c.send('GET', '/home/sair').body() == 'true'
        assert c.send('GET', '/home/seguranca').body().startsWith('null|true|')   // deslogado
    }

    @Test
    void 'grailsResourceLocator e consoleService'() {
        assert call('GET', '/home/recursos').body() == 'true|true|2|oi'
    }

    @Test
    void 'rotas do grox-dsl na app Grails: mesmas respostas dos controllers, com layout e asset'() {
        assert call('GET', '/dsl/ola/x').body() == call('GET', '/ola/x').body()
        assert call('GET', '/dsl-ator/ola/x').body() == 'Olá, x!'
        String lista = call('GET', '/dsl/usuarios').body()
        assert lista.contains('/assets/') && lista.contains('<title>Usuários')   // layout com asset: e a GSP da lista
        def api = new TestClient(base)
        def dsl = api.send('GET', '/dsl/api/usuarios/1', null, [Authorization: "Bearer ${adminToken}", Accept: 'application/json'])
        def controller = api.send('GET', '/api/usuarios/1', null, [Authorization: "Bearer ${adminToken}", Accept: 'application/json'])
        assert dsl.status() == controller.status()
        if (controller.status() == 200) assert dsl.body() == controller.body()
    }

    @Test
    void 'render com closure sem contentType e HTML pela DSL, com escape e tags g'() {
        def r = call('GET', '/home/htmlDsl?nome=%3Cb%3EAna%3C/b%3E')
        assert r.status() == 200 && r.headers().firstValue('Content-Type').get().startsWith('text/html')
        assert r.body() == '<div id="saudacao"><p>Olá &lt;b&gt;Ana&lt;/b&gt;</p><a href="/">início</a></div>'   // home/index é o "/" no UrlMappings
    }

    @Test
    void 'namespace de TagLib como propriedade no controller e na TagLib'() {
        assert call('GET', '/home/tagNamespace').body() == '30 anos|Idade: 30 anos'
    }

    @Test
    void 'request withFormat escolhe pelo Content-Type da requisicao'() {
        assert new TestClient(base).form('/home/formato', [a: 1]).body() == 'form'
        assert call('POST', '/home/formato', [a: 1], [Authorization: "Bearer ${adminToken}"]).body() == 'json'
        assert call('GET', '/home/formato').body() == 'form'      // sem corpo: o primeiro bloco, como no Grails
    }

    @Test
    void 'request JSON e um JSONObject do Grails'() {
        assert call('POST', '/home/corpoJson', [nome: 'Ana'], [Authorization: "Bearer ${adminToken}"]).body() == 'JSONObject|Ana|padrao'
    }

    @Test
    void 'sessao e flash entre requisicoes'() {
        int first = parse(call('GET', '/home/contador')).visitas
        assert parse(call('GET', '/home/contador')).visitas == first + 1
        def redirect = call('GET', '/home/mensagem')
        assert redirect.status() == 302
        assert redirect.headers().firstValue('Location').get() == '/home/lerMensagem'
        assert call('GET', '/home/lerMensagem').body() == 'Olá do flash'
        assert call('GET', '/home/lerMensagem').body() == 'vazio'
    }

    @Test
    void 'mapeamentos de erro 404 e 500 com negociacao'() {
        def notFound = call('GET', '/nao/existe', null, [Accept: 'application/json'])
        assert notFound.status() == 404
        assert parse(notFound) == [erro: 'não encontrado', path: '/nao/existe', status: 404]   // request['javax.servlet.error.status_code']
        def html = call('GET', '/nao/existe', null, [Accept: 'text/html'])
        assert html.status() == 404 && html.body().contains('Página não encontrada')
        def failure = call('GET', '/home/falha', null, [Accept: 'application/json'])
        assert failure.status() == 500
        assert parse(failure) == [erro: 'falha proposital']
    }

    @Test
    void 'requisicoes concorrentes gravando no banco e no service-ator'() {
        int before = parse(call('GET', '/usuario/contagem')).total
        def futures = (1..200).collect { i ->
            client.http.postAsync('/api/usuarios') {
                header 'Authorization', "Bearer ${adminToken}"
                json([nome: "U$i", email: "u$i@x.com"])
            }
        }
        CompletableFuture.allOf(futures as CompletableFuture[]).join()
        assert futures.every { it.get().status() == 201 }
        assert futures.collect { parse(it.get()).id }.toSet().size() == 200
        assert parse(call('GET', '/usuario/contagem')).total == before + 200
        assert app.services.beans.estatisticaService.resumo().eventos.criado >= 200
    }

    @Test
    void 'pagina HTML com layout, content tag, taglib da aplicacao e recurso estatico'() {
        call('POST', '/api/usuarios', [nome: 'Teo', email: 't@x.com', idade: 15])
        def r = page('/usuario/index')
        assert r.status() == 200
        assert r.headers().firstValue('Content-Type').get() == 'text/html;charset=UTF-8'
        String html = r.body()
        assert html.contains('<title>Usuários · Grox Sample</title>')
        assert html =~ /<link rel="stylesheet" href="\/assets\/application-[0-9a-f]{32}\.css"\/>/
        assert html =~ /<p class="submenu">Total: \d+<\/p>/
        assert html.contains('15 anos (menor)')
        assert html.contains('<a href="/usuario/create">Novo usuário</a>')

        def css = call('GET', '/static/css/app.css')
        assert css.status() == 200 && css.headers().firstValue('Content-Type').get().startsWith('text/css')
        assert call('GET', '/static/nao-existe.css').status() == 404
    }

    @Test
    void 'CRUD HTML com formularios, _method, flash e templates'() {
        def create = page('/usuario/create')
        assert create.body().contains('<form action="/usuario/save" method="post">')
        assert create.body().contains('<input type="text" name="nome" value="" required id="nome" />')

        def invalid = form('/usuario/save', [nome: '', email: 'x'])
        assert invalid.status() == 422
        assert invalid.body().contains('<li>Informe o nome</li>')
        assert invalid.body().contains('<li>E-mail inválido: x</li>')
        assert invalid.body().contains('<p class="com-erro">')
        assert invalid.body().contains('value="x"')

        def grupo = Grupo.withTransaction { new Grupo(nome: 'Html').save(failOnError: true) }
        def saved = form('/usuario/save', [nome: 'Lia <3', email: 'lia@x.com', idade: '40', 'grupo.id': grupo.id])
        assert saved.status() == 302
        String location = saved.headers().firstValue('Location').get()
        assert location ==~ /\/usuario\/show\/\d+/
        String id = location.tokenize('/').last()

        def show = page(location)
        assert show.body().contains('Usuário Lia &lt;3 criado')
        assert show.body().contains('<h1>Lia &lt;3 <span class="badge">#' + id + '</span></h1>')
        assert show.body().contains('<input type="hidden" name="_method" value="DELETE"/>')
        assert show.body().contains('<dd>Html</dd>')
        assert page("/usuario/edit/${id}").body().contains("<option value=\"${grupo.id}\" selected=\"selected\">Html</option>")
        assert !page(location).body().contains('criado')

        def rejected = form("/usuario/update/${id}", [_method: 'PUT', nome: 'L', email: 'lia@x.com'])
        assert rejected.status() == 422 && rejected.body().contains('O nome deve ter ao menos 2 caracteres')
        assert Usuario.get(id as Long).nome == 'Lia <3'

        def updated = form("/usuario/update/${id}", [_method: 'PUT', nome: 'Lia', email: 'lia@x.com', idade: '41', 'grupo.id': ''])
        assert updated.status() == 302
        assert page(location).body().contains('41 anos')
        assert Usuario.get(id as Long).grupo == null

        def deleted = form("/usuario/delete/${id}", [_method: 'DELETE'])
        assert deleted.headers().firstValue('Location').get() == '/usuario/index'
        assert page('/usuario/index').body().contains('Usuário removido')
        assert call('GET', "/api/usuarios/${id}").status() == 404
    }

    @Test
    void 'i18n por parametro lang guardado na sessao'() {
        assert page('/usuario/index?lang=en').body().contains('<h1>Users</h1>')
        assert page('/').body().contains('<h1>Welcome to Grox</h1>')
        assert page('/?lang=pt_BR').body().contains('<h1>Bem-vindo ao Grox</h1>')
    }

    @Test
    void 'dynamic finder em action e lazy loading na view'() {
        def g = Grupo.withTransaction { new Grupo(nome: 'Lazy').save(failOnError: true) }
        call('POST', '/api/usuarios', [nome: 'Velho', email: 'velho@x.com', idade: 149, grupo: [id: g.id]])
        def maiores = parse(call('GET', '/usuario/maiores.json'))
        assert maiores[0].nome == 'Velho'
        assert maiores.every { it.idade >= 18 }
        assert page('/usuario/index?sort=idade&order=desc').body() =~ /Velho<\/a><\/td>\s*<td>velho@x.com<\/td>\s*<td>149 anos<\/td>\s*<td>Lazy<\/td>/
    }

    @Test
    void 'scaffold dinamico: CRUD por formulario com as views geradas e JSON'() {
        def c = new TestClient(base).loggedIn('admin', 'admin')
        def lista = c.page('/grupo/index')
        assert lista.status() == 200 && lista.body().contains('scaffold-list') && lista.body().contains('<table>')
        def novo = c.page('/grupo/create')
        assert novo.status() == 200 && novo.body() =~ /<input type="text" name="nome" id="nome" required="required"/
        assert !novo.body().contains('name="usuarios"')                     // hasMany fica fora do formulário
        // componente (embedded): um fieldset com um campo por propriedade
        assert novo.body() =~ /<fieldset class="embedded sede"><legend>Sede<\/legend>.*name="sede.cidade".*name="sede.uf" id="sede.uf" maxlength="2"/

        def invalido = c.form('/grupo/save', [nome: ''])
        assert invalido.status() == 422 && invalido.body().contains('class="errors"') && invalido.body().contains('fieldcontain required error')

        def salvo = c.form('/grupo/save', [nome: 'Scaffold', 'sede.cidade': 'Curitiba', 'sede.uf': 'PR'])
        assert salvo.status() == 302
        String show = salvo.headers().firstValue('Location').get()
        long id = (show =~ /\/grupo\/show\/(\d+)/)[0][1] as long
        def pagina = c.page(show)
        assert pagina.body().contains('scaffold-show') && pagina.body().contains('Scaffold') && pagina.body().contains('criado')
        assert pagina.body().contains('Curitiba, PR')

        def edit = c.page("/grupo/edit/${id}")
        assert edit.body().contains('value="Scaffold"') && edit.body().contains('name="_method" value="PUT"')
        assert c.form("/grupo/update/${id}", [_method: 'PUT', nome: 'Scaffold 2']).status() == 302
        def json = c.send('GET', "/grupo/show/${id}", null, [Accept: 'application/json'])
        assert json.status() == 200 && TestClient.parse(json).nome == 'Scaffold 2'
        assert TestClient.parse(json).sede == [cidade: 'Curitiba', uf: 'PR']
        assert c.page('/grupo/index?max=100').body().contains("<a href=\"/grupo/show/${id}\">Scaffold 2</a>")

        def removido = c.form("/grupo/delete/${id}", [_method: 'DELETE'])
        assert removido.status() == 302 && removido.headers().firstValue('Location').get().endsWith('/grupo/index')
        assert c.page("/grupo/show/${id}").status() == 404
    }
}
