# Tutorial: uma livraria completa

Este tutorial monta, passo a passo, uma aplicação completa sem `grails-app/`: páginas HTML com GSP e layout, cadastro com validação, uma API JSON, login com usuários e regras de acesso, um relatório executado em atores, uma página feita com a DSL de HTML e os testes de integração. O código final tem a mesma forma do `sample-plain/` do repositório, que você pode rodar para comparar.

Todos os arquivos ficam no projeto `livraria/`, pacote `livraria`.

## 1. Build e entrada

`settings.gradle` (na raiz do repositório do Grox, para usar os módulos como subprojetos):

```groovy
include('livraria')
```

`livraria/build.gradle`:

```groovy
plugins {
    id 'groovy'
    id 'application'
}

repositories { mavenCentral() }

dependencies {
    implementation project(':framework:grox-dsl')     // core, gorm, gsp e security vêm junto
    runtimeOnly libs.slf4j.simple
    runtimeOnly libs.h2

    testImplementation libs.groovy.test.junit6
    testImplementation libs.groovy.http.builder
    testImplementation libs.junit.jupiter
    testRuntimeOnly libs.junit.launcher
}

java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }

tasks.withType(GroovyCompile).configureEach { groovyOptions.parameters = true }

application { mainClass = 'livraria.Aplicacao' }

tasks.named('run') { systemProperty 'grox.env.default', 'development' }

tasks.named('test') {
    useJUnitPlatform()
    forkEvery = 1
}
```

`src/main/resources/application.yml`:

```yaml
server:
  port: 8080

grox:
  i18n:
    defaultLocale: pt-BR
  routes:
    execution: virtual

dataSource:
  driverClassName: org.h2.Driver
  username: sa
  password: ''
  dbCreate: create-drop

---
environments:
  development:
    grox:
      gsp:
        viewsDir: src/main/resources/views
    dataSource:
      url: jdbc:h2:mem:livrariaDev;DB_CLOSE_DELAY=-1
  test:
    server:
      port: 0
    dataSource:
      url: jdbc:h2:mem:livrariaTest;DB_CLOSE_DELAY=-1
```

`src/main/groovy/livraria/Aplicacao.groovy` (por enquanto, só a entrada; a DSL de projeto entra no passo 6):

```groovy
package livraria

import org.grox.GroxApp

class Aplicacao {
    static void main(String[] args) {
        GroxApp.run(Aplicacao, args)
    }
}
```

## 2. A domain class

`src/main/groovy/livraria/Livro.groovy`:

```groovy
package livraria

import grails.gorm.annotation.Entity

@Entity
class Livro {
    String titulo
    String autor
    Integer ano

    static constraints = {
        titulo blank: false
        autor blank: false
        ano nullable: true, min: 1450
    }
}
```

`@Entity` dá a API do GORM, `id`, `version` e o construtor com `Map`. Como `dbCreate` é `create-drop`, a tabela `livro` é criada na subida.

## 3. O service

`src/main/groovy/livraria/LivroService.groovy`:

```groovy
package livraria

import grails.gorm.transactions.Transactional

@Transactional
class LivroService {

    List<Livro> todos() {
        Livro.list(sort: 'titulo')
    }

    Livro buscar(Long id) {
        id == null ? null : Livro.get(id)
    }

    /** O livro salvo ou, com erros de validação, sem id. */
    Livro salvar(Map dados) {
        def livro = new Livro(dados)
        livro.save()
        livro
    }

    void excluir(Livro livro) {
        livro.delete()
    }

    Map<String, Long> porAutor() {
        Livro.list().countBy { it.autor }.collectEntries { k, v -> [k, v as Long] }
    }
}
```

O service é um singleton, injetado pelo nome (`livroService`). Cada chamada de fora roda numa transação. `new Livro(dados)` converte os tipos (`ano` em texto vira `Integer`) e ignora chaves desconhecidas, como `_csrf`.

## 4. Páginas HTML

O layout, `src/main/resources/views/layouts/main.gsp`:

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8"/>
    <title><g:layoutTitle default="Livraria"/></title>
    <g:layoutHead/>
</head>
<body>
<header>
    <a href="/livros">Livros</a> · <a href="/sobre">Sobre</a>
    <g:if test="${request.userPrincipal}">
        · ${request.userPrincipal.name}
        <g:form uri="/logoff" method="POST" style="display:inline"><button>Sair</button></g:form>
    </g:if>
    <g:else><a href="/login/auth">Entrar</a></g:else>
