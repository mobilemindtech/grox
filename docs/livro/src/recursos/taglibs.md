# TagLibs

Este capítulo mostra como escrever as suas tags GSP: TagLibs com closures ou métodos, namespaces, tags que devolvem valores, o corpo da tag, a API disponível e como chamar tags fora das views.

## Uma TagLib

```groovy
// grails-app/taglib/sample/FormatacaoTagLib.groovy
class FormatacaoTagLib {
    static namespace = 'fmt'               // padrão: g
    static returnObjectForTags = ['total'] // chamada como método devolve o objeto, não o HTML

    def badge = { attrs, body -> out << '<span class="badge">' << body() << '</span>' }
    def idade = { attrs -> out << g.message(code: 'usuario.anos', args: [attrs.valor]) }
    def total = { attrs -> attrs.itens.sum() }
}
```

Uso na GSP: `<fmt:badge>#1</fmt:badge>`, `<fmt:idade valor="${30}"/>` ou `${fmt.badge { '#1' }}`.

## Tags como métodos

Como no Grails 3+, uma tag também pode ser um **método** público, e os dois estilos podem ser misturados:

```groovy
class FormatacaoTagLib {
    static namespace = 'fmt'

    def badge(Map attrs, Closure body) { out << '<span class="badge">' << body() << '</span>' }
    def total(Map attrs) { attrs.itens.sum() }
}
```

- A assinatura é `(Map attrs)` ou `(Map attrs, body)`, com o corpo `Closure` ou sem tipo. Com as duas sobrecargas, vale a que recebe o corpo.
- **Não** viram tag: métodos estáticos e privados, os de outra assinatura (auxiliares), getters e setters gerados e os do trait da TagLib.
- Uma subclasse pode sobrescrever o método de tag da superclasse.

## Atributos e corpo

```groovy
def lista = { attrs, body ->
    int max = attrs.int('max') ?: 10            // também attrs.boolean('x')
    attrs.itens.take(max).each { item ->
        out << '<li>' << body(item) << '</li>'  // body(item) expõe it
    }
}
def linha = { attrs, body ->
    out << body([nome: attrs.nome, idx: 1])      // body(Map) expõe variáveis
}
```

- `body()` devolve o corpo renderizado.
- `body(item)` expõe `it`; `body([nome: v])` expõe variáveis.
- Um atributo `attr="${obj}"` chega como o **objeto**; atributos mistos chegam como String.

## Codificação

A saída das tags **não** é codificada. Codifique os valores vindos do usuário:

```groovy
def nome = { attrs -> out << '<b>' << attrs.valor.encodeAsHTML() << '</b>' }
```

`org.grox.http.Html.escape(...)` e `Html.attributes(map)` também servem. A saída de uma tag é `SafeHtml`: na GSP, `${fmt.nome(valor: x)}` não é escapada de novo.

## API disponível

Dentro da TagLib: `out`, `params`, `request`, `session`, `flash`, `pageScope`, `controllerName`, `actionName`, `grailsApplication`, `grailsAttributes`, `log`, `g.<tag>(...)` e `<tag>(...)` direto para tags `g:`. Services são injetados por nome. TagLibs são singletons: não guarde estado em campos.

## Namespaces como propriedades

Qualquer namespace é uma propriedade, nas TagLibs e nos controllers, como no Grails:

```groovy
class PedidoController {
    def show(Long id) {
        if (!auth.isAdmin()) return render(status: 403)     // tag de outra TagLib
        render text: fmt.idade(valor: 30)                   // HTML da tag
    }
}
```

Com `returnObjectForTags`, a chamada como método devolve o objeto em vez do HTML. Tags `g:` também são chamáveis direto nas actions (`formatDate(date: d, format: 'dd/MM/yyyy')`).

## Tags de outros módulos

As tags `sec:` vêm do plugin spring-security e as `asset:` do asset-pipeline, registradas depois das nativas e antes das da aplicação. A lista completa está em [Tags GSP](../referencia/tags.md).

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| `defaultEncodeAs`, `encodeAsForTags` | Ausentes: codifique na tag |

## Veja também

- [Views GSP](gsp.md)
- [Tags GSP](../referencia/tags.md)
- [Controllers](controllers.md)
