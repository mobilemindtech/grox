# Rotas por closure

Uma classe que implementa `org.grox.dsl.Routes` declara rotas num closure, com um verbo HTTP, um caminho e o código que atende. Este capítulo cobre a declaração, os caminhos, as opções de rota e de grupo, o que o handler enxerga e a execução em virtual thread ou em ator.

## Declaração

```groovy
package livraria

import org.grox.dsl.Routes

class Rotas implements Routes {

    LivroService livroService                     // injetado pelo nome

    Closure routes = {
        get('/') { redirect '/livros' }
        get('/livros', name: 'index') {
            render view: 'livros/index', model: [livros: livroService.todos()]
        }
        get('/livros/{id}', name: 'show', view: 'livros/show') {
            Livro livro = livroService.buscar(params.long('id'))
            if (!livro) return render(status: 404)
            [livro: livro]                         // sem render: modelo da view: da rota
        }
        post('/livros', name: 'save') {
            Livro livro = livroService.salvar(params)
            if (livro.hasErrors()) {
                return render(view: 'livros/index', model: [livros: livroService.todos(), novo: livro], status: 422)
            }
            flash.message = "Livro ${livro.titulo} criado"
            redirect "/livros/${livro.id}"
        }
        group('/relatorios', execution: 'actor', poolSize: 2) {
            get('/autores', name: 'autores') { render json: livroService.porAutor() }
        }
    }
}
```

- A classe é achada pela varredura do pacote da aplicação. Ela é instanciada uma vez e recebe injeção por nome: uma propriedade `livroService` recebe o service `LivroService`.
- O closure `routes` roda uma vez, na subida, para registrar as rotas. O closure de cada rota roda a cada requisição.
- Podem existir várias classes `Routes` na mesma app.

## Verbos

| Método | HTTP |
|---|---|
| `get(caminho) { }` | GET |
| `post(caminho) { }` | POST |
| `put(caminho) { }` | PUT |
| `patch(caminho) { }` | PATCH |
| `delete(caminho) { }` | DELETE |

Cada um aceita um mapa de opções antes do caminho: `get('/livros', name: 'index') { }`.

## Caminhos

- `{nome}` vira um parâmetro: `/livros/{id}` casa com `/livros/42` e põe `id = '42'` em `params`.
- A rota mais específica (com mais texto literal) casa primeiro, como no UrlMappings.
- Caminho que existe com outro método HTTP responde `405`; caminho sem rota, `404`.
- Formulários podem simular PUT, PATCH e DELETE com o campo `_method` (o `<g:form method="PUT">` já gera) ou com o cabeçalho `X-HTTP-Method-Override`. Isso é do pipeline HTTP do core, então vale também aqui.

## Opções

| Opção | Na rota | No grupo | Efeito |
|---|---|---|---|
| `name:` | sim | | Nome da rota no grupo: o "action" dos links (`createLink`), das métricas e do `actionName` nas views. Sem ele, o nome é gerado (`r0`, `r1`...). Dois nomes iguais na mesma classe impedem a subida |
| `view:` | sim | sim | Um `Map` devolvido pelo handler, sem `render`, vira o modelo dessa view |
| `execution:` | sim | sim | `virtual` ou `actor` (ver abaixo) |
| `poolSize:` | sim | sim | Atores da rota com `execution: 'actor'` |
| `access:` | sim | sim | Regras de acesso da rota, como um `@Secured`: uma String ou lista (`'ROLE_ADMIN'`, `['ROLE_A', 'ROLE_B']`, `'permitAll'`). Valem antes das regras de URL. Ver [Segurança sem o plugin](seguranca.md) |

## Grupos

`group(prefixo, opções) { ... }` aplica um prefixo de caminho e opções comuns às rotas de dentro. Grupos podem ser aninhados; as opções da rota vencem as do grupo. `ns` é um nome alternativo para `group`, com o mesmo efeito *(extensão Grox)*.

```groovy
group('/admin', access: 'ROLE_ADMIN') {
    get('/painel', name: 'painel') { render { h1 'Painel' } }
    group('/relatorios', execution: 'actor', poolSize: 2) {
        get('/vendas', name: 'vendas') { render json: vendaService.resumo() }
    }
}
```

Dentro de um grupo, o caminho da rota é relativo ao do grupo, com ou sem a barra inicial, e pode ser omitido:

