# Controllers

Este capítulo cobre os controllers no estilo do Grails: como declarar actions, as propriedades estáticas que mudam o comportamento, a API disponível (`params`, `request`, `render`, `respond`...), a negociação de formato e o preenchimento dos parâmetros das actions.

## Declarando um controller

Um controller é uma classe `*Controller` em `grails-app/controllers`. Não estende nada: a AST aplica o trait `ControllerApi`.

```groovy
class LivroController {
    static allowedMethods = [save: 'POST', delete: ['DELETE', 'POST']]  // senão, 405
    static responseFormats = ['html', 'json']    // formatos aceitos pelo respond
    static defaultAction = 'index'               // padrão: index, ou a única action
    static concurrency = 'pool'                  // 'pool' (padrão da app) ou 'request'
    static poolSize = 8                          // atores no modo pool
    static layout = 'admin'                      // layout padrão das views deste controller

    LivroService livroService                    // injeção por nome

    def index(Integer max) { [livros: livroService.listar(max ?: 10)] }  // Map → views/livro/index.gsp
    def show(Long id) { respond livroService.buscar(id) }                // null → 404
    def save(Livro livro) { ... }                                        // command object (params ou JSON)
}
```

**Actions** são:

- os métodos públicos do controller;
- as propriedades closure no estilo Grails 2 (`def list = { ... }`), sem parâmetros (use `params`). Um campo `private` com closure não vira action, como no Grails.

| Propriedade estática | Efeito |
|---|---|
| `allowedMethods` | Métodos HTTP aceitos por action. Outro método dá `405` |
| `responseFormats` | Formatos que o `respond` aceita, em ordem de preferência |
| `defaultAction` | Action de `/livro`. Padrão: `index`, ou a única action |
| `concurrency` | `pool` (atores reaproveitados) ou `request` (instância nova por requisição). Veja [Concorrência](concorrencia.md) |
| `poolSize` | Quantos atores no modo `pool` (padrão `grox.controllers.poolSize`, 2 × CPUs) |
| `layout` | Layout padrão das views deste controller |

## O que a action devolve

| Retorno | Resultado |
|---|---|
| `Map` | Modelo da view padrão `views/<controller>/<action>.gsp` |
| `null` (sem render) | A view padrão, com modelo vazio |
| Algo já escrito (`render`, `redirect`, `respond`, `response.outputStream << bytes`) | Nada mais: a view padrão não é usada |
| `Promise`, `CompletableFuture`, `Awaitable` | Action assíncrona (veja [Actions assíncronas](async.md)) |

## API disponível nas actions

| Membro | Descrição |
|---|---|
| `params` | Query + form + variáveis da rota. `params.int('max', 10)`, `long`, `double`, `boolean`, `list('ids')`, `date('d', 'dd/MM/yyyy')` e acesso aninhado (`params.endereco.cidade` para a chave `endereco.cidade`) |
| `request` | `method`, `getHeader()`, `JSON` (corpo parseado), `jsonRequest`, `xhr`, `forwardURI`, `get/setAttribute` (também `request.x`), `cookies`, `getFile('campo')`, `getFiles('campo')`, `fileNames`, `multipart`, `getRequestURL()`, `reader`, `exception`, `format`, `withFormat` |
| `response` | `status`, `setHeader()`, `contentType`, `writer`, `outputStream`, `sendError()`, `sendRedirect()`, `addCookie()`, `headerNames` |
| `session`, `flash` | Sessão HTTP (memória ou Redis) e escopo flash, que dura até a próxima requisição |
| `controllerName`, `actionName`, `grailsApplication`, `log` | Como no Grails |
| `render`, `respond`, `redirect`, `withFormat` | Respostas (abaixo) |
| `bindData(obj, params, [include:/exclude:])` | Binding com conversão de tipos |
| `createLink(...)`, `g.<tag>(...)`, `message(code:, args:)` | Links, tags e i18n |
| `header('X-Nome', valor)` | Define um cabeçalho da resposta |
| `isLoggedIn()`, `getPrincipal()`, `getAuthenticatedUser()` | Com a segurança ativa |
| `grailsAttributes` | `messageSource`, `applicationContext`, `grailsApplication`, `getApplicationUri`/`getControllerUri`/`getControllerActionUri(request)`, `getFlashScope(request)`. O context path é sempre a raiz |

`request`, `response` e `session` implementam as interfaces completas do jar `jakarta.servlet-api` 6.1: bibliotecas que recebem `HttpServletRequest` funcionam. O que depende de container (`getServletContext`, `startAsync`, `getRequestDispatcher`, `getParts`, `login`/`logout`, `upgrade`) lança `UnsupportedOperationException`. `getUserPrincipal` e `getRemoteUser` devolvem o usuário autenticado; `isUserInRole` é sempre `false` (use `springSecurityService` ou `SpringSecurityUtils.ifAllGranted`).

## `render`

