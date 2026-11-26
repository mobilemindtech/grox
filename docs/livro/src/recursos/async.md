# Actions assíncronas

Este capítulo mostra como uma action devolve uma promise para liberar o ator do controller durante esperas longas, usando a API `grails.async` (`task`, `tasks`, `Promise`), e o que acontece com o contexto, a sessão de dados, os erros e o timeout.

## Por que usar

Uma action síncrona que bloqueia uma virtual thread é barata, mas **segura aquele ator**: num controller em modo `pool` com `poolSize` atores, só `poolSize` requisições daquele controller andam ao mesmo tempo. Com uma promise, o ator fica livre na hora e atende a próxima requisição; a resposta sai quando a promise completa.

Use em esperas longas: serviços externos, processamento pesado, várias consultas em paralelo.

## Exemplos

```groovy
import static grails.async.Promises.*

class PainelController {
    def relatorio() {
        task { [total: Usuario.count()] }                 // Map → view relatorio.gsp, como uma action normal
    }

    def painel() {
        tasks(usuarios: { Usuario.count() }, tarefas: { Tarefa.count() })   // em paralelo
            .then { Map m -> respond m }
    }

    def importar() {
        task { servicoLento.processar(params.id); render text: 'ok' }
    }

    def externo() {
        java.util.concurrent.CompletableFuture.supplyAsync { cliente.buscar() }
            .thenApply { dados -> [dados: dados] }
    }
}
```

## O que a action pode devolver

- `grails.async.Promise` (de `task`/`tasks`, ou `task { }` direto no controller);
- `CompletableFuture` ou qualquer `CompletionStage`;
- o `Awaitable` do Groovy 6.

O valor final segue as mesmas regras de uma action síncrona: um `Map` vira o modelo da view padrão, e `render`/`respond`/`redirect` dentro da tarefa escrevem a resposta.

## Contexto das tarefas

- As tarefas rodam em **virtual threads**, com o contexto de quem as criou: `params`, `request`, usuário autenticado e tenant.
- **Cada tarefa tem a própria sessão de dados**, aberta sob demanda: a Session do Hibernate não é thread-safe, e a action e a tarefa podem rodar ao mesmo tempo.
- Entidades carregadas na action ficam **desanexadas** na tarefa. Carregue os dados dentro dela (passe ids, não entidades).

## Quando a promise completa

O fluxo normal continua: a view adiada ou padrão, os `after()`/`afterView()` dos interceptors e o layout SiteMesh.

## Erros e timeout

- Um erro na tarefa gera `500`, pelo mapeamento `"500"` do UrlMappings, se houver.
- Passado `grox.async.timeout` (padrão 60 s), a resposta é `503`.

```yaml
grox:
  async:
    timeout: 120
```

## API

| `Promises` | |
|---|---|
| `task { }` | Uma tarefa |
| `tasks(Closure...)`, `tasks(List)` | Várias em paralelo; o resultado é a lista |
| `tasks(Map)` | Várias em paralelo; o resultado é um mapa com as mesmas chaves |
| `waitAll(promises)` | Espera todas |
| `onComplete(promises) { resultados -> }` | Callback ao completar |
| `createBoundPromise(valor)`, `createPromise()` | Promises prontas ou a completar |

| `Promise` | |
|---|---|
| `get()`, `get(timeout, unit)` | Espera o resultado |
| `then { valor -> }` | Encadeia |
| `onComplete { }`, `onError { }` | Callbacks |
| `isDone()` | Já completou |

Fora dos controllers (services, jobs), `Promises.task { }` é a forma recomendada de rodar algo em paralelo com o contexto propagado.

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| `startAsync()` da Servlet API | Ausente |
| Streaming e Server-Sent Events | Ausentes (no roadmap) |
| Promises do GPars | Tarefas em virtual threads; GPars mínimo (`GParsPool.withPool`) sobre o `groovy.concurrent` |

## Veja também

- [Concorrência: atores e virtual threads](concorrencia.md)
- [Sessões, transações e eventos](transacoes.md)
- [Interceptors](interceptors.md)
