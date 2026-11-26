# Testes

Os testes de uma app do `grox-dsl` sobem a aplicação de verdade, numa porta aleatória, e fazem requisições HTTP com o `groovy.http.HttpBuilder` (módulo `groovy-http-builder` do Groovy 6, sobre o `HttpClient` do JDK). Este capítulo mostra a configuração do Gradle, o ambiente `test`, um teste completo no estilo do `SamplePlainTest` e receitas para login, CSRF, Basic e JSON.

## Gradle

```groovy
dependencies {
    testImplementation libs.groovy.test.junit6
    testImplementation libs.groovy.http.builder     // org.apache.groovy:groovy-http-builder
    testImplementation libs.junit.jupiter
    testRuntimeOnly libs.junit.launcher
}

tasks.named('test') {
    useJUnitPlatform()
    // cada classe de teste sobe a própria aplicação: o GORM é global na JVM
    forkEvery = 1
}
```

`forkEvery = 1` põe cada classe de teste na sua própria JVM. O GORM, o tenancy e o datastore são estáticos: duas aplicações na mesma JVM se atrapalham.

## Ambiente `test`

```yaml
# src/main/resources/application.yml
environments:
  test:
    server:
      port: 0                                  # porta aleatória
    dataSource:
      url: jdbc:h2:mem:livrariaTest;DB_CLOSE_DELAY=-1
```

No teste, defina o ambiente antes de subir: `System.setProperty('grails.env', 'test')` (ou `grox.env`). A porta escolhida fica em `app.port`.

## Um teste completo

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

    /** Cliente com cookies (guarda a sessão), sem seguir redirecionamentos. */
    static HttpBuilder client() {
        def cookies = new CookieManager()
        HttpBuilder.http {
            followRedirects false
            clientConfig { it.cookieHandler(cookies) }
        }
    }

    /** O corpo vai como texto, com o Content-Type dos cabeçalhos; a URL é base + caminho. */
    static HttpResult send(HttpBuilder c, String method, String path, String body = null, Map<String, String> headers = [:]) {
        c.request(method, base + path) {
            headers.each { k, v -> header k, v }
            if (body != null) text(body)
        }
    }

    @Test
    void 'API JSON: 401 sem login, 201, 422 e 404'() {
        def c = client()
        def json = ['Content-Type': 'application/json', Accept: 'application/json']
        String livro = JsonOutput.toJson([titulo: 'Dom Casmurro', autor: 'Machado', ano: 1899])

        assert send(c, 'POST', '/api/livros', livro, json).status() == 401

        def criado = send(c, 'POST', '/api/livros', livro, json + [Authorization: basicAdmin])
        assert criado.status() == 201
        def id = new JsonSlurper().parseText(criado.body()).id

        def invalido = send(c, 'POST', '/api/livros', JsonOutput.toJson([titulo: '']), json + [Authorization: basicAdmin])
        assert invalido.status() == 422
        assert new JsonSlurper().parseText(invalido.body()).errors*.field.contains('titulo')

        assert send(c, 'GET', "/api/livros/${id}", null, [Accept: 'application/json']).status() == 200
        assert send(c, 'GET', '/api/livros/999999', null, [Accept: 'application/json']).status() == 404
    }

    @Test
    void 'paginas: login por formulario com CSRF e flash'() {
        def c = client()
        def form = ['Content-Type': 'application/x-www-form-urlencoded']

        // POST sem token CSRF é recusado
        assert send(c, 'POST', '/livros', 'titulo=X', form).status() == 403

        // login: o token vem da página de login
        String login = send(c, 'GET', '/login/auth').body()
        String csrf = (login =~ /name="_csrf" value="([^"]+)"/)[0][1]
        assert send(c, 'POST', '/login/authenticate', "username=admin&password=admin&_csrf=${csrf}", form).status() == 302

        // o <g:form> da página traz o token da sessão logada
        String token = (send(c, 'GET', '/livros').body() =~ /name="_csrf" value="([^"]+)"/)[0][1]
        def salvo = send(c, 'POST', '/livros', "titulo=Iracema&autor=Alencar&ano=1865&_csrf=${token}", form)
        assert salvo.status() == 302
        def show = send(c, 'GET', salvo.headers().firstValue('Location').get())
        assert show.body().contains('<h1>Iracema</h1>') && show.body().contains('Livro Iracema criado')
    }

    @Test
    void 'erros do pipeline e actuator'() {
        def c = client()
        assert send(c, 'GET', '/nao/existe', null, [Authorization: basicAdmin]).status() == 404
        assert send(c, 'DELETE', '/api/livros', null, [Authorization: basicAdmin]).status() == 405
        def health = new JsonSlurper().parseText(send(c, 'GET', '/actuator/health').body())
        assert health.checks.db.status == 'UP'
    }
}
```

## Receitas

| Preciso | Como |
|---|---|
| Manter a sessão entre requisições | `clientConfig { it.cookieHandler(new CookieManager()) }` no `HttpBuilder.http { }` |
| Conferir redirecionamentos (302, `Location`) | `followRedirects false` no `HttpBuilder.http { }` |
| URL com `//` no início do caminho | Monte a URL absoluta (`base + path`): com `baseUri`, `//outro.host/x` iria para outro host |
| Corpo binário (PDF, imagens) | `bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.ISO_8859_1)` na requisição e `r.body().getBytes(StandardCharsets.ISO_8859_1)`: o ISO-8859-1 preserva os bytes |
| Login por formulário | Leia o `_csrf` de `/login/auth` e faça `POST /login/authenticate` com `username`, `password` e `_csrf` (302 no sucesso) |
| Formulário com sessão | Leia o `_csrf` do `<g:form>` da página (o token muda no login) |
| HTTP Basic | Cabeçalho `Authorization: Basic base64(usuario:senha)` (com `useBasicAuth: true`) |
| JWT | `POST /api/login` com `{"username", "password"}`, depois `Authorization: Bearer <access_token>` |
| JSON | `Content-Type: application/json` e `Accept: application/json` (sem `Accept`, `respond` com `view:` devolve HTML) |
| Inspecionar a aplicação | `app.config.getProperty('x', String)`, `app.modules*.name`, `app.module(Tipo)` |
| Dados direto no banco | Use as domain classes no teste: `Livro.withTransaction { new Livro(...).save() }` (o GORM é global na JVM) |
| Testar o desligamento | Socket cru: o `HttpClient` do JDK (por baixo do `HttpBuilder`) reenvia o GET numa conexão nova quando o servidor fecha |

O resultado (`HttpResult`) tem `status()`, `body()`, `headers()` (com `firstValue('Location')`) e `json` (parse do corpo, sem olhar o `Content-Type`).

`GroxApp.run` devolve o `GroxRuntime` já pronto. `app.close()` faz o desligamento gracioso (espera as requisições em andamento).

## Veja também

- [Projeto do zero](projeto-do-zero.md)
- [Tutorial: uma livraria completa](tutorial.md)
- [Ambientes](../referencia/ambientes.md)
- [Solução de problemas](../recomendacoes/problemas.md)
