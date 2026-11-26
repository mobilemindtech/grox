# API dos controllers

Resumo de tudo o que está disponível dentro de controllers, interceptors e TagLibs. A AST aplica a API pelo diretório do fonte: `grails-app/controllers/*Controller` ganha o trait `ControllerApi`, `grails-app/controllers/*Interceptor` o `InterceptorApi` e `grails-app/taglib/*TagLib` o `GrailsTagLibApi`. Nenhuma dessas classes estende nada.

## Propriedades estáticas do controller

| Propriedade | Padrão | Efeito |
|---|---|---|
| `static allowedMethods = [save: 'POST', delete: ['DELETE', 'POST']]` | | Método HTTP fora da lista dá `405` |
| `static responseFormats = ['html', 'json']` | | Formatos aceitos pelo `respond` |
| `static defaultAction = 'index'` | `index`, ou a única action | Action de `/<controller>` |
| `static concurrency = 'pool'` | `grox.controllers.concurrency` | `pool` ou `request` *(extensão Grox)* |
| `static poolSize = 8` | `grox.controllers.poolSize` | Atores do controller no modo `pool` *(extensão Grox)* |
| `static layout = 'admin'` | | Layout padrão das views do controller |

**Actions** são os métodos públicos e as propriedades closure no estilo Grails 2 (`def list = { }`, sem parâmetros). Um campo `private` com closure não é action.

## Membros disponíveis nas actions

| Membro | Tipo | Descrição |
|---|---|---|
| `params` | `GroxParams` | Query, formulário e variáveis da rota. Conversões: `params.int('max', 10)`, `long`, `double`, `float`, `short`, `byte`, `boolean`, `bigDecimal`, `list('ids')`, `date('d', 'dd/MM/yyyy')`. Acesso aninhado: `params.endereco.cidade` para a chave `endereco.cidade` |
| `request` | `GroxRequest` (`HttpServletRequest`) | Veja a tabela abaixo |
| `response` | `GroxResponse` (`HttpServletResponse`) | `status`, `setHeader()`, `contentType`, `writer`, `outputStream`, `sendError()`, `sendRedirect()`, `addCookie()` |
| `session` | `GroxSession` (`HttpSession`) | `session.x = v`, `getAttribute`, `invalidate()` |
| `flash` | `FlashScope` | Dura até a próxima requisição |
| `controllerName`, `actionName` | `String` | |
| `webRequest` | `WebRequest` | A requisição do Grox (params, sessão, links, mensagens) |
| `grailsApplication` | `GrailsApplication` | Configuração, artefatos, `mainContext` |
| `grailsAttributes` | `GrailsApplicationAttributes` | `messageSource`, `applicationContext`, `getControllerUri(request)`... |
| `log` | SLF4J `Logger` | |
| `g` | namespace de tags | `g.createLink(...)`, `g.render(template: 'x')`, `g.message(...)` |
| `<namespace>` | namespace de tags | Qualquer namespace de TagLib como propriedade: `fmt.moeda(valor: v)`, `auth.isAdmin()` |
| `<tag g:>(...)` | | Tags `g:` chamadas direto: `formatDate(date: d, format: 'dd/MM')` |

### `request`

| Membro | Descrição |
|---|---|
| `method`, `requestURI`, `forwardURI`, `contextPath` (sempre a raiz) | |
| `getHeader(n)`, `getHeaders(n)`, `headerNames`, `getDateHeader(n)`, `getIntHeader(n)` | Cabeçalhos |
| `JSON` | Corpo JSON parseado, como `JSONObject`/`JSONArray` (que são `Map`/`List`) |
| `jsonRequest`, `xhr`, `multipart` | Booleanos |
| `text`, `reader`, `inputStream` | Corpo cru |
| `format` | Formato pelo `Content-Type` (`form`, `multipartForm`, `json`) |
| `withFormat { json { } form { } }` | Bloco conforme o `Content-Type` da requisição |
| `getFile('campo')`, `getFiles('campo')`, `fileNames` | Upload multipart |
| `getAttribute`, `setAttribute`, `request.x` | Atributos (também como propriedades) |
| `cookies`, `locale`, `locales`, `parameterMap`, `getParameter(n)` | Servlet API |
| `remoteAddr`, `clientAddr`, `scheme`, `serverName`, `secure`, `requestURL` | Respeitam `X-Forwarded-*` de proxy confiável |
| `exception` | A exceção da requisição (no controller do mapeamento `"500"`) |
| `userPrincipal`, `remoteUser` | Usuário autenticado pela segurança do Grox. `isUserInRole` é sempre `false` |

O que depende de container de Servlet (`getServletContext`, `startAsync`, `getRequestDispatcher`, `getParts`, `login`, `logout`, `authenticate`, `upgrade`) lança `UnsupportedOperationException`.

## Métodos das actions

