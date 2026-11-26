# Rotas por anotação

Uma classe anotada com `@Handler` agrupa rotas em métodos, como um controller: o caminho base fica na classe, e cada método recebe `@Get`, `@Post`, `@Put`, `@Patch` ou `@Delete`. Este capítulo cobre as anotações, os parâmetros dos métodos, a API que a classe ganha e a execução.

## Exemplo

```groovy
package livraria

import org.grox.dsl.Get
import org.grox.dsl.Handler
import org.grox.dsl.Post

@Handler('/api/livros')
class LivrosApi {

    LivroService livroService                     // injetado pelo nome

    @Get
    def index() {
        respond livroService.todos()
    }

    @Get('/{id}')
    def show(Long id) {
        respond livroService.buscar(id)           // null: 404
    }

    @Post
    def save() {
        Livro livro = livroService.salvar(request.JSON as Map)
        if (livro.hasErrors()) return respond(livro.errors, status: 422)
        respond livro, status: 201
    }
}
```

| Requisição | Método |
|---|---|
| `GET /api/livros` | `index()` |
| `GET /api/livros/7` | `show(7)` |
| `POST /api/livros` | `save()` |
| `DELETE /api/livros` | `405` (o caminho existe, o método não) |

## As anotações

Todas ficam no pacote `org.grox.dsl`.

| Anotação | Atributos | Descrição |
|---|---|---|
| `@Handler` | `value` (caminho base, padrão `''`), `execution` (`virtual`/`actor`; vazio: `grox.routes.execution`), `poolSize` (0: `grox.routes.poolSize`), `access` (regras de acesso de todas as rotas da classe) | Na classe |
| `@Get`, `@Post`, `@Put`, `@Patch`, `@Delete` | `value` (caminho relativo ao do handler, padrão `''`), `access` (regras da rota; vencem as do `@Handler`) | Nos métodos públicos de instância |

- O caminho do método é concatenado ao da classe: `@Handler('/api/livros')` com `@Get('/{id}')` dá `/api/livros/{id}`.
- `{nome}` vira um parâmetro, e a rota mais específica casa primeiro, como nas [rotas por closure](rotas-closure.md).
- O nome da rota (o "action" nos links e nas métricas) é o nome do método. O grupo é o nome da classe em camelCase sem o sufixo `Handler` (`LivrosApi` → `livrosApi`, `AutoresHandler` → `autores`).
- `access` aceita a mesma sintaxe das regras de URL (`'ROLE_ADMIN'`, `'permitAll'`, `"hasRole('ADMIN') and isFullyAuthenticated()"`) e vale como um `@Secured`, antes das regras de URL.

```groovy
@Handler(value = '/admin/usuarios', access = ['ROLE_ADMIN'])
class UsuariosAdmin {
    UsuarioService usuarioService
    @Get def index() { respond usuarioService.todos() }
    @Get(value = '/{id}', access = ['ROLE_ADMIN', 'ROLE_SUPORTE']) def show(Long id) { respond usuarioService.buscar(id) }
}
```

## A API da classe

A anotação faz a classe implementar o trait `org.grox.dsl.HandlerApi`. Os membros valem para a requisição em atendimento na thread:

| Membro | Descrição |
|---|---|
| `params` | `GroxParams` com query, formulário, arquivos e variáveis do caminho |
| `request`, `response` | `GroxRequest` (`JSON`, `getHeader`, `getFile`, `userPrincipal`...) e `GroxResponse` |
| `session`, `flash` | Sessão HTTP e escopo flash |
| `locale` | Locale da requisição |
| `config` | Configuração da aplicação |
| `routeContext` | O `RouteContext` da requisição (o mesmo delegate das rotas por closure) |
| `render(...)`, `respond(...)`, `redirect(...)` | Respostas: ver [Respostas](respostas.md) |

O IntelliJ IDEA completa esses membros nas classes `@Handler` pelo descritor GDSL do framework ([Autocomplete no IntelliJ IDEA](../recursos/estrutura.md#autocomplete-no-intellij-idea)).

Diferente das rotas por closure, services e beans **não** são resolvidos por nome automaticamente: declare a propriedade (`LivroService livroService` ou `def livroService`) para recebê-la por injeção.

Chamar esses membros fora de uma requisição (num construtor, numa thread própria) lança `IllegalStateException: Nenhuma rota do grox-dsl em atendimento nesta thread`.

## Parâmetros dos métodos

Os argumentos são preenchidos pelo **nome** do parâmetro. Por isso o build precisa de `groovyOptions.parameters = true` (ver [Projeto do zero](projeto-do-zero.md)).

| Tipo do parâmetro | Valor |
|---|---|
| `RouteContext` | A requisição corrente |
| `Map` chamado `params` | O `params` da requisição |
| Tipo simples (números, `String`, `Boolean`, datas, enums, `UUID`) | `params[nome]` convertido; `null` se ausente ou inválido, `0`/`false` para primitivos |
| Outra classe (command object, domain class) | Uma instância nova preenchida por binding com `params` |

```groovy
@Get('/busca')
def busca(String q, Integer max, boolean disponiveis) {
    respond livroService.buscar(q, max ?: 10, disponiveis)
}

@Post('/form')
def salvarForm(Livro livro) {          // new Livro() + binding de params
    livro.save() ? redirect("/livros/${livro.id}") : render(view: 'livros/index', model: [novo: livro], status: 422)
}
```

Diferenças em relação às actions dos controllers do Grails:
- o objeto é preenchido só com `params` (formulário, query e caminho). Para JSON, leia `request.JSON` (ou `request.json`) e passe o `Map` ao service ou ao construtor;
- `validate()` não é chamado automaticamente; uma domain class não é carregada pelo `id`. Faça isso no método (`Livro.get(id)`), se precisar.

## Valor de retorno

O retorno do método é ignorado, a menos que nenhuma resposta tenha sido escrita. Nesse caso, a resposta sai vazia com o status corrente (200). Nas classes anotadas não há `view:` por rota: para HTML, use `render view:` ou `respond x, view: '...'`.

## Execução e instâncias

| `execution` | Instâncias da classe |
|---|---|
| `virtual` (padrão) | Uma instância compartilhada por todas as requisições, em virtual threads. Não guarde estado de requisição em campos |
| `actor` | Um pool de `poolSize` atores, **cada um com a sua instância** (injetada). Campos da instância só são vistos por um ator por vez |

```groovy
@Handler(value = '/relatorios', execution = 'actor', poolSize = 2)
class Relatorios {
    def relatorioService
    @Get('/mensal') def mensal() { render json: relatorioService.mensal() }
}
```

## Descoberta e registro

As classes `@Handler` do pacote da aplicação são achadas pela varredura. Na [DSL de projeto](dsl-projeto.md), `routes { handlers LivrosApi, AutoresApi }` também as registra (útil para classes fora do pacote varrido); a mesma classe não é registrada duas vezes. Uma classe listada em `handlers` sem `@Handler` impede a subida.

## Interceptores

Métodos com `@Before`, `@After` e `@OnError` rodam antes, depois ou na exceção das rotas da classe (com um padrão de caminho opcional no `value`):

```groovy
@OnError
def falhou(Throwable e) { render status: 400, json: [erro: e.message] }
```

Detalhes em [Interceptores](interceptores.md).

## Veja também

- [Interceptores](interceptores.md)
- [Rotas por closure](rotas-closure.md)
- [Respostas: render, respond e redirect](respostas.md)
- [Segurança sem o plugin](seguranca.md)
- [Validação, binding e mensagens](../recursos/validacao.md)
