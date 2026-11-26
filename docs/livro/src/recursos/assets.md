# Assets e estáticos

Este capítulo cobre o asset pipeline do Grox, que segue as convenções do asset-pipeline do Grails (pastas, diretivas, tags, digest), e os arquivos estáticos servidos em `/static/**`.

## Asset pipeline

```
grails-app/assets/
  javascripts/application.js     //= require util   //= require_tree modulos   //= require_self
  stylesheets/application.css    /* *= require base */   url('../images/logo.png')
  images/logo.png
```

```html
<asset:stylesheet href="application.css"/>
<asset:javascript src="application.js" defer="${true}"/>
<asset:image src="logo.png" alt="Logo"/>
<asset:link rel="icon" href="favicon.ico" type="image/x-icon"/>
<div style="background: url(${assetPath(src: 'logo.png')})"></div>
<g:if test="${assetPathExists(src: 'tema.css')}">...</g:if>
```

### Raízes e caminhos

Cada subpasta de `grails-app/assets` é uma raiz (`javascripts`, `stylesheets`, `images` e outras que você criar). O caminho lógico é relativo à raiz: `application.js`, `lib/util.js`, `logo.png`.

### Diretivas

| Diretiva | Efeito |
|---|---|
| `require x` | Inclui o arquivo (extensão opcional; busca primeiro relativo ao arquivo, depois nas raízes) |
| `require_tree pasta` | Todos os arquivos da pasta, em ordem |
| `require_self` | Posição do próprio conteúdo (o padrão é depois dos requires) |

Cada arquivo entra uma vez só no bundle. Um `require` inexistente gera aviso, como no asset-pipeline.

### CSS

`url(...)` relativos são reescritos para a URL do asset, inclusive `../images/x.png` e o nome solto `x.png`. URLs absolutas, `data:` e `http(s):` ficam como estão.

### Tags

| Tag | Uso |
|---|---|
| `asset:javascript src=` | `<script>` (atributos extras, como `defer`, vão para a tag) |
| `asset:stylesheet href=` | `<link rel="stylesheet">` |
| `asset:image src=` | `<img>` |
| `asset:link href=` | `<link>` genérico |
| `asset:script` / `asset:deferredScripts` | Script inline adiado e o ponto onde os adiados são escritos |
| `assetPath(src:)` | URL do asset (também no namespace `g` e sem prefixo) |
| `assetPathExists(src:)` | Se o asset existe |

No código, o bean `assetResourceLocator` lê um asset: `assetResourceLocator.findAssetForURI('logo.png')?.inputStream`.

### Desenvolvimento

Com `grails-app/assets` e reload ligado, `/assets/**` é processado na hora, com `Cache-Control: no-cache`, e reprocessado quando qualquer arquivo da cadeia de `require` muda.

### Build e produção

A tarefa `assetCompile` do plugin `org.grox.app` gera `nome.ext` e `nome-<md5>.ext` em `META-INF/assets`, mais o `manifest.properties`.

Em produção:

- as tags apontam para os nomes com digest, servidos com `Cache-Control: public, max-age=31536000, immutable` e `ETag` (com resposta 304);
- nomes lógicos também respondem, com cache curto (5 min).

### Segurança

`/assets/**` nunca passa pela cadeia de segurança nem lê a sessão, como no Grails (o filtro do asset-pipeline roda antes do Spring Security). Com `rejectIfNoRule`, libere `/assets/**` no `interceptUrlMap` mesmo assim, para deixar a regra explícita.

### Configuração

| Chave | Padrão | |
|---|---|---|
| `grails.assets.mapping` | `assets` | Prefixo da URL |
| `grox.assets.dir` | `grails-app/assets` | Diretório de desenvolvimento |

## Estáticos (`/static/**`)

`src/main/resources/public/**` é servido em `/static/**`:

- em desenvolvimento, os arquivos são lidos direto do diretório de fontes, sem cache;
- nos demais casos, vêm do classpath, com `Last-Modified`, `304` e `Cache-Control`.

`<g:resource dir="css" file="x.css"/>` gera `/static/css/x.css`.

| Chave | Padrão |
|---|---|
| `grox.static.prefix` | `/static` |
| `grox.static.dir` | `src/main/resources/public` |

Estáticos em GET/HEAD cuja regra libera anônimos (por exemplo `permitAll`) também pulam a segurança e a sessão. Um estático protegido por regra continua passando pela segurança. Symlinks que apontam para fora do diretório não são servidos.

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| asset-pipeline-core | Implementação própria: o 5.0.9 não roda no Groovy 6, e o 5.2.0 (RC) exige GraalVM |
| Babel/ES6, SASS, LESS, CoffeeScript | Ausentes |
| Minificação, gzip, `bundle=false` | Ausentes (minificação e gzip no roadmap) |

## Veja também

- [Layouts (SiteMesh 3)](layouts.md)
- [Plugins e tarefas Gradle](../referencia/gradle.md)
- [Implantação em produção](../recomendacoes/implantacao.md)
