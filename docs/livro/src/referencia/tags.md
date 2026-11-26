# Tags GSP

Todas as tags que o Grox oferece, por namespace. As tags `g:` são nativas do módulo GSP; `sec:` vem do plugin spring-security, `asset:` do asset-pipeline e `rendering:` do plugin rendering. Toda tag também pode ser chamada como método (`${g.link(action: 'x') { 'corpo' }}`, `${createLink(action: 'x')}`) nas GSPs, controllers e TagLibs.

Regras gerais:
- Um atributo que é só `${...}` recebe o **objeto**; atributos mistos (`"a ${b}"`) viram `String`.
- Atributos literais de lista ou mapa são avaliados: `noSelection="['':'-']"`, `params="[a: 1]"`.
- Atributos que a tag não conhece vão para o elemento HTML gerado (`class`, `id`, `data-*`...).
- A saída das tags não é codificada de novo; a saída de `${}` é codificada em HTML.

## Controle (sintaxe)

Compiladas como estruturas de controle Groovy, não como chamadas de tag.

| Tag | Atributos | Observação |
|---|---|---|
| `g:if` | `test`, `env` | `<g:if env="development">` testa o ambiente |
| `g:elseif` | `test` | |
| `g:else` | | |
| `g:unless` | `test` | |
| `g:while` | `test` | |
| `g:each` | `in`, `var`, `status` | `var="${x}"` e `in="${lista?}"` também aceitos; sem `var`, o item é `it` |
| `g:set` | `var`, `value` (ou corpo) | Grava no `pageScope` |

## Links e recursos

| Tag | Atributos |
|---|---|
| `g:link` | `controller`, `action`, `id`, `params`, `uri`, `url`, `resource`, `absolute`, `base`, `fragment`; os demais vão para o `<a>` |
| `g:createLink` | Os mesmos de `g:link`; devolve a URL |
| `g:resource` | `dir`, `file`, `absolute`: URL em `/static/...` |
| `g:cookie` | `name`: valor do cookie da requisição |

## Formulários

| Tag | Atributos |
|---|---|
| `g:form` | `action`, `controller`, `id`, `url`, `resource`, `method` (`PUT`/`PATCH`/`DELETE` geram `_method`). Inclui o `_csrf` quando o CSRF está ativo |
| `g:uploadForm` | Como `g:form`, com `enctype="multipart/form-data"` |
| `g:textField`, `g:passwordField`, `g:hiddenField`, `g:emailField`, `g:numberField`, `g:dateField` | `name`, `value`; os demais vão para o `<input>` |
| `g:field` | `type`, `name`, `value` |
| `g:textArea` | `name`, `value`, `id` |
| `g:checkBox` | `name`, `value`, `checked`. Gera `_nome` para o desmarcado virar `false` no binding |
| `g:radio` | `name`, `value`, `checked` |
| `g:select` | `name`, `from`, `optionKey`, `optionValue`, `value`, `noSelection`, `id` |
| `g:submitButton` | `name`, `value` |
| `g:actionSubmit` | `action`, `value`: o botão escolhe a action (`_action_x`) |
| `g:datePicker` | `name`, `value`, `precision` (`year`...`minute`), `years`, `noSelection`, `id`. Binding por `nome_day`, `nome_month`, `nome_year`... |

## Erros de validação

| Tag | Atributos |
|---|---|
| `g:hasErrors` | `bean` ou `model`, `field`: renderiza o corpo se houver erros. Como método: `hasErrors(bean: u, field: 'nome', 'classe-css')` |
| `g:eachError` | `bean`, `field`, `var` |
| `g:renderErrors` | `bean`, `field`: `<ul class="errors">` |
| `g:fieldValue` | `bean`, `field`: valor da propriedade, inclusive o valor rejeitado |
| `g:message` | `error` (um `FieldError`), ou `code` |

## Templates, listas e layouts

| Tag | Atributos |
|---|---|
| `g:render` | `template`, `model`, `bean`, `collection`, `var` |
| `g:paginate` | `total`, `max`, `offset`, `maxsteps`, `params`, `controller`, `action`, `id`, `prev`, `next`, `omitPrev`, `omitNext`, `omitFirst`, `omitLast`. Classes `prevLink`, `step`, `currentStep`, `nextLink` |
| `g:sortableColumn` | `property`, `title` ou `titleKey`, `defaultOrder`, `params`, `controller`, `action`. Gera `<th class="sortable sorted asc">` |
| `g:layoutTitle` | `default` |
| `g:layoutHead` | |
| `g:layoutBody` | |
| `g:pageProperty` | `name` (`page.x`, `meta.x`, `body.onload`), `default` |
| `g:ifPageProperty` | `name` |
| `g:applyLayout` | `name`, `template`, `model` |

