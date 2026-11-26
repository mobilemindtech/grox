# Interceptors

Este capítulo cobre os interceptors no estilo do Grails 3+: classes que rodam antes e depois das actions, como escolher o que interceptam, a ordem de execução e como alterar o modelo e a view.

## Declarando

Classes `*Interceptor` em `grails-app/controllers`:

```groovy
class AuditoriaInterceptor {
    int order = HIGHEST_PRECEDENCE + 10         // menor roda primeiro no before (e por último no after)

    AuditoriaInterceptor() {
        matchAll().excludes(controller: 'erro')
        // match(controller: 'livro', action: 'show'), match(controller: ~/(autor|editora)/),
        // match(uri: '/api/**'), match(method: 'POST')
    }

    boolean before() {
        log.info "${controllerName}.${actionName} por ${request.userPrincipal?.name}"
        true                                     // false interrompe (renderize/redirecione antes)
    }

    boolean after() {
        model.extra = 1                          // false impede a view; model e view podem ser alterados
        true
    }

    void afterView() {
        if (throwable) log.error 'falhou', throwable
    }
}
```

## O que é interceptado

| Declaração | Casa com |
|---|---|
| Sem `match(...)` | O controller de mesmo nome (`LivroInterceptor` → `livro`) |
| `matchAll()` | Todas as requisições de controllers |
| `match(controller: 'livro', action: 'show')` | Controller e action exatos |
| `match(controller: ~/(autor\|editora)/)` | Regex (pré-compilada) |
| `match(uri: '/api/**')` | Padrão Ant no caminho |
| `match(method: 'POST')` | Método HTTP |
| `.excludes(controller: 'erro')` | Exclui do conjunto |

## Ciclo

1. **`before()`**: antes do binding e da action. `false` interrompe; renderize ou redirecione antes de devolver `false`.
2. A action roda.
3. **`after()`**: depois da action e **antes** da view. `model` e `view` podem ser alterados; `false` impede a renderização.
4. A view é renderizada (com layout).
5. **`afterView()`**: depois da renderização. `throwable` traz a exceção da requisição, se houver.

Isso funciona porque, dentro de uma action, `render(view:)`, `respond` (HTML) e a view padrão são **adiados** até depois dos `after()`. Templates e `render text:` saem na hora.

No `after()`, também existe `modelAndView`: `modelAndView.model` e `modelAndView.viewName` (`null` quando a action não tem view).

## Ordem

`order` define a precedência: o menor valor roda primeiro no `before()` e por último no `after()`/`afterView()`. As constantes `HIGHEST_PRECEDENCE` e `LOWEST_PRECEDENCE` estão disponíveis. Com a mesma `order`, vale a ordem alfabética do nome completo da classe *(no Grails, a ordem nesse caso não é definida)*.

## API disponível

`params`, `request`, `response`, `session`, `flash`, `controllerName`, `actionName`, `model`, `view`, `modelAndView`, `throwable`, `render`, `redirect`, `header`, `log` e injeção de services por nome.

## Onde rodam

Depois da segurança (uma requisição negada não chega aos interceptors) e **dentro do ator do controller**. Em actions assíncronas, `after()` e `afterView()` rodam quando a promise completa.

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| `beforeInterceptor`/`afterInterceptor` (Grails 2) | Ausentes |
| Interceptors nas rotas do `grox-dsl` | Ainda não existem |

## Veja também

- [Controllers](controllers.md)
- [Actions assíncronas](async.md)
- [Segurança: regras de acesso](seguranca-regras.md)
