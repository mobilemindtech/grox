# Views GSP

Este capítulo descreve o motor GSP do Grox: onde ficam as views, a sintaxe, a codificação HTML, as variáveis implícitas, os templates, a pré-compilação, o reload em desenvolvimento, os erros com linha e os codecs. O motor é próprio (o GSP do Grails depende de Spring e do Groovy 5), compatível com a sintaxe do Grails.

## Onde ficam

Em `grails-app/views` (no classpath, `views/`):

- `<controller>/<action>.gsp` é a view padrão de uma action;
- `_nome.gsp` é um template;
- `layouts/*.gsp` são os layouts (veja [Layouts](layouts.md)).

Nas apps sem `grails-app/`, as views ficam em `src/main/resources/views/`.

## Sintaxe

| Construção | Efeito |
|---|---|
| `${expr}`, `<%= expr %>` | Imprime o valor **codificado em HTML**. `null` imprime vazio |
| `${raw(html)}`, `${x.encodeAsRaw()}` | Imprime sem codificar |
| `<% codigo %>` | Scriptlet Groovy |
| `<%@ page import="a.B; c.D" contentType="text/plain" %>` | Diretiva |
| `%{-- comentário --}%`, `<%-- comentário --%>` | Removidos da saída |
| `<g:tag attr="literal" outro="${objeto}" misto="a ${b}">corpo</g:tag>` | Tag. Um atributo que é só `${...}` recebe o **objeto**; atributos mistos viram String |
| `${g.link(action: 'x') { 'corpo' }}`, `${createLink(action: 'x')}`, `${fmt.moeda(valor: v)}` | Tags chamadas como métodos |

```html
<%@ page import="sample.Status" %>
<h1>${livro.titulo}</h1>                         <%-- escapado --%>
<g:if test="${livro.status == Status.ATIVO}">
  <p>${raw(livro.descricaoHtml)}</p>
</g:if>
<g:each in="${livro.autores}" var="a" status="i">
  <span class="${i % 2 ? 'par' : 'impar'}">${a.nome}</span>
</g:each>
<g:set var="total" value="${livros.size()}"/>
<p>${total} livros</p>
```

Atributos com literal de lista ou mapa são avaliados: `noSelection="['':'-']"`, `params="[a: 1]"`.

### Tags de controle

`g:if`, `g:elseif`, `g:else`, `g:unless`, `g:while`, `g:each` e `g:set` são compiladas como estruturas de controle Groovy, não como chamadas de tag. `g:if env="development"` testa o ambiente. `g:each` aceita `var="${x}"`, `in="${lista?}"` e `g:each` aninhado sem `var` (com `it`).

`g:collect`, `g:findAll` e `g:grep` não existem.

## Codificação (encoding)

`${expr}` e `<%= %>` são codificados em HTML. **Não** passam pela codificação:

- valores `SafeHtml`: o `raw(x)`, a saída de tags e `encodeAsRaw()`;
- o que `encodeAsHTML()`, `encodeAsJavaScript()` e `encodeAsURL()` devolveram na mesma requisição, como no Grails: `${x.encodeAsHTML()}` sai escapado uma vez só;
- o texto literal da página.

Métodos de `String` funcionam sobre valores `raw()`/codificados. A saída das suas TagLibs **não** é codificada automaticamente: codifique os valores na tag (veja [TagLibs](taglibs.md)).

## Variáveis implícitas

`params`, `request`, `response`, `session`, `flash`, `controllerName`, `actionName`, `grailsApplication`, `webRequest`, `pageScope`, `locale`, `out`. Uma variável desconhecida vale `null` (sem exceção).

## Templates

```html
<g:render template="form" model="[livro: livro]"/>
<g:render template="linha" collection="${livros}" var="l"/>
<g:render template="/shared/menu"/>
```

| Template | Arquivo |
|---|---|
| `template="form"` | `views/<controller>/_form.gsp` |
| `template="/shared/menu"` | `views/shared/_menu.gsp` |
| Fora de um controller | Relativo ao diretório da view atual |

Um template enxerga o modelo da página que o renderizou, como no Grails. Caminhos com `..` funcionam. Templates não são decorados por layout.

Para renderizar fora de uma requisição (e-mail, PDF), use o bean `groovyPageRenderer`: `groovyPageRenderer.render(template: '/email/boasVindas', model: [u: u])`.

## Pré-compilação

O plugin `org.grox.app` adiciona a tarefa `compileGsp`, ligada por padrão (`grox { precompileGsp = true }`):

- um erro de GSP **falha o build**, com arquivo e linha;
- as classes vão para o classpath e para o jar;
- sem reload, o runtime usa as views pré-compiladas: a primeira requisição não paga a compilação, e as fontes não precisam estar em produção.

`./gradlew compileGsp` sozinho valida a sintaxe de todas as views.

## Reload em desenvolvimento

Com `grox.gsp.reload` (ligado por padrão em `development`), as views são lidas de `grails-app/views` (`grox.gsp.viewsDir`) e recompiladas quando o arquivo muda. Isso exige rodar a partir do diretório do projeto, como faz o `gradle run`. Nos demais casos elas vêm do classpath e são compiladas uma vez. O reload das views independe da [recarga de classes](recarga.md) e continua funcionando depois dela.

## Erros

Erros de GSP indicam **arquivo e linha da GSP**, por exemplo `/usuario/show.gsp:13: ...`, tanto na compilação quanto na execução.

O motor tolera, **com aviso no log**, construções que o Grails aceita em silêncio:

- tag sem `>` antes do próximo `<`;
- vírgula entre atributos de tag (`<g:link action="x", id="1">`);
- aspas dentro de atributos.

## Codecs

Disponíveis em qualquer objeto, nas views e no código:

| Codec | Resultado |
|---|---|
| `encodeAsHTML()` | Escape HTML |
| `encodeAsURL()` | Codificação de URL |
| `encodeAsJSON()` | JSON |
| `encodeAsJavaScript()` | Escape para string JavaScript (aspas, `&`, `<` e `>` como `\u0022`...: seguro também dentro de atributo HTML) |
| `encodeAsRaw()` | Marca como seguro (sem escape) |
| `encodeAsMD5()`, `encodeAsSHA1()`, `encodeAsSHA256()` | Digest em hex; `...Bytes()` devolve `byte[]` |
| `encodeAsBase64()`, `encodeAsHex()` | Codificação |

Texto é lido em UTF-8. A configuração de codec padrão (`grails.views.default.codec`) não existe: a codificação HTML é sempre o padrão.

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| GSP do Grails (Spring, Groovy 5) | Motor próprio, mesma sintaxe |
| `g:collect`, `g:findAll`, `g:grep`, `g:remoteLink`, `g:javascript`, `g:img` | Ausentes |
| Codec padrão configurável | Sempre HTML |
| SiteMesh 2 | SiteMesh 3 |
| Erros de sintaxe tolerados em silêncio | Tolerados com aviso |

## Veja também

- [Tags GSP](../referencia/tags.md)
- [TagLibs](taglibs.md)
- [Layouts (SiteMesh 3)](layouts.md)
- [DSL de HTML](dsl-html.md)
- [Internacionalização](i18n.md)