</header>
<g:if test="${flash.message}"><p class="flash">${flash.message}</p></g:if>
<g:layoutBody/>
</body>
</html>
```

A lista com o formulário de cadastro, `views/livros/index.gsp`:

```html
<html>
<head><meta name="layout" content="main"/><title>Livros</title></head>
<body>
<h1>Livros</h1>
<ul>
<g:each in="${livros}" var="l">
    <li><g:link controller="rotas" action="show" id="${l.id}">${l.titulo}</g:link> (${l.autor})</li>
</g:each>
</ul>

<h2>Novo livro</h2>
<g:hasErrors bean="${novo}">
    <ul class="erros"><g:eachError bean="${novo}" var="e"><li><g:message error="${e}"/></li></g:eachError></ul>
</g:hasErrors>
<g:form uri="/livros" method="POST">
    <input name="titulo" placeholder="Título" value="${novo?.titulo}"/>
    <input name="autor" placeholder="Autor" value="${novo?.autor}"/>
    <input name="ano" placeholder="Ano" value="${novo?.ano}"/>
    <button>Salvar</button>
</g:form>
</body>
</html>
```

O detalhe, `views/livros/show.gsp`:

```html
<html>
<head><meta name="layout" content="main"/><title>${livro.titulo}</title></head>
<body>
<h1>${livro.titulo}</h1>
<p>${livro.autor}<g:if test="${livro.ano}">, ${livro.ano}</g:if></p>
</body>
</html>
```

As rotas, `src/main/groovy/livraria/Rotas.groovy`:

```groovy
package livraria

import org.grox.dsl.Routes

class Rotas implements Routes {

    LivroService livroService

    Closure routes = {
        get('/') { redirect '/livros' }

        get('/livros', name: 'index') {
            render view: 'livros/index', model: [livros: livroService.todos()]
        }

        // sem render: o Map devolvido vira o modelo da view: da rota
        get('/livros/{id}', name: 'show', view: 'livros/show') {
            Livro livro = livroService.buscar(params.long('id'))
            if (!livro) return render(status: 404)
            [livro: livro]
        }

        post('/livros', name: 'save') {
            Livro livro = livroService.salvar(params)
            if (livro.hasErrors()) {
                return render(view: 'livros/index', model: [livros: livroService.todos(), novo: livro], status: 422)
            }
            flash.message = "Livro ${livro.titulo} criado"
            redirect controller: 'rotas', action: 'show', id: livro.id
        }
    }
}
```

Rode `./gradlew :livraria:run` e abra `http://localhost:8080/livros`. Ainda não há segurança, então o cadastro funciona direto. Um título vazio volta para a lista com status 422 e a mensagem de validação em português (as mensagens padrão vêm do framework).

## 5. A API JSON

`src/main/groovy/livraria/LivrosApi.groovy`:

```groovy
package livraria

import org.grox.dsl.Delete
import org.grox.dsl.Get
import org.grox.dsl.Handler
import org.grox.dsl.Post

@Handler('/api/livros')
class LivrosApi {

    LivroService livroService

    @Get
    def index() {
        respond livroService.todos()
    }

    @Get('/{id}')
    def show(Long id) {
        respond livroService.buscar(id)                 // null: 404
    }

    @Post
    def save() {
        Livro livro = livroService.salvar(request.JSON as Map)
        if (livro.hasErrors()) return respond(livro.errors, status: 422)
        respond livro, status: 201
    }

    @Delete('/{id}')
    def delete(Long id) {
        Livro livro = livroService.buscar(id)
        if (!livro) return respond(null)
        livroService.excluir(livro)
        render status: 204
    }
}
```

```bash
curl -X POST localhost:8080/api/livros -H 'Content-Type: application/json' \
     -d '{"titulo": "Dom Casmurro", "autor": "Machado de Assis", "ano": 1899}'
# 201 {"id":1,"version":0,"titulo":"Dom Casmurro",...}

curl -X POST localhost:8080/api/livros -H 'Content-Type: application/json' -d '{"titulo": ""}'
# 422 {"errors":[{"object":"livraria.Livro","field":"titulo",...,"message":"..."}, ...]}

curl -i localhost:8080/api/livros/99 -H 'Accept: application/json'   # 404
```

## 6. Segurança

Agora a aplicação ganha usuários, login e regras. A classe de entrada passa a declarar a segurança na DSL de projeto:

```groovy
package livraria

import org.grox.GroxApp

class Aplicacao {

    static app = {
        security {
            user 'admin', password: '{noop}admin', roles: ['ROLE_ADMIN']
            user 'leitor', password: '{noop}leitor', roles: ['ROLE_USER']
            useBasicAuth = true                    // a API aceita Basic
            stateless = ['/api/**']                // sem sessão nem CSRF na API
        }
    }

    static void main(String[] args) {
        GroxApp.run(Aplicacao, args)
    }
}
```

As regras por caminho ficam em `src/main/resources/application.groovy`:

