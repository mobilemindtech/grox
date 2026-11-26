# DSL de HTML

Este capítulo apresenta a alternativa à GSP: montar o HTML com um closure no `render { }`, pelo `MarkupBuilder` do Groovy ajustado para HTML5. Vale nos controllers (como no Grails) e nas rotas do `grox-dsl`.

## Exemplo

```groovy
def index() {
    def livros = Livro.list()
    render(layout: 'main') {                       // ou meta(name: 'layout', content: 'main') no head, ou o layout padrão
        h1 'Livros'
        ul {
            livros.each { l ->
                li { a(href: g.createLink(action: 'show', id: l.id), l.titulo) }
            }
        }
        p g.message(code: 'livros.total', args: [livros.size()])
    }
}
```

O resultado é decorado pelo layout SiteMesh como uma view GSP.

## Regras do HTML gerado

- Elementos vazios do HTML (`br`, `input`, `meta`, `img`...) saem como `<br />`; os demais, mesmo vazios, com a tag de fechamento (`<script src="x"></script>`, `<div></div>`).
- Texto e atributos são **escapados**. Um valor seguro (a saída das tags `g:`, `raw(...)`) entra sem escape duplo.
- Atributos `null` são omitidos.
- Começando por `html { }`, a saída ganha o `<!DOCTYPE html>`.

```groovy
render {
    html {
        head { title 'Página' }
        body {
            input(type: 'checkbox', checked: marcado ? 'checked' : null)   // null: atributo omitido
            p "Total: ${total} & contando"                                 // & escapado
            div raw('<em>já seguro</em>')
        }
    }
}
```

## Tags e nomes

As tags ficam nos namespaces (`g.*`, `sec.*`, os das TagLibs da app). **Um nome solto vira elemento**: `message(...)` gera `<message>`. Use `g.message(...)`, `g.createLink(...)`, `g.formatDate(...)`.

Variáveis do método, `params`, `flash` e os services continuam acessíveis dentro do closure.

## Opções

| Opção | Efeito |
|---|---|
| `layout:` | Layout SiteMesh |
| `model:` | Modelo para o layout |
| `status:` | Status HTTP |
| `contentType:` | `application/json` ou `text/json`: o closure monta JSON (estilo JSONBuilder); XML: `MarkupBuilder` puro; sem ele: HTML |

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| `render { }` com `MarkupBuilder` puro | Ajustado para HTML5 (elementos vazios, escape, `SafeHtml`), com layout |
| Não existe | Também nas rotas do `grox-dsl` |

Antes, o Grox tratava `render { }` sem `contentType` como JSON; hoje é HTML, como no Grails.

## Veja também

- [Views GSP](gsp.md)
- [Layouts (SiteMesh 3)](layouts.md)
- [Views e HTML](../sem-grails/views.md)
