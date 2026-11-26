# Concorrência: atores e virtual threads

Este capítulo explica onde o seu código roda no Grox: o event loop do Netty, os atores dos controllers, os services-ator, as virtual threads e o contexto que acompanha cada execução. É o ponto em que o Grox mais difere do Grails, e vale ler antes de escrever controllers com estado.

## O caminho de uma requisição

```
 Netty event loop                 Atores (virtual threads)
 ─────────────────                ─────────────────────────────────────────
 recebe e decodifica  ──────────► roteamento (só CPU)
                                  segurança (virtual thread)
                                  ator do controller
                                    interceptors → binding → action
                                    service-ator? mensagem ao ator do service
                                    view GSP + layout
 escreve a resposta  ◄──────────  CompletableFuture com a resposta
 (na ordem das requisições da conexão)
```

| Peça | Modelo |
|---|---|
| Netty | Event loops. Nunca bloqueiam: não executam actions, GSP nem IO de arquivo |
| Roteador | Função pura sobre dados imutáveis, fora de ator (um ator único serializaria todo o tráfego) |
| Controller, modo `pool` (padrão) | N atores, cada um com **sua instância** do controller. O despacho escolhe o ator com menos mensagens pendentes, a partir de um índice round-robin |
| Controller, modo `request` | Um ator e uma instância novos por requisição, descartados no fim |
| Service comum | Singleton compartilhado. Precisa ser thread-safe |
| Service com `static actor = true` | Instância atrás de um ator; quem injeta recebe um proxy |
| TagLibs | Singletons. O `out` vem do contexto de renderização |

Os atores rodam em **virtual threads**: bloquear dentro de uma action (JDBC, HTTP externo, chamada a um service-ator) é aceitável e barato.

## Modo `pool` e modo `request`

```yaml
grox:
  controllers:
    concurrency: pool      # padrão da app: pool ou request
    poolSize: 16           # padrão: 2 × CPUs
```

```groovy
class RelatorioController {
    static concurrency = 'request'   // por controller
}
class BuscaController {
    static poolSize = 4
}
```

- **`pool`** reaproveita instâncias. Cada ator processa uma mensagem por vez, então **só `poolSize` requisições daquele controller andam ao mesmo tempo**. Uma action lenta segura o ator; para esperas longas, use [actions assíncronas](async.md), que liberam o ator.
- **`request`** cria um ator e uma instância por requisição: sem limite por controller, com o custo de criar e injetar a instância.
- **Campos de instância** de um controller em `pool` pertencem àquele ator: não são compartilhados entre atores e persistem entre requisições do mesmo ator. Não guarde estado de requisição em campos; use variáveis locais, `request` ou `session`.

## Services-ator

```groovy
class SequenciaService {
    static actor = true
    private long atual
    long proxima() { ++atual }
}
```

- Quem injeta `sequenciaService` recebe um **proxy-subclasse** (a injeção tipada funciona). Cada chamada vira uma mensagem (`sendAndGet`) e bloqueia a virtual thread até a resposta. Exceções chegam para quem chamou.
- O estado dispensa `synchronized`.
- A sessão de dados de quem chama vai junto: como quem chama está bloqueado, a sessão nunca é usada por duas threads ao mesmo tempo.
- **Risco de deadlock:** um service-ator A que chama um service-ator B, que por sua vez chama A de volta, trava. Evite ciclos entre services-ator.
- Ator serializa: use para estado mutável compartilhado, não para trabalho que deveria rodar em paralelo.

## Contexto em `ScopedValue`

O Grox não usa `ThreadLocal`. O estado de cada execução vive em `ScopedValue` (Java 25+):

| Valor | O que guarda | Vai para services-ator? | Vai para tarefas `grails.async`? |
|---|---|---|---|
| Requisição corrente (`RequestContext`) | `params`, `request`, `session`... | não | sim |
| Contexto de renderização GSP | `out`, `pageScope` | não | não |
| Sessão Hibernate (`Persistence`) | sessão de dados da action | **sim** | não: cada tarefa abre a sua |
| Autenticação (`SecurityContextHolder`) | usuário logado | sim | sim |
| Tenant forçado (`Tenants.withId`/`withoutId`) | tenant | sim | sim |

Consequências práticas:

- Uma **thread criada por você** (`new Thread`, `Executors`) não vê requisição, usuário nem tenant. Use `grails.async.Promises.task { }`, que propaga o contexto.
- Fora de uma action (BootStrap, jobs, threads próprias), cada chamada do GORM abre e fecha a própria sessão; agrupe com `withTransaction`.
- `RequestContextHolder` e `SecurityContextHolder` funcionam porque leem esses valores.

## Sessão Hibernate por action

Há uma sessão por invocação de action, **incluindo a renderização da view** (lazy loading funciona na GSP). Ela é aberta sob demanda: actions que não tocam o banco não pagam abertura de sessão nem resolução de tenant. Detalhes em [Sessões, transações e eventos](transacoes.md).

## Rotas do `grox-dsl`

Nas apps sem `grails-app/`, cada rota roda numa virtual thread por requisição (`execution: 'virtual'`, padrão) ou num pool de atores (`execution: 'actor'`), configurável por rota, grupo ou globalmente:

```yaml
grox:
  routes:
    execution: virtual     # ou actor
    poolSize: 4            # atores por grupo no modo actor
```

```groovy
group('/relatorios', execution: 'actor', poolSize: 2) {
    get('/autores') { render json: livroService.porAutor() }
}
```

Num `@Handler` em modo `actor`, cada ator tem a sua instância da classe. Veja [Rotas por closure](../sem-grails/rotas-closure.md).

## Ordem das respostas

O Netty aceita HTTP pipelining (até `grox.http.maxPipelined`, padrão 16, requisições em voo por conexão). As respostas saem **na ordem das requisições** da conexão, mesmo que terminem fora de ordem.

## Diferenças para o Grails

| Grails | Grox |
|---|---|
| Thread por requisição do container | Event loop do Netty + atores em virtual threads |
| Controller singleton | Instância por ator (`pool`) ou por requisição (`request`) |
| `ThreadLocal` (Spring) | `ScopedValue`, propagado a services-ator e tarefas |
| Não existe | Services-ator (`static actor = true`) |

## Veja também

- [Actions assíncronas](async.md)
- [Services e injeção de dependências](services.md)
- [Desempenho](../recomendacoes/desempenho.md)
- [A arquitetura em uma página](../introducao/arquitetura.md)