```groovy
ns('/usuarios') {
    get { render json: usuarioService.todos() }                               // GET /usuarios
    post { redirect "/usuarios/${usuarioService.salvar(params).id}" }         // POST /usuarios
    get('{id}') { render json: usuarioService.buscar(params.long('id')) }     // GET /usuarios/{id}
    get('/ativos', name: 'ativos') { render json: usuarioService.ativos() }   // GET /usuarios/ativos
}
```

`group('/x', ...)` com um caminho `'/'` dentro, ou sem caminho, gera `/x`, sem barra final.

Para código que roda antes, depois ou no erro das rotas de um grupo ou da classe (`before`, `after`, `error`), veja [Interceptores](interceptores.md).

## O que o handler enxerga

O closure da rota roda com a requisição (`RouteContext`) como delegate e a classe de rotas como owner. Por isso, ele acessa tanto a API da requisição quanto as propriedades da própria classe.

| Membro | Descrição |
|---|---|
| `params` | `GroxParams`: query string, formulário, arquivos enviados e as variáveis do caminho (estas vencem). `params.long('id')`, `params.int('max', 10)`, `boolean`, `double`, `list('ids')`, `date('d', 'dd/MM/yyyy')` |
| `request` | `GroxRequest`: `method`, `path`, `getHeader()`, `JSON` ou `json` (corpo JSON parseado), `getFile('campo')`, `cookies`, `userPrincipal`, atributos. Implementa `HttpServletRequest` |
| `response` | `GroxResponse`: `status`, `setHeader()`, `contentType`, `writer`, `outputStream` |
| `session`, `flash` | Sessão HTTP (memória ou Redis) e escopo flash (dura até a próxima requisição) |
| `locale` | Locale da requisição: `?lang=en` (guardado na sessão), a sessão, o `Accept-Language` ou `grox.i18n.defaultLocale` |
| `config` | A configuração da aplicação: `config.getProperty('grox.routes.poolSize', Integer)` |
| `route` | A rota em atendimento: `route.action`, `route.path`, `route.httpMethod`, `route.execution`, `route.poolSize` |
| services e beans | Pelo nome, como propriedades (`livroService`, `relogio`), mesmo sem declarar a propriedade na classe |
| `render`, `respond`, `redirect` | Respostas: ver [Respostas](respostas.md) |

Se o closure declara um parâmetro, ele recebe o `RouteContext`:

```groovy
get('/eco') { ctx -> render text: ctx.request.path }
```

Cada handler roda com a autenticação da requisição e uma sessão de dados disponível, aberta só no primeiro acesso ao banco e mantida até o fim (inclusive a renderização da view). Associações lazy funcionam na GSP.

## Execução: virtual thread ou ator

| Modo | Como roda | Quando usar |
|---|---|---|
| `virtual` (padrão) | Uma virtual thread por requisição | Quase sempre. Bloquear (JDBC, HTTP) é barato |
| `actor` | Um pool de `poolSize` atores; a requisição vai para o ator com menos mensagens pendentes | Limitar a concorrência de um trecho (relatórios pesados, integrações com cota) |

Os padrões vêm da configuração:

```yaml
grox:
  routes:
    execution: virtual      # ou actor
    poolSize: 8             # padrão: 2 × CPUs
```

Cada rota ou grupo troca com `execution:` e `poolSize:`. Valor inválido impede a subida.

Nas rotas por closure, a instância da classe `Routes` é única e compartilhada entre threads e atores. Guarde estado só em services (ou em services-ator), nunca em campos da classe de rotas.

## Links para as rotas

Cada classe de rotas é um "controller" no roteamento: o nome é o da classe em camelCase, sem o sufixo `Routes` ou `Handler` (`Rotas` → `rotas`, `LivroRoutes` → `livro`, `LivrosApi` → `livrosApi`). A opção `name:` é o "action":

```groovy
redirect controller: 'rotas', action: 'index'           // 302 para /livros
redirect controller: 'rotas', action: 'show', id: 1     // 302 para /livros/1
```

Nas GSPs e na DSL de HTML, `<g:link controller="rotas" action="index">` e `g.createLink(controller: 'rotas', action: 'show', id: 1)` usam as mesmas rotas. `uri:` e `url:` também funcionam. Duas classes com o mesmo nome lógico impedem a subida.

## Veja também

- [Rotas por anotação](rotas-anotacao.md)
- [Respostas: render, respond e redirect](respostas.md)
- [DSL de projeto](dsl-projeto.md)
- [Concorrência: atores e virtual threads](../recursos/concorrencia.md)