```groovy
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    [pattern: '/', access: ['permitAll']],
    [pattern: '/sobre', access: ['permitAll']],
    [pattern: '/livros', httpMethod: 'GET', access: ['permitAll']],
    [pattern: '/livros/**', httpMethod: 'GET', access: ['permitAll']],
    [pattern: '/api/livros/**', httpMethod: 'GET', access: ['permitAll']],
    [pattern: '/api/**', access: ['ROLE_ADMIN']],
    [pattern: '/relatorios/**', access: ['ROLE_ADMIN']],
    [pattern: '/**', access: ['isAuthenticated()']]
]
```

A primeira regra que casar vale. Assim, qualquer um lê a lista e os detalhes; cadastrar pelo site exige login (`/**`); a API só aceita GET anônimo, e o resto pede `ROLE_ADMIN`.

A página de login, `views/login/auth.gsp`:

```html
<html>
<head><meta name="layout" content="main"/><title>Entrar</title></head>
<body>
<h1>Entrar</h1>
<g:if test="${errorMessage}"><p class="erro">${errorMessage}</p></g:if>
<form action="${postUrl}" method="post">
    <input type="hidden" name="${csrfParameter}" value="${csrfToken}"/>
    <input name="${usernameParameter}" placeholder="Usuário"/>
    <input name="${passwordParameter}" type="password" placeholder="Senha"/>
    <button>Entrar</button>
</form>
</body>
</html>
```

O que muda no comportamento:
- Cadastrar pelo site exige login: sem ele, o navegador vai para `/login/auth` e, depois do login, volta para a URL pedida.
- O CSRF fica ativo: o `<g:form>` da lista já inclui o `_csrf`, e o botão "Sair" do layout faz o `POST /logoff` com o token.
- A API exige `Authorization: Basic ...` para POST e DELETE:

```bash
curl -X POST localhost:8080/api/livros -u admin:admin -H 'Content-Type: application/json' \
     -d '{"titulo": "Iracema", "autor": "José de Alencar", "ano": 1865}'     # 201
curl -X DELETE localhost:8080/api/livros/1 -u leitor:leitor                  # 403
```

## 7. Um relatório em atores

Relatórios pesados não devem ocupar todas as threads. Um grupo com `execution: 'actor'` limita a concorrência a dois atores. Acrescente ao closure de `Rotas`:

```groovy
group('/relatorios', execution: 'actor', poolSize: 2) {
    get('/autores', name: 'autores') {
        render json: [execucao: route.execution, autores: livroService.porAutor()]
    }
}
```

```bash
curl -u admin:admin localhost:8080/relatorios/autores
# {"execucao":"actor","autores":{"Machado de Assis":1,"José de Alencar":1}}
```

No máximo duas requisições do relatório andam ao mesmo tempo; as outras esperam na fila dos atores. As demais rotas continuam em virtual threads.

## 8. Uma página com a DSL de HTML

A página "Sobre" não precisa de GSP. Acrescente a `Rotas`:

```groovy
get('/sobre', name: 'sobre') {
    render(layout: 'main') {
        h1 'Sobre'
        p "${livroService.todos().size()} livros & contando"
        a(href: g.createLink(controller: 'rotas', action: 'index'), 'Ver livros')
    }
}
```

O texto é escapado (`&` vira `&amp;`), o link vem da rota `index` e a página sai decorada pelo layout `main`.

## 9. Configuração, beans e dados iniciais

A DSL de projeto também guarda padrões de configuração e beans. Complete o `app` e o `main`:

```groovy
static app = {
    config {
        grox.routes.poolSize = 4                   // padrão: o yml, o ambiente e o -D vencem
    }
    security { /* como no passo 6 */ }
    routes {
        get('/app/info', access: 'permitAll') {
            render json: [relogio: relogio.zone.id, livros: livroService.todos().size(),
                          poolSize: config.getProperty('grox.routes.poolSize', Integer)]
        }
    }
    beans {
        relogio = java.time.Clock.systemUTC()
    }
    bootstrap {
        init {
            environments {
                development {
                    livroService.salvar(titulo: 'Dom Casmurro', autor: 'Machado de Assis', ano: 1899)
                    livroService.salvar(titulo: 'Iracema', autor: 'José de Alencar', ano: 1865)
                }
            }
        }
    }
}

static void main(String[] args) {
    GroxApp.run(Aplicacao, args)
}
```

A rota `/app/info` usa o bean `relogio` e o service pelo nome, e o `access: 'permitAll'` da rota vale antes da regra `/**`. O `bootstrap { init { } }` roda na subida, antes do servidor, e o `environments { }` limita os livros de exemplo ao ambiente de desenvolvimento (o `run` do Gradle).

## 10. Testes