```groovy
render 'texto'                                             // text/html
render text: 'x', contentType: 'text/plain', status: 201
render text: null                                           // envia "null", como no Grails
render view: 'edit', model: [livro: l]                      // views/livro/edit.gsp
render view: '/shared/erro'                                 // caminho absoluto em views/
render template: 'linha', model: [l: l]                     // views/livro/_linha.gsp
render template: 'linha', bean: l                           // modelo: it
render template: 'linha', collection: livros, var: 'l'      // repete para cada item
render status: 204
render status: NOT_FOUND                                    // org.springframework.http.HttpStatus
render 404                                                  // só o status
render file: bytes, fileName: 'a.pdf', contentType: 'application/pdf'
render livros as JSON                                       // grails.converters.JSON
render(status: 200, contentType: 'application/json') {      // estilo JSONBuilder
    nome = 'Ana'
    endereco { cidade = 'Recife' }
}
render(layout: 'main') { h1 'Olá' }                         // DSL de HTML
render obj                                                   // escreve o texto do objeto
```

- `render x as JSON` fecha o writer, como no Grails: renders seguintes na mesma resposta são descartados.
- `render { }` sem `contentType` gera **HTML** com o `MarkupBuilder`; com `contentType` JSON, monta JSON; com XML, o `MarkupBuilder` puro. Veja [DSL de HTML](dsl-html.md).
- `render view:` e a view padrão são **adiados** até depois dos `after()` dos interceptors. Templates e `render text:` saem na hora. Veja [Interceptors](interceptors.md).

## `respond`

```groovy
respond livro                                  // json: o objeto; html: view da action com [livro: livro]
respond livros                                  // html: [livroList: livros, livroCount: n]
respond livro, status: 201, model: [extra: 1], view: 'detalhe'
respond livro.errors, status: 422               // {"errors": [{"object", "field", "rejected-value", "message"}]}
respond null                                    // 404
```

- No formato `json`, responde com o objeto serializado. Entidades saem **rasas**: as propriedades, mais `{id}` nas associações; o `tenantId` não aparece.
- No formato `html`, renderiza a view da action com o modelo `livro` (ou `livroList` + `livroCount` para coleções), mesclado com `model:`.
- Com `grails.scaffolding.templates.domainSuffix: Instance`, os nomes viram `livroInstance` e `livroInstanceList`, como nos scaffolds antigos.

## `redirect`

```groovy
redirect action: 'show', id: 1, params: [aba: 'dados']
redirect controller: 'home'
redirect uri: '/livros/1'
redirect url: 'https://exemplo.com'
redirect action: 'index', permanent: true      // 301
redirect resource: livro, action: 'update'     // controller pelo nome da classe e id da instância
```

URLs são geradas a partir do [UrlMappings](urlmappings.md). `chain` e `forward` não existem.

## Negociação de formato e `withFormat`

O formato é escolhido nesta ordem:

1. `params.format`, que vem da extensão na URL (`/livro/show/1.json`) ou do mapeamento (`format: 'json'`);
2. o cabeçalho `Accept`;
3. `Content-Type` JSON na requisição.

Sem preferência (`*/*`), vale o primeiro de `responseFormats` ou o primeiro bloco do `withFormat`.

```groovy
def show(Long id) {
    def l = Livro.get(id)
    withFormat {
        html { [livro: l] }
        json { render l as JSON }
        '*' { render status: 406 }
    }
}
```

`request.withFormat { form { } multipartForm { } json { } }` decide pelo **Content-Type da requisição**, como no Grails. Sem corpo, vale o primeiro bloco. `request.format` dá o nome do formato.

## Parâmetros das actions

Os parâmetros são preenchidos **por nome**:

| Tipo do parâmetro | Origem |
|---|---|
| Números, `String`, `Boolean`, datas, enums, `UUID` | `params`, convertidos. Valor inválido vira `null` (ou `0` em primitivos), sem erro |
| `MultipartFile` | Arquivo enviado no campo de mesmo nome |
| `Map` | O JSON do corpo ou `params` |
| `GrailsParameterMap` | `params` |
| Domain class | Com `id` na requisição, a entidade é **carregada** e recebe o binding (`null` se não existir); sem `id`, uma nova. `validate()` já foi chamado |
| Outra classe | **Command object**: instanciado, injetado e preenchido com o JSON do corpo (`Content-Type: application/json`) ou com `params`; depois `validate()`, se existir |

```groovy
class BuscaCommand implements grails.validation.Validateable {
    String termo
    Integer pagina
    static constraints = { termo blank: false; pagina nullable: true, min: 1 }
}

def buscar(BuscaCommand cmd) {
    if (cmd.hasErrors()) return render(view: 'busca', model: [cmd: cmd], status: 422)
    [resultados: Livro.findAllByTituloIlike("%${cmd.termo}%")]
}

def update(Usuario usuario) {          // carregado pelo id e já validado
    if (usuario == null) return render(status: 404)
    if (usuario.hasErrors()) return respond(usuario.errors, status: 422)
    usuario.save()
    redirect action: 'show', id: usuario.id
}
```

`id`, `version`, `dateCreated` e `lastUpdated` nunca são vinculados, e propriedades com `bindable: false` ficam de fora. Detalhes do binding em [Validação, binding e mensagens](validacao.md).

## JSON

