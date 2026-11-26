package sample.plain

import groovy.http.HttpBuilder
import groovy.http.HttpResult
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** A app sem Grails (core + gorm + gsp + security + grox-dsl), real, numa porta aleatória. */
class SamplePlainTest {

    static GroxRuntime app
    static String base
    static String basicAdmin = 'Basic ' + 'admin:admin'.bytes.encodeBase64()

    @BeforeAll
    static void start() {
        System.setProperty('grails.env', 'test')
        app = GroxApp.run(Aplicacao)
        base = "http://localhost:${app.port}"
    }

    @AfterAll
    static void stop() {
        app?.close()
    }

    /** Requisição com o corpo como texto (o Content-Type vai nos cabeçalhos); a URL é base + caminho. */
    static HttpResult send(HttpBuilder c, String method, String path, String body = null, Map<String, String> headers = [:]) {
        c.request(method, base + path) {
            headers.each { k, v -> header k, v }
            if (body != null) text(body)
        }
    }

    /** Cliente com cookies (a sessão), sem seguir redirecionamentos. */
    static HttpBuilder client() {
        def cookies = new CookieManager()
        HttpBuilder.http {
            followRedirects false
            clientConfig { it.cookieHandler(cookies) }
        }
    }

    @Test
    void 'sobe sem o modulo Grails no classpath'() {
        assert !app.modules*.name.contains('grails')
        assert app.modules*.name.containsAll(['http', 'gorm', 'security', 'gsp', 'dsl'])
        def missing = { String c -> try { Class.forName(c); false } catch (ClassNotFoundException ignored) { true } }
        assert missing('org.grox.web.GrailsModule') && missing('grails.converters.JSON')
        // application.groovy mesclado ao yml (com o bloco environments do ambiente test)
        assert app.config.getProperty('grox.origemDaConfiguracao', String) == 'application.groovy'
        assert app.config.getProperty('grox.i18n.defaultLocale', String) == 'pt-BR'
    }

    @Test
    void 'rota de texto com access permitAll antes das regras de URL'() {
        def ola = send(client(), 'GET', '/ola/mundo')
        assert ola.status() == 200 && ola.body() == 'Olá, mundo!'
        assert ola.headers().firstValue('Content-Type').get().startsWith('text/plain')
    }

    @Test
    void 'API por anotacao: respond, validacao, 201 e Basic'() {
        def c = client()
        def json = ['Content-Type': 'application/json', Accept: 'application/json']
        assert send(c, 'POST', '/api/livros', JsonOutput.toJson([titulo: 'Dom Casmurro', autor: 'Machado', ano: 1899]), json).status() == 401
        def criado = send(c, 'POST', '/api/livros', JsonOutput.toJson([titulo: 'Dom Casmurro', autor: 'Machado', ano: 1899]), json + [Authorization: basicAdmin])
        assert criado.status() == 201
        def livro = new JsonSlurper().parseText(criado.body())
        assert livro.titulo == 'Dom Casmurro' && livro.id
        def invalido = send(c, 'POST', '/api/livros', JsonOutput.toJson([titulo: '', autor: 'X']), json + [Authorization: basicAdmin])
        assert invalido.status() == 422 && new JsonSlurper().parseText(invalido.body()).errors*.field.contains('titulo')
        def show = send(c, 'GET', "/api/livros/${livro.id}", null, [Accept: 'application/json'])
        assert show.status() == 200 && new JsonSlurper().parseText(show.body()).autor == 'Machado'
        assert send(c, 'GET', '/api/livros/999999', null, [Accept: 'application/json']).status() == 404
        assert new JsonSlurper().parseText(send(c, 'GET', '/api/livros', null, [Accept: 'application/json']).body())*.titulo.contains('Dom Casmurro')
    }