| Método | Exemplos e observações |
|---|---|
| `render` | `render 'texto'`; `render text: 'x', contentType: 'text/plain', status: 201`; `render view: 'edit', model: [...]`; `render template: 'linha', model:/bean:/collection:, var:`; `render status: 204`; `render status: NOT_FOUND` (`HttpStatus` do Spring); `render file: bytes, fileName: 'a.pdf'`; `render obj as JSON`; `render(404)`, `render(obj)` (texto do objeto) |
| `render { }` | Sem `contentType`: HTML pela DSL de HTML (`render(layout: 'main') { h1 'Livros' }`). Com `contentType` JSON: monta o JSON (`render(status: 401, contentType: 'application/json') { erro = 'x' }`). Com XML: `MarkupBuilder` |
| `respond` | `respond obj`; `respond obj, status: 201, model: [...], view: 'x'`; `respond lista`; `respond obj.errors, status: 422`. `null` vira `404` |
| `redirect` | `redirect action: 'show', id: 1, params: [...]`, `controller:`, `uri:`, `url:`, `permanent: true`, `resource: instancia` |
| `withFormat { html { } json { } '*' { } }` | Executa o bloco do formato negociado |
| `bindData(obj, fonte)`, `bindData(obj, fonte, [include: [...], exclude: [...]])` | Binding com conversão de tipos |
| `createLink(controller:, action:, id:, params:, absolute:, resource:)` | URL a partir do UrlMappings |
| `message(code:, args:, default:)`, `message(error: fieldError)` | i18n no locale da requisição |
| `header('X-Nome', valor)` | Cabeçalho da resposta |
| `task { }` | Tarefa assíncrona (como `Promises.task`); devolva-a da action |
| `renderPdf(template:, model:, filename:)` | Plugin rendering (exige o `flying-saucer-pdf`) |

Argumentos nomeados chegam primeiro: `respond x, status: 201` equivale a `respond([status: 201], x)`. Uma action que escreve o corpo direto (`response.outputStream << bytes`) não cai na view padrão.

### Retorno da action

| Retorno | Efeito |
|---|---|
| `Map` | Modelo da view `views/<controller>/<action>.gsp` |
| `null` ou nada, sem render | View padrão com modelo vazio |
| `grails.async.Promise`, `CompletableFuture`, `CompletionStage`, `Awaitable` | Action assíncrona: o ator fica livre e a resposta sai quando completar |

### Parâmetros da action

| Tipo do parâmetro | Origem |
|---|---|
| Tipos simples (números, `String`, `Boolean`, datas, enums, `UUID`) | `params` pelo nome, convertidos. Inválido: `null` (ou `0` em primitivos) |
| `MultipartFile` | Arquivo do campo de mesmo nome |
| `Map` | JSON do corpo ou `params` |
| Domain class | Com `id` na requisição, carregada (ou `null`) e preenchida; senão, nova. `validate()` já foi chamado |
| Outra classe (command object) | Instanciada, injetada e preenchida com o JSON ou `params`; `validate()` se existir |

## Segurança nos controllers

Com o plugin spring-security ativo:

| Membro | Descrição |
|---|---|
| `isLoggedIn()` | Há usuário autenticado |
| `getPrincipal()` | `GroxUser` (username, authorities, roles, id, tenantId), ou `null` |
| `getAuthenticatedUser()` / `authenticatedUser` | A domain class do usuário logado |
| `springSecurityService` | Por injeção: `currentUser`, `principal`, `loggedIn`, `authentication`, `encodePassword()`, `reauthenticate()` |

## Interceptors

Classes `*Interceptor` em `grails-app/controllers`:

| Membro | Descrição |
|---|---|
| `int order` | Menor roda primeiro no `before` (e por último no `after`). Constantes `HIGHEST_PRECEDENCE`, `LOWEST_PRECEDENCE` |
| `match(controller:, action:, uri:, method:)`, `matchAll()`, `.excludes(...)` | No construtor. Sem nenhum, intercepta o controller de mesmo nome |
| `boolean before()` | `false` interrompe (renderize ou redirecione antes) |
| `boolean after()` | `false` impede a view |
| `void afterView()` | Depois da renderização |
| `model`, `view` (get e set) | Modelo e view da action, alteráveis no `after()` |
| `modelAndView.model`, `modelAndView.viewName` | Mesma coisa, no formato do Spring |
| `throwable` | Exceção da requisição |
| `params`, `request`, `response`, `session`, `flash`, `controllerName`, `actionName`, `webRequest`, `log` | Como nos controllers |
| `render`, `redirect`, `header` | Como nos controllers |

## TagLibs

Classes `*TagLib` em `grails-app/taglib`:

| Membro | Descrição |
|---|---|
| `static namespace = 'fmt'` | Padrão: `g` |
| `static returnObjectForTags = ['total']` | Tags que, chamadas como método, devolvem o objeto em vez do HTML |
| `out` | Writer da renderização |
| `params`, `request`, `response`, `session`, `flash`, `controllerName`, `actionName`, `pageScope`, `webRequest`, `log` | |
| `grailsApplication`, `grailsAttributes` | |
| `g.<tag>(...)`, `<tag>(...)`, `<namespace>.<tag>(...)` | Outras tags |
| `attrs.int('x')`, `attrs.boolean('y')`, `attrs.list('ids')` | Conversões nos atributos |
| `body()`, `body(item)`, `body([nome: v])` | Corpo renderizado; com `it` ou variáveis |

A saída das tags não é codificada: codifique os valores com `encodeAsHTML()` ou `org.grox.http.Html.escape(...)`.

## Veja também

- [Controllers](../recursos/controllers.md)
- [Interceptors](../recursos/interceptors.md)
- [TagLibs](../recursos/taglibs.md)
- [Tags GSP](tags.md)
- [API do GORM](api-gorm.md)
