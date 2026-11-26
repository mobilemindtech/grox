# Views em Pug e Markdown

Este capítulo cobre dois formatos de view além da GSP: **Pug** (módulo `grox-pug`, sobre o [pug4j](https://github.com/neuland/pug4j)) e **Markdown** (módulo `grox-markdown`, sobre o [commonmark-java](https://github.com/commonmark/commonmark-java)). Os dois funcionam nas apps sem `grails-app/` (rotas do `grox-dsl`) e nos controllers do Grails, e nos dois as **TagLibs** funcionam: as nativas (`g:`), as dos plugins e as da aplicação.

São extensões do Grox: o Grails não tem views em Pug nem em Markdown.

## Instalação

Basta a dependência; o módulo se registra sozinho:

```groovy
dependencies {
    implementation project(':framework:grox-pug')        // views .pug
    implementation project(':framework:grox-markdown')   // views .md e a tag <md:render>
}
```

## Como a view é escolhida

`render view: 'livros/index'` procura, nesta ordem, `livros/index.gsp`, `livros/index.pug` e `livros/index.md`, nos mesmos lugares das GSPs (o diretório de views em desenvolvimento e `views/` no classpath). O mesmo vale para `<g:render template="card"/>` (`_card.gsp`, `_card.pug`, `_card.md`), para a página de login (`login/auth`) e para o `groovyPageRenderer`. A saída HTML passa pelos [layouts SiteMesh](layouts.md) como a de uma GSP.

## Pug

```pug
//- views/catalogo/index.pug
mixin capa(livro)
  li.livro
    g:link(controller='livros' action='show' id=livro.id) #{livro.titulo}
    fmt:autor(livro=livro)

html
  head
    meta(name='layout' content='main')
    title Catálogo
  body
    h1 Catálogo (#{livros.size()} livros)
    ul
      each l in livros
        +capa(l)
    g:form(action='save')
      g:textField(name='titulo' value=novo?.titulo)
      button Salvar
```

### Expressões em Groovy

As expressões são **Groovy**, como na GSP (o pug4j usa JEXL por padrão; o Grox troca o avaliador):

| Pug | O que faz |
|---|---|
| `#{livro.titulo}` / `p= livro.titulo` | Valor escapado |
| `!{html}` / `p!= html` | Valor sem escape (só para HTML confiável) |
| `- var total = livros*.preco.sum()` | Variável no escopo atual |
| `if livros` / `unless`, `each l in livros`, `each v, k in mapa`, `while` | Verdade do Groovy (lista vazia, `0`, `''` e `null` são falsos) |
| `a(href=g.createLink(action: 'show', id: l.id))` | Atributo com expressão |
| `p #{message(code: 'livro.label')}` | Tags do `g:` como método, como na GSP: a saída da tag já vem escapada e não é escapada de novo |

- Uma variável que não existe é `null` (sem exceção), e `?.`, `?:`, closures (`livros.findAll { it.ano > 1900 }`), GStrings e listas e mapas do Groovy (`[a: 1]`) funcionam.
- Visíveis em toda view: o modelo, `params`, `session`, `flash`, `request`, `response`, `controllerName`, `actionName`, `locale` e os namespaces de tags (`g`, `fmt`...).
- Strings entre aspas num atributo são constantes. Para montar um valor, use uma expressão: `a(href='/livros/' + l.id)`.
- Sintaxe só de JavaScript (`items.forEach(...)`, `` `template ${x}` ``, objetos `{a: 1}`) não vale: use o equivalente em Groovy.

### TagLibs como elementos

Um elemento `ns:tag` cujo namespace e tag existem vira a chamada da tag. Os atributos (e o `&attributes(mapa)`) são o `attrs`, e o bloco aninhado é o `body`:

```pug
g:link(controller='livros' action='index') Livros
g:form(action='save')
  g:textField(name='titulo')
sec:ifLoggedIn
  p Olá, #{sec.username()}
fmt:destaque
  | Mais vendido: #{maisVendido.titulo}
```

- A tag decide se e quantas vezes renderiza o corpo. O argumento de `body(x)` fica visível no bloco como `it` (ou como variáveis, se for um `Map`), como na GSP.
- A saída da tag não é escapada (ela já gera HTML).
- `g:if`, `g:each` e `g:set` são sintaxe da GSP, não tags: no Pug, use `if`, `each` e `- var`.
- Um elemento com `:` que não é tag registrada continua sendo um elemento (`svg:rect`).

### Layouts, includes e filtros

- Layout SiteMesh, como numa GSP: `meta(name='layout' content='main')`, o `static layout` do controller ou o layout padrão.
- Herança do próprio Pug: `extends /layouts/base` com `block conteudo`, e `include _menu`, com caminhos a partir da raiz das views (`/layouts/base`) ou relativos ao template.
- Mixins (`mixin capa(livro)` e `+capa(l)`).
- Filtro `:markdown`, com o `grox-markdown` no classpath (o HTML cru do texto é escapado).

## Markdown

O arquivo `.md` é uma **GSP cuja saída é Markdown**: `${}`, `<g:link>`, `<fmt:autor>` e o resto funcionam, e o resultado é convertido em HTML.

```markdown
---
title: Ajuda
layout: main
descricao: Como usar o catálogo
---
# Ajuda

O catálogo tem **${total} livros**. Veja a <g:link controller="livros">lista</g:link>.

| Página | Formato |
|---|---|
| /livros | GSP |
| /ajuda | Markdown |
```

- **Front matter** (opcional, YAML entre `---`): `title` vira o `<title>` da página, `layout` escolhe o layout SiteMesh e as demais chaves viram `<meta name="chave">`, lidas no layout com `<g:pageProperty name="meta.descricao"/>`.
- Extensões: tabelas e tachado do GFM, links automáticos e âncoras nos títulos (`# Ajuda` → `<h1 id="ajuda">`).
- `${}` é escapado em HTML, como na GSP, e também na sintaxe do Markdown: um dado com `[x](url)` ou `![](url)` aparece como texto, e não vira link nem imagem. Uma URL solta no dado ainda vira link pelo autolink (com o endereço visível e `rel="nofollow"`). HTML escrito no próprio `.md` passa como está.
- Links `javascript:` e afins são neutralizados, e os links recebem `rel="nofollow"`.
- Templates (`_nome.md`) saem sem o documento em volta, para `<g:render template>`.
- Cuidado: como o arquivo é uma GSP, um `${...}` dentro de um bloco de código também é avaliado.

### Markdown de conteúdo: `<md:render>`

Para texto vindo do usuário ou do banco, em qualquer view:

```html
<md:render text="${post.corpo}"/>
<md:render trusted="true">**texto** fixo com <u>HTML</u></md:render>
```

O HTML cru do texto é escapado, a menos que `trusted="true"`. Em código: `org.grox.markdown.Markdown.toHtml(texto)`.

## Recarga e cache

Em desenvolvimento (`grox.gsp.reload`), as views `.pug` e `.md` são relidas a cada mudança. Fora dele, os templates Pug, as expressões compiladas e os `.md` ficam em cache. Veja `grox.pug.*` e `grox.markdown.*` na [configuração](../referencia/configuracao.md#views-e-estáticos).

## Veja também

- [Views GSP](gsp.md)
- [TagLibs](taglibs.md)
- [Layouts (SiteMesh 3)](layouts.md)
- [Views e HTML sem `grails-app/`](../sem-grails/views.md)
