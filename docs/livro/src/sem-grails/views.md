# Views e HTML

O módulo `grox-gsp` traz o motor GSP, os layouts SiteMesh 3 e as tags `g:` nativas também para apps sem `grails-app/`. Este capítulo mostra onde ficam as views, como o layout é escolhido, o que as views enxergam numa rota, a página de login, TagLibs próprias e a DSL de HTML, a alternativa à GSP. Views em Pug e Markdown estão em [Views em Pug e Markdown](../recursos/pug-markdown.md).

## Onde ficam as views

As views ficam em `src/main/resources/views/`, que vai para o classpath como `views/`:

```
src/main/resources/views/
  layouts/main.gsp
  livros/index.gsp
  livros/show.gsp
  livros/_linha.gsp          template
  login/auth.gsp             página de login (opcional)
```

- `render view: 'livros/index'` renderiza `views/livros/index.gsp`. A barra inicial é opcional.
- Templates começam com `_` e são renderizados com `<g:render template="/livros/linha" model="[l: l]"/>`.
- Em development, para recarregar as views ao salvar, aponte `grox.gsp.viewsDir` para o diretório de fontes (`src/main/resources/views`); o padrão (`grails-app/views`) não existe nesta trilha. Sem isso, as views vêm do classpath e são compiladas uma vez.
- Não há pré-compilação no build (a tarefa `compileGsp` é do plugin `org.grox.app`): cada GSP é compilada no primeiro uso. Erros mostram o arquivo e a linha da GSP (`/livros/show.gsp:13: ...`).

## Layout

```html
<!-- views/layouts/main.gsp -->
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8"/>
    <title><g:layoutTitle default="Livros"/></title>
    <g:layoutHead/>
</head>
<body>
<header>
    <a href="/livros">Livros</a>
    <g:if test="${request.userPrincipal}">
        · ${request.userPrincipal.name}
        <g:form uri="/logoff" method="POST" style="display:inline"><button>Sair</button></g:form>
    </g:if>
</header>
<g:if test="${flash.message}"><p class="flash">${flash.message}</p></g:if>
<g:layoutBody/>
</body>
</html>
```

```html
<!-- views/livros/index.gsp -->
<html>
<head><meta name="layout" content="main"/><title>Livros</title></head>
<body>
<h1>Livros</h1>
<ul>
<g:each in="${livros}" var="l"><li><g:link uri="/livros/${l.id}">${l.titulo}</g:link> (${l.autor})</li></g:each>
</ul>
</body>
</html>
```

Ordem de escolha do layout de uma view:
1. `<meta name="layout" content="main"/>` na página;
2. `layouts/<grupo>/<rota>.gsp` (por exemplo `layouts/rotas/index.gsp`);
3. `layouts/<grupo>.gsp` (`layouts/rotas.gsp`);
4. a configuração `grails.sitemesh.default.layout`.

Só respostas `text/html` de views são decoradas; `render text:` e `render json:` não. Funcionam `g:layoutTitle`, `g:layoutHead`, `g:layoutBody`, `g:pageProperty` (`<content tag="menu">` na página), `g:ifPageProperty`, `g:applyLayout` e o `<sitemesh:write property="body"/>` nativo.

## O que a view enxerga

| Variável | Valor numa rota do `grox-dsl` |
|---|---|
| o modelo | o `model:` do `render`/`respond`, ou o `Map` devolvido com `view:` |
| `params`, `request`, `response`, `session`, `flash`, `locale`, `out`, `pageScope` | os da requisição |
| `controllerName` | o grupo: a classe de rotas em camelCase sem `Routes`/`Handler` (`rotas`, `livrosApi`, ou o nome da classe da aplicação para as rotas da DSL de projeto) |
| `actionName` | o nome da rota (`name:`) ou do método |

Uma variável desconhecida vale `null`. `${expr}` é codificado em HTML; `${raw(x)}` imprime sem codificar.

## Tags disponíveis

As tags `g:` nativas do `grox-gsp` funcionam sem o módulo Grails:

| Grupo | Tags |
|---|---|
| Controle | `if`/`elseif`/`else`, `unless`, `while`, `each`, `set` |
| Links | `link`, `createLink`, `resource` |
| Formulários | `form` (com `_method` para PUT/PATCH/DELETE e o token CSRF), `uploadForm`, `textField`, `passwordField`, `hiddenField`, `emailField`, `numberField`, `dateField`, `field`, `textArea`, `checkBox`, `radio`, `select`, `submitButton`, `actionSubmit`, `datePicker` |
| Erros de validação | `hasErrors`, `eachError`, `renderErrors`, `fieldValue`, `message error=` |
| Templates e listas | `render`, `paginate`, `sortableColumn` |
| Layouts | `layoutTitle`, `layoutHead`, `layoutBody`, `pageProperty`, `ifPageProperty`, `applyLayout` |
| i18n e formatação | `message`, `formatDate`, `formatNumber`, `formatBoolean`, `join`, `cookie`, `renderException` |