`src/test/groovy/livraria/LivrariaTest.groovy`:

```groovy
package livraria

import groovy.http.HttpBuilder
import groovy.http.HttpResult
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class LivrariaTest {

    static GroxRuntime app
    static String base
    static String admin = 'Basic ' + 'admin:admin'.bytes.encodeBase64()

    @BeforeAll
    static void start() {
        System.setProperty('grails.env', 'test')
        app = GroxApp.run(Aplicacao)
        base = "http://localhost:${app.port}"
    }

    @AfterAll
    static void stop() { app?.close() }

    static HttpBuilder client() {
        def cookies = new CookieManager()
        HttpBuilder.http { followRedirects false; clientConfig { it.cookieHandler(cookies) } }
    }

    static HttpResult send(HttpBuilder c, String method, String path, String body = null, Map<String, String> h = [:]) {
        c.request(method, base + path) {
            h.each { k, v -> header k, v }
            if (body != null) text(body)
        }
    }

    @Test
    void 'API: Basic, 201, 422, 404 e 403'() {
        def c = client()
        def json = ['Content-Type': 'application/json', Accept: 'application/json']
        String livro = JsonOutput.toJson([titulo: 'Dom Casmurro', autor: 'Machado', ano: 1899])
        assert send(c, 'POST', '/api/livros', livro, json).status() == 401
        def criado = send(c, 'POST', '/api/livros', livro, json + [Authorization: admin])
        assert criado.status() == 201
        def id = new JsonSlurper().parseText(criado.body()).id
        assert send(c, 'POST', '/api/livros', JsonOutput.toJson([titulo: '']), json + [Authorization: admin]).status() == 422
        assert send(c, 'GET', '/api/livros/999999', null, [Accept: 'application/json']).status() == 404
        String leitor = 'Basic ' + 'leitor:leitor'.bytes.encodeBase64()
        assert send(c, 'DELETE', "/api/livros/${id}", null, [Authorization: leitor]).status() == 403
        assert send(c, 'DELETE', "/api/livros/${id}", null, [Authorization: admin]).status() == 204
    }

    @Test
    void 'site: login, CSRF, cadastro, flash e 422'() {
        def c = client()
        def form = ['Content-Type': 'application/x-www-form-urlencoded']
        String csrf = (send(c, 'GET', '/login/auth').body() =~ /name="_csrf" value="([^"]+)"/)[0][1]
        assert send(c, 'POST', '/login/authenticate', "username=admin&password=admin&_csrf=${csrf}", form).status() == 302
        String token = (send(c, 'GET', '/livros').body() =~ /name="_csrf" value="([^"]+)"/)[0][1]
        def salvo = send(c, 'POST', '/livros', "titulo=Iracema&autor=Alencar&ano=1865&_csrf=${token}", form)
        assert salvo.status() == 302
        def show = send(c, 'GET', salvo.headers().firstValue('Location').get())
        assert show.body().contains('<h1>Iracema</h1>') && show.body().contains('Livro Iracema criado')
        assert send(c, 'POST', '/livros', "titulo=&autor=&_csrf=${token}", form).status() == 422
    }

    @Test
    void 'relatorio em atores e pagina com DSL de HTML'() {
        def c = client()
        def rel = send(c, 'GET', '/relatorios/autores', null, [Authorization: admin])
        assert new JsonSlurper().parseText(rel.body()).execucao == 'actor'
        def sobre = send(c, 'GET', '/sobre').body()
        assert sobre.contains('<h1>Sobre</h1>') && sobre.contains('&amp; contando')
    }
}
```

```bash
./gradlew :livraria:test
```

## O que você tem agora

| Recurso | Onde |
|---|---|
| Domain class e service transacional | `Livro`, `LivroService` |
| Páginas GSP com layout, validação (422) e flash | `Rotas`, `views/` |
| API JSON com 201, 404, 422 e 204 | `LivrosApi` |
| Login por formulário, Basic, CSRF e regras | `Aplicacao.app.security`, `application.groovy`, `views/login/auth.gsp` |
| Concorrência limitada por atores | grupo `/relatorios` |
| HTML sem GSP | rota `/sobre` |
| Configuração padrão e beans no código | `Aplicacao.app.config`, `beans` |
| Testes de integração | `LivrariaTest` |

Próximos passos: usuários no banco com o `grox-security-gorm`, JWT para a API (`rest.active`), sessão no Redis (`grox.session.store: redis`) e a [implantação em produção](../recomendacoes/implantacao.md).

## Veja também

- [Projeto do zero](projeto-do-zero.md)
- [Rotas por closure](rotas-closure.md)
- [Rotas por anotação](rotas-anotacao.md)
- [Segurança sem o plugin](seguranca.md)
- [Testes](testes.md)
