# Interceptores

Sem o módulo Grails não há classes `*Interceptor`. Nas apps do `grox-dsl`, o código que roda antes, depois ou no erro das rotas é declarado na própria DSL de rotas (`before`, `after`, `error`) ou, nas classes `@Handler`, em métodos anotados (`@Before`, `@After`, `@OnError`). *(extensão Grox)*

## Na DSL de rotas

```groovy
class Rotas implements Routes {
    Closure routes = {
        group('/relatorios', execution: 'actor', poolSize: 2) {
            before { if (!session.usuario) redirect '/login' }          // interrompe a rota
            after { response.setHeader('X-Relatorio', actionName) }
            get('/autores', name: 'autores') { render json: livroService.porAutor() }
        }
        error { Throwable e -> render status: 500, text: "falhou: ${e.message}" }
    }
}
```

| Interceptor | Quando roda | Efeito |
|---|---|---|
| `before { }` | Antes do handler | Devolver `false` ou responder (`render`, `redirect`) interrompe a rota: o handler e os `after` não rodam |
| `after { valor -> }` | Depois do handler, se não houve exceção | Recebe o valor devolvido pelo handler (o modelo de uma rota com `view:`, antes de a view ser renderizada) |
| `error { Throwable e -> }` | Exceção no handler ou noutro interceptor | A resposta parcial é descartada. Se o interceptor responder, a resposta é a dele; senão a exceção segue para a página de erro do pipeline (500) |

Os closures têm o mesmo delegate das rotas: `params`, `request`, `response`, `session`, `flash`, `actionName`, `route`, os services pelo nome e `render`/`redirect`. Um `before` com um parâmetro recebe o `RouteContext`.

## Alcance

O interceptor vale para as rotas do escopo onde foi declarado, qualquer que seja a posição dele no bloco:

| Onde | Vale para |
|---|---|
| Num `group`/`ns` | As rotas do grupo e dos grupos de dentro |
| Na raiz de uma classe `Routes` | As rotas da classe |
| Na raiz do `routes { }` da [DSL de projeto](dsl-projeto.md) | **Todas as rotas da aplicação**: as da DSL, as das classes `Routes` e as das classes `@Handler` |

Com um padrão Ant de caminho, só as rotas cujo caminho casa:

```groovy
static app = {
    routes {
        before('/admin/**') { if (!request.userPrincipal) render status: 401, text: 'entre primeiro' }
        error('/api/**') { Throwable e -> render status: 503, json: [erro: e.message] }
    }
}
```

A ordem é a do escopo: os `before` rodam do mais externo (a app, a classe, o grupo) para o mais interno; os `after` e os `error`, do mais interno para o mais externo. Dentro do mesmo escopo, na ordem de declaração.

## Nas classes `@Handler`

Métodos anotados com `@Before`, `@After` ou `@OnError` valem para as rotas da classe. O `value` da anotação é o padrão de caminho opcional. Os parâmetros vêm pelo tipo: `RouteContext`, `Throwable` (no `@OnError`) ou o valor devolvido pela rota (no `@After`).

```groovy
@Handler('/api/livros')
class LivrosApi {
    LivroService livroService

    @Post def save() { respond livroService.salvar(request.json as Map), status: 201 }

    @Before('/api/livros/admin/**')
    def soAdmin() { if (!request.userPrincipal) { render status: 401, text: ''; return false } }

    @OnError
    def falhou(Throwable e) { render status: 400, json: [erro: e.message] }   // ex.: JSON inválido no corpo
}
```

Os interceptores globais da DSL de projeto também valem para essas rotas, antes dos da classe.

## Execução e custo

Os interceptores rodam na mesma thread da rota: na virtual thread ou dentro do ator da rota (`execution: 'actor'`), com a requisição, a autenticação e a sessão de dados já vinculadas. Eles rodam depois da segurança: uma rota negada pelas regras de acesso não chega ao `before`. Rotas sem interceptores não pagam nada; um padrão de caminho é uma expressão regular compilada na subida.

## Diferenças para os interceptors do Grails

- Não há `match`/`matchAll`/`excludes` por controller e action: o alcance é o escopo da declaração mais o padrão de caminho.
- Não há `afterView` nem `order`; a ordem é a dos escopos.
- O `error` é próprio do Grox: no Grails, a exceção vai para o mapeamento `"500"` do `UrlMappings`.

## Veja também

- [Rotas por closure](rotas-closure.md)
- [Rotas por anotação](rotas-anotacao.md)
- [DSL de projeto](dsl-projeto.md)
- [Interceptors (Grails)](../recursos/interceptors.md)