Os links usam as rotas: `<g:link controller="rotas" action="show" id="${l.id}">` gera `/livros/7` a partir da rota `show` da classe `Rotas`. `uri:` e `url:` também funcionam.

O que não existe sem os módulos de plugins:
- **tags `sec:`** (são do plugin spring-security, no `grox-grails-plugins`). Use `request.userPrincipal`: `<g:if test="${request.userPrincipal}">`, `${request.userPrincipal.name}`, `request.userPrincipal.authorities*.authority`;
- **tags `asset:`** (plugin asset-pipeline). Com o `grox-assets`, os arquivos de `grails-app/assets` (ou `grox.assets.dir`) continuam servidos em `/assets/**`; escreva o `<link>`/`<script>` à mão.

A lista completa de atributos está em [Tags GSP](../referencia/tags.md).

## Página de login

Com a segurança ativa, se existir `views/login/auth.gsp`, ela é a página de login; senão, vale a página padrão do framework. O modelo:

| Variável | Conteúdo |
|---|---|
| `postUrl` | URL do POST do login (`/login/authenticate`) |
| `usernameParameter`, `passwordParameter` | nomes dos campos (`username`, `password`) |
| `csrfParameter`, `csrfToken` | campo e valor do token CSRF |
| `errorMessage` | mensagem traduzida da falha de login, ou `null` |

```html
<html>
<head><meta name="layout" content="main"/><title>Entrar</title></head>
<body>
<g:if test="${errorMessage}"><p class="erro">${errorMessage}</p></g:if>
<form action="${postUrl}" method="post">
    <input type="hidden" name="${csrfParameter}" value="${csrfToken}"/>
    <input name="${usernameParameter}"/> <input name="${passwordParameter}" type="password"/>
    <button>Entrar</button>
</form>
</body>
</html>
```

A página é renderizada com `controllerName = 'login'` e `actionName = 'auth'`, com uma sessão de dados aberta (o layout pode consultar o banco) e `Cache-Control: no-store`.

## TagLibs próprias

Classes `*TagLib` do pacote da aplicação são registradas depois das nativas. Fora de `grails-app/taglib` não há AST, então implemente o trait `org.grox.view.taglib.TagLibApi` para ter `out`, `params`, `request` e as tags `g.*`:

```groovy
package livraria

import org.grox.http.Html
import org.grox.view.taglib.TagLibApi

class FormatacaoTagLib implements TagLibApi {
    static namespace = 'fmt'

    def badge = { attrs, body -> out << '<span class="badge">' << body() << '</span>' }
    def ano(Map attrs) { out << Html.escape(attrs.livro?.ano?.toString() ?: 's/d') }
}
```

Uso: `<fmt:badge>novo</fmt:badge>`, `<fmt:ano livro="${l}"/>`. A saída das tags não é codificada: codifique os valores (`Html.escape`, `encodeAsHTML()`). Detalhes em [TagLibs](../recursos/taglibs.md).

## DSL de HTML

Em vez de uma GSP, a rota pode montar o HTML com um closure (o `MarkupBuilder` do Groovy, ajustado para HTML5):

```groovy
get('/sobre', name: 'sobre') {
    render(layout: 'main') {
        h1 'Sobre'
        p "${livroService.todos().size()} livros & contando"          // texto escapado: &amp;
        a(href: g.createLink(controller: 'rotas', action: 'index'), 'Ver livros')
        ul { livroService.todos().each { l -> li l.titulo } }
    }
}
```

- Elementos vazios do HTML (`br`, `input`, `img`, `meta`...) saem como `<br />`; os demais, mesmo vazios, com a tag de fechamento (`<script src="x"></script>`).
- Texto e atributos são escapados; um valor seguro (saída de tags `g.`, `raw(...)`) entra sem escape duplo; atributos `null` são omitidos.
- Começando por `html { }`, a saída ganha o `<!DOCTYPE html>`.
- As tags ficam nos namespaces (`g.message(...)`, `g.createLink(...)`, `fmt.badge(...)`). Um nome solto vira elemento: `message(...)` gera `<message>`.
- Opções: `layout:`, `model:` (para o layout), `status:` e `contentType:`. Sem `layout:`, vale a escolha padrão (inclusive `meta(name: 'layout', content: 'main')` no `head`).
- Com `contentType` JSON, o closure monta JSON; com XML, o `MarkupBuilder` puro.

O resultado é decorado pelo layout SiteMesh como uma view GSP. Mais em [DSL de HTML](../recursos/dsl-html.md).

## Veja também

- [Views em Pug e Markdown](../recursos/pug-markdown.md)

- [Views GSP](../recursos/gsp.md)
- [Layouts (SiteMesh 3)](../recursos/layouts.md)
- [Tags GSP](../referencia/tags.md)
- [Respostas: render, respond e redirect](respostas.md)