Caminhos de template: `template="form"` é `views/<controller>/_form.gsp`; `template="/shared/menu"` é `views/shared/_menu.gsp`; fora de um controller, é relativo ao diretório da view atual.

## i18n e formatação

| Tag | Atributos |
|---|---|
| `g:message` | `code`, `args`, `default`, `error`, `encodeAs` |
| `g:formatDate` | `date` (`Date` ou `java.time`), `format`, `locale`, `timeZone` |
| `g:formatNumber` | `number`, `type` (`currency`, `percent`, `number`), `format`, `currencyCode`, `minFractionDigits`, `maxFractionDigits`, `locale` |
| `g:formatBoolean` | `boolean`, `true`, `false` (textos; padrão: mensagens `boolean.true`/`boolean.false`) |
| `g:join` | `in`, `delimiter` |
| `g:renderException` | `exception` |

## Segurança (`sec:`)

Exigem o plugin spring-security (`grox-grails-plugins`).

| Tag | Atributos |
|---|---|
| `sec:ifLoggedIn`, `sec:ifNotLoggedIn` | |
| `sec:ifAllGranted`, `sec:ifAnyGranted`, `sec:ifNotGranted` | `roles` (lista separada por vírgula) |
| `sec:username` | |
| `sec:loggedInUserInfo` | `field` (propriedade do principal) |
| `sec:access`, `sec:noAccess` | `expression` (`"hasRole('ADMIN')"`), ou `url` e `method` |
| `sec:csrfToken` | Devolve o token |
| `sec:csrfInput` | Campo oculto `_csrf` |

## Assets (`asset:`)

Exigem o módulo de assets e o plugin asset-pipeline.

| Tag | Atributos |
|---|---|
| `asset:javascript` | `src` (extensão `.js` opcional); os demais (`defer`, `async`) vão para o `<script>` |
| `asset:stylesheet` | `href` ou `src` |
| `asset:image` | `src`; os demais vão para o `<img>` |
| `asset:link` | `href`, `rel`, `type` |
| `asset:script` | `type`: guarda o corpo para o `asset:deferredScripts` |
| `asset:deferredScripts` | Escreve os scripts guardados |
| `asset:assetPath` (também `g:assetPath` e `${assetPath(...)}`) | `src`: URL do asset (com digest em produção) |
| `asset:assetPathExists` (também `g:assetPathExists`) | `src`: `true` se o asset existe |

## PDF (`rendering:`)

| Tag | Atributos |
|---|---|
| `rendering:inlinePng`, `rendering:inlineJpeg`, `rendering:inlineGif` | `bytes`; os demais vão para o `<img>` (data URI) |

## Campos (`f:`, plugin fields)

Subconjunto usado pelas views do [scaffold dinâmico](../recursos/controllers.md#scaffold-dinâmico).

| Tag | Uso |
|---|---|
| `<f:all bean="livro" except="isbn"/>` | Um campo por propriedade editável (sem `id`, `version`, `dateCreated`, `lastUpdated` e coleções), na ordem das constraints |
| `<f:field bean="livro" property="titulo"/>` | Rótulo, widget, `required` e a classe `error` se a propriedade tem erro |
| `<f:display bean="livro"/>` | Lista rótulo/valor (`<ol class="property-list">`); com `property`, só o valor. Associações viram link para o `show` |
| `<f:table collection="${livroList}" properties="['titulo', 'autor']"/>` | Tabela; a primeira coluna leva ao `show`. Sem `properties`, as 7 primeiras. `domainClass` vale para a lista vazia |

`bean` aceita o nome da variável da página (`bean="livro"`) ou o objeto (`bean="${livro}"`).

## Codecs

Disponíveis em qualquer objeto, nas GSPs e no código: `encodeAsHTML()`, `encodeAsURL()`, `encodeAsJSON()`, `encodeAsJavaScript()`, `encodeAsRaw()`, `encodeAsMD5()`, `encodeAsSHA1()`, `encodeAsSHA256()` (hex; `...Bytes()` devolve `byte[]`), `encodeAsBase64()`, `encodeAsHex()`. `raw(x)` imprime sem codificar.

## Veja também

- [Views GSP](../recursos/gsp.md)
- [TagLibs](../recursos/taglibs.md)
- [Layouts (SiteMesh 3)](../recursos/layouts.md)
- [Assets e estáticos](../recursos/assets.md)
- [API dos controllers](api-controllers.md)