    @Test
    void 'paginas por closure: GSP com layout, login da app, CSRF, flash e modelo devolvido'() {
        def c = client()
        def lista = send(c, 'GET', '/livros')
        assert lista.status() == 200 && lista.body().contains('<h1>Livros</h1>') && lista.body().contains('<title>Livros</title>')
        // POST sem token CSRF é recusado; a página de login é a views/login/auth.gsp da app
        assert send(c, 'POST', '/livros', 'titulo=X', ['Content-Type': 'application/x-www-form-urlencoded']).status() == 403
        def login = send(c, 'GET', '/login/auth')
        assert login.body().contains('Entrar no sample-plain')
        String csrf = (login.body() =~ /name="_csrf" value="([^"]+)"/)[0][1]
        assert send(c, 'POST', '/login/authenticate', "username=admin&password=admin&_csrf=${csrf}", ['Content-Type': 'application/x-www-form-urlencoded']).status() == 302
        // o g:form da página traz o token CSRF
        String form = send(c, 'GET', '/livros').body()
        String token = (form =~ /name="_csrf" value="([^"]+)"/)[0][1]
        def salvo = send(c, 'POST', '/livros', "titulo=Iracema&autor=Alencar&ano=1865&_csrf=${token}", ['Content-Type': 'application/x-www-form-urlencoded'])
        assert salvo.status() == 302
        String location = salvo.headers().firstValue('Location').get()
        def show = send(c, 'GET', location)
        assert show.status() == 200 && show.body().contains('<h1>Iracema</h1>') && show.body().contains('Livro Iracema criado')
        assert show.body().contains('rota: rotas/show')
        def invalido = send(c, 'POST', '/livros', "titulo=&autor=&_csrf=${token}", ['Content-Type': 'application/x-www-form-urlencoded'])
        assert invalido.status() == 422 && invalido.body().contains('Corrija os campos')
        assert send(c, 'GET', '/livros/999999').status() == 404
        // layout com o usuário logado (request.userPrincipal) e logout pelo g:form com CSRF
        assert show.body().contains('admin')
        assert send(c, 'POST', '/logoff', "_csrf=${token}", ['Content-Type': 'application/x-www-form-urlencoded']).status() == 302
        assert !send(c, 'GET', '/livros').body().contains('Sair')   // deslogado
    }

    @Test
    void 'view em Pug: expressoes em Groovy, mixin, include, filtro markdown, TagLibs e layout'() {
        def c = client()
        def r = send(c, 'GET', '/catalogo?busca=%3Cb%3Ex%3C%2Fb%3E')
        assert r.status() == 200
        String html = r.body()
        assert html.contains('<title>Catálogo</title>') && html.contains('<header>')            // layout SiteMesh (meta layout)
        assert html =~ /<h1>Catálogo \(\d+ livros\)<\/h1>/
        assert html =~ /<li class="livro"><a href="\/livros\/\d+">[^<]+<\/a>/                    // mixin + g:link
        assert html.contains('<span class="autor">')                                           // TagLib da aplicação
        assert html.contains('<strong class="destaque">Mais antigo: ')                         // tag com corpo
        assert html.contains('Busca: &lt;b&gt;x&lt;/b&gt;')                                     // = escapa
        assert html.contains('<p>/ajuda</p>')                                                    // != com g.createLink
        assert html.contains('<footer><p>Feito com <strong>Pug</strong> e <a rel="nofollow" href="/sobre">Grox</a>.</p>')   // include + :markdown (links com nofollow)
        assert html =~ /<form action="\/livros" method="post"[^>]*>.*name="_csrf"/              // g:form com o token CSRF
    }

    @Test
    void 'view em Markdown: GSP antes do commonmark, front matter, tabela e links sanitizados'() {
        def r = send(client(), 'GET', '/ajuda?busca=%3Ci%3Ey%3C%2Fi%3E')
        assert r.status() == 200
        String html = r.body()
        assert html.contains('<title>Ajuda</title>') && html.contains('<header>')                // front matter: title e layout
        assert html =~ /<h1 id="ajuda">Ajuda<\/h1>/
        assert html =~ /O catálogo tem <strong>\d+ livros<\/strong>/                           // ${} do modelo
        assert html.contains('<a href="/catalogo">lista em Pug</a>') && html.contains('<strong class="destaque">a lista em GSP</strong>')
        assert html.contains('<table>') && html.contains('<td>Markdown</td>')
        assert html.contains('Busca: &lt;i&gt;y&lt;/i&gt;')                                      // ${} escapado
        assert !html.contains('javascript:')                                                    // sanitizeUrls
    }

    @Test
    void 'DSL de projeto: rotas com access, bean, usuarios e config padrao'() {
        def c = client()
        def info = send(c, 'GET', '/app/info')
        assert info.status() == 200
        def json = new JsonSlurper().parseText(info.body())
        assert json.relogio == 'Z' && json.poolSize == 3 && json.livros >= 0   // bean relogio, livroService e config da DSL
        assert app.config.getProperty('grox.i18n.defaultLocale', String) == 'pt-BR'   // o yml vence o padrão da DSL
        String leitor = 'Basic ' + 'leitor:leitor'.bytes.encodeBase64()
        assert send(c, 'GET', '/app/admin', null, [Authorization: leitor]).status() == 403   // access: ROLE_ADMIN
        assert send(c, 'GET', '/app/admin', null, [Authorization: basicAdmin]).body() == 'só admin'
    }

    @Test
    void 'DSL de HTML na rota, com layout e link reverso'() {
        def r = send(client(), 'GET', '/sobre')
        assert r.status() == 200
        String body = r.body()
        assert body.contains('<title>Livros</title>') && body.contains('<a href="/livros">Livros</a>')   // layout main
        assert body.contains('<h1>Sobre</h1>') && body =~ /<p>\d+ livros &amp; contando<\/p>/
        assert body.contains('<a href="/livros">Ver livros</a>')   // createLink(controller: 'rotas', action: 'index')
    }

    @Test
    void 'grupo em atores, 404 e 405 do pipeline e actuator'() {
        def c = client()
        def rel = send(c, 'GET', '/relatorios/autores', null, [Authorization: basicAdmin])
        assert rel.status() == 200 && new JsonSlurper().parseText(rel.body()).execucao == 'actor'
        assert send(c, 'GET', '/nao/existe', null, [Authorization: basicAdmin]).status() == 404
        assert send(c, 'DELETE', '/api/livros', null, [Authorization: basicAdmin]).status() == 405
        def health = send(c, 'GET', '/actuator/health')
        assert health.status() == 200 && new JsonSlurper().parseText(health.body()).checks.db.status == 'UP'
    }

    @Test
    void 'BootStrap da DSL de projeto e da classe, nessa ordem, com environments'() {
        def livros = new JsonSlurper().parseText(send(client(), 'GET', '/api/livros', null, [Accept: 'application/json']).body())
        def guarani = livros.find { it.titulo == 'O Guarani' }
        def senhora = livros.find { it.titulo == 'Senhora' }
        assert guarani && senhora && guarani.id < senhora.id
    }

    @Test
    void 'interceptores: global com padrao, do grupo, error da DSL e OnError do handler'() {
        def c = client()
        def bloqueado = send(c, 'GET', '/relatorios/autores?bloquear=1', null, [Authorization: basicAdmin])
        assert bloqueado.status() == 423 && bloqueado.body() == 'relatórios bloqueados'
        assert !bloqueado.headers().firstValue('X-Relatorio').present   // o before interrompeu: sem rota nem after
        def rel = send(c, 'GET', '/relatorios/autores', null, [Authorization: basicAdmin])
        assert rel.status() == 200 && rel.headers().firstValue('X-Relatorio').get() == 'autores'
        assert !send(c, 'GET', '/ola/x').headers().firstValue('X-Relatorio').present   // o after é só do grupo
        def falha = send(c, 'GET', '/app/falha')
        assert falha.status() == 503 && new JsonSlurper().parseText(falha.body()).erro == 'falhou de propósito'
        def invalido = send(c, 'POST', '/api/livros', '{nao é json', ['Content-Type': 'application/json', Accept: 'application/json', Authorization: basicAdmin])
        assert invalido.status() == 400 && new JsonSlurper().parseText(invalido.body()).erro
    }

    @Test
    void 'ns com rota sem caminho e caminho relativo ao grupo'() {
        assert send(client(), 'GET', '/notas').body() == 'notas'
        assert send(client(), 'GET', '/notas/7').body() == 'nota 7'
    }
}