- `request.JSON` e `JSON.parse(texto)` devolvem `org.grails.web.json.JSONObject`/`JSONArray`. Eles são `Map`/`List` e também têm os métodos do org.json: `optInt`, `optLong`, `optDouble`, `optBoolean`, `optString`, `optJSONObject`, `getInt`..., com `JSONException`.
- `obj as JSON` funciona em qualquer objeto, entidades e erros de validação.
- Marshallers customizados e `as XML` não existem.

## RestfulController

```groovy
class LivroController extends grails.rest.RestfulController<Livro> {
    static responseFormats = ['json']
    LivroController() { super(Livro) }          // super(Livro, true): só leitura
}
```

Implementa `index` (com `max` limitado a 100), `show`, `create`, `save`, `edit`, `update` e `delete`, como o do Grails:

| Requisição | `save` / `update` / `delete` |
|---|---|
| Formulário HTML (`form`, `multipartForm`) | Mensagem no flash (`default.created.message`, `default.updated.message`, `default.deleted.message`) e redirecionamento para `show` (ou `index`, no `delete`) |
| JSON e outros | `respond` com 201 (e `Location`), 200 ou 204 |
| Erro de validação | 422: a view `create`/`edit` com a instância, ou os erros em JSON |
| Registro inexistente | 404; num formulário, a mensagem `default.not.found.message` e a lista |

Os ganchos `queryForResource`, `createResource`, `listAllResources`, `countResources`, `saveResource`, `updateResource`, `deleteResource` e `getObjectToBind` podem ser sobrescritos. `super(Livro, true)` deixa o controller só leitura (405 nas escritas).

## Scaffold dinâmico

```groovy
class LivroController {
    static scaffold = Livro
}
```

Como no plugin scaffolding do Grails 3+, o controller passa a estender `RestfulController<Livro>` e ganha as sete actions. As views que você não criar (`views/livro/index.gsp`, `show`, `create`, `edit`) são geradas de templates e compiladas uma vez; criar uma delas faz a sua valer. A página tem a tabela, os formulários com rótulos, campos obrigatórios marcados e os erros de validação, e os botões de editar e remover.

- **Templates próprios:** ponha `index.gsp`, `show.gsp`, `create.gsp` e `edit.gsp` em `src/main/templates/scaffolding/`, no formato dos do Grails 3+: `${propertyName}` e `${className}` viram o nome da domain, e `\${...}` vira uma expressão da GSP.
- **Tags `f:`** (subconjunto do plugin fields): `<f:all bean="livro"/>`, `<f:field bean="livro" property="titulo"/>`, `<f:display bean="livro"/>` e `<f:table collection="${livroList}"/>`. O campo sai do tipo e das constraints: `inList`, enum e associação viram `<select>`; `email`, `url` e `password`, o input do tipo; `maxSize` acima de 255 ou `widget: 'textarea'`, um `<textarea>`; datas, o `g:datePicker` (ou `type="date"` para `LocalDate`). Coleções `hasMany` ficam fora do formulário.
- **Rótulos e mensagens:** `livro.titulo.label` ou o nome natural (`dataNascimento` → `Data Nascimento`); `livro.label` para o nome da classe. As mensagens padrão do scaffold (`default.list.label`, `default.button.create.label`...) vêm no framework, em inglês e português.
- **Não há:** a geração de código (`generate-all`), o `scaffold = true` do Grails 2 (erro de compilação com a orientação) e os templates por propriedade do plugin fields (`views/_fields/`).

## Tags e namespaces nas actions

Tags podem ser chamadas como métodos (`formatDate(date: d, format: 'dd/MM')`, `g.link(...)`), e qualquer namespace de TagLib é uma propriedade (`fmt.moeda(valor: v)`, `auth.isAdmin()`). Veja [TagLibs](taglibs.md).

## Erros

- `response.sendError(404)` ou uma exceção fazem o Grox procurar o mapeamento `"404"`/`"500"` do UrlMappings; se aponta para um controller, a requisição é redespachada para ele, com `request.exception` disponível.
- Sem mapeamento, sai a página de erro padrão (HTML ou JSON, conforme o `Accept`), com stack trace em development. Fora de development, a mensagem da exceção não aparece, só o status.

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| Controller singleton (padrão) | Uma instância **por ator** (`pool`) ou por requisição (`request`): campos de instância não são compartilhados entre atores |
| `chain`, `forward` | Ausentes |
| Namespaces de controller | Ausentes (o `GrailsClass.namespace` existe, para leitura) |
| `beforeInterceptor`/`afterInterceptor` (Grails 2) | Ausentes: use [Interceptors](interceptors.md) |
| Parâmetro simples inválido (`show(Long id)` com `abc`) | `null` sem erro |
| Transients e propriedades `def` | Vinculados no binding (o Grails não vincula por padrão) |

## Veja também

- [UrlMappings](urlmappings.md)
- [Concorrência: atores e virtual threads](concorrencia.md)
- [Validação, binding e mensagens](validacao.md)
- [API dos controllers](../referencia/api-controllers.md)
- [Upload de arquivos](upload.md)
