# Layouts (SiteMesh 3)

Este capítulo explica a decoração das páginas por layouts: como o layout é escolhido, as tags de layout, propriedades da página com `<content tag>` e o que é ou não decorado. O Grox usa o SiteMesh 3 (o Grails usa o 2), com as mesmas tags do Grails.

## Um layout

```html
<!-- views/layouts/main.gsp -->
<!DOCTYPE html>
<html>
<head>
  <title><g:layoutTitle default="App"/></title>
  <asset:stylesheet href="application.css"/>
  <g:layoutHead/>
</head>
<body>
  <nav><g:pageProperty name="page.menu"/></nav>
  <g:layoutBody/>
  <g:pageProperty name="page.include.bottom"/>
</body>
</html>
```

```html
<!-- views/livro/index.gsp -->
<html>
<head>
  <meta name="layout" content="main"/>
  <title>Livros</title>
</head>
<body onload="iniciar()">
  <content tag="menu"><a href="/">início</a></content>
  <h1>Livros</h1>
</body>
</html>
```

## Escolha do layout

Na ordem:

1. `<meta name="layout" content="main"/>` na página;
2. `static layout = 'admin'` do controller;
3. `layouts/<controller>/<action>.gsp`;
4. `layouts/<controller>.gsp`;
5. a configuração `grails.sitemesh.default.layout`.

```yaml
grails:
  sitemesh:
    default:
      layout: main
```

## Tags

| Tag | Efeito |
|---|---|
| `g:layoutTitle default="x"` | O `<title>` da página |
| `g:layoutHead` | O conteúdo do `<head>` da página (sem o título) |
| `g:layoutBody` | O `<body>` da página |
| `g:pageProperty name="page.x"` | Conteúdo de `<content tag="x">`; também `meta.x` (metas) e `body.onload` (atributos do body) |
| `g:ifPageProperty name="page.x"` | Corpo só se a propriedade existe |
| `g:applyLayout name="x"` / `template=` | Aplica um layout a um trecho |

- Nomes com ponto funcionam: `<content tag="include.bottom">` e `pageProperty(name: 'page.include.bottom')`.
- O `<sitemesh:write property="body"/>` nativo do SiteMesh também funciona no layout.
- Os atributos do `<body>` da página são mesclados com os do layout.

## O que é decorado

Só respostas `text/html` de **views** (GSP ou [DSL de HTML](dsl-html.md) com layout). `render text:`, templates e JSON não são.

A página de login e as views de erro também são decoradas, com sessão de dados aberta (um layout que lê associação lazy funciona).

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| SiteMesh 2 | SiteMesh 3, com as mesmas tags `g:` |
| Não existe | `<sitemesh:write>` também aceito |

## Veja também

- [Views GSP](gsp.md)
- [DSL de HTML](dsl-html.md)
- [Assets e estáticos](assets.md)
