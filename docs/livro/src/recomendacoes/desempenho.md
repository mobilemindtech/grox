# Desempenho

Este capítulo reúne o que fazer, e o que evitar, para tirar proveito do desempenho do Grox na sua aplicação. A regra de ouro é a mesma do framework: **meça antes de otimizar**.

## Grox x Grails em números

A mesma aplicação (o mesmo `grails-app/`) medida no Grails 8.0.0, no Grox com o módulo Grails (compat) e reescrita no `grox-dsl` (puro), na mesma máquina, com H2 em memória e segurança ligada:

| | Grails 8 | Grox compat | Grox puro |
|---|---:|---:|---:|
| Texto, 64 conexões | 1× | ~3,4× | ~3,2× |
| Página GSP com layout, 64 conexões | 1× | ~2,7× a 4,6× | ~2,6× a 4,3× |
| JSON, 64 conexões | 1× | ~1,3× | ~1,4× |
| Página GSP, latência com 1 conexão | 1,5 a 2,1 ms | 0,7 ms | 0,7 ms |
| Texto, latência com 1 conexão | 0,21 ms | 0,31 ms | 0,3 ms |
| JSON, latência com 1 conexão | 0,38 ms | 0,45 a 0,48 ms | 0,36 ms |
| Subida | 17 a 19 s | 8 a 9 s | 8 a 8,5 s |

Medido em 2026-11-27, com o JDK 27. O ganho maior está nas páginas GSP e sob carga. Numa requisição isolada e trivial (texto, JSON), o Grails ainda responde um pouco mais rápido, porque cada requisição no Grox troca de thread pelo menos duas vezes. A página de login do piloto app A, medida no mesmo MySQL e Redis, deu de 2× a 2,4× com 64 conexões. Método, números completos e o que ainda está em aberto: `docs/benchmark.md` no repositório; cada rodada, com a comparação contra a versão anterior, fica em `docs/benchmark-historico.md`.

## Concorrência dos controllers

| Escolha | Quando |
|---|---|
| `concurrency: 'pool'` (padrão) | A maioria dos controllers. Instâncias reaproveitadas, sem custo de criação por requisição |
| `concurrency: 'request'` | Controllers com estado de instância por requisição, ou que você quer isolar. Cada requisição cria ator, instância e faz a injeção |
| `poolSize` maior | Controllers cujas actions esperam muito (JDBC remoto, serviços externos). Com `poolSize` atores, só `poolSize` requisições daquele controller andam ao mesmo tempo |

O padrão é 2 × CPUs por controller. Em medições numa máquina compartilhada, o efeito de mudar o `poolSize` ficou dentro do ruído; meça com o seu banco e a sua carga antes de mexer. Com banco remoto, a espera do JDBC prende o ator, e um pool maior costuma ajudar.

### Actions assíncronas para esperas longas

Uma action síncrona que bloqueia uma virtual thread é barata, mas segura aquele ator. Para chamadas lentas (APIs externas, relatórios, várias consultas independentes), devolva uma promise:

```groovy
import static grails.async.Promises.*

def painel() {
    tasks(usuarios: { Usuario.count() }, pedidos: { Pedido.count() })   // em paralelo
        .then { Map m -> respond m }
}
```

O ator fica livre na hora. Cada tarefa tem a própria sessão de dados: carregue os dados dentro dela.

### Services-ator só para estado

`static actor = true` serializa todas as chamadas do service num único ator. É ótimo para estado interno sem sincronização (contadores, caches com invalidação, filas), e ruim para throughput: vira um gargalo se o service for chamado em toda requisição. Services sem estado devem continuar singletons comuns. Evite ciclos entre services-ator (A → B → A trava).

### `grox-dsl` para rotas quentes

Uma rota do `grox-dsl` pula interceptors, binding dos argumentos e negociação de formato. Desde que o caminho dos controllers passou a ser compilado estaticamente, a diferença diminuiu: na medição de 2026-11-27, texto e GSP empataram com a action equivalente, e no JSON a rota da DSL respondeu em 0,36 ms contra 0,45 a 0,48 ms. Para endpoints de altíssimo volume que devolvem JSON (health de negócio, APIs simples), considere uma rota da DSL. Virtual thread e ator empatam em texto e JSON; em páginas GSP, um pool de atores limita a concorrência de renderização e rendeu cerca de 15% mais.

## Formulários em páginas públicas

Com o CSRF ativo (o padrão), o `<g:form>` de uma página aberta a anônimos põe o token num cookie (`GROX-CSRF`), não na sessão: visitantes, robôs e monitores não criam sessão só por ver a página. Ler `flash.message` no layout também não cria. Até a versão de 2026-11-27, cada requisição anônima a uma página com formulário criava uma sessão (57 mil em 1 minuto de carga numa medição; no Redis, uma escrita por requisição).

O que ainda cria sessão para um anônimo: escrever no flash ou na sessão (`session.x = ...`), um login com falha (a mensagem vai no flash) e o acesso a uma página protegida (a URL fica guardada para depois do login). Com muito tráfego anônimo nessas rotas, prefira `grox.session.store: redis` e um `grox.session.timeout` menor.

## Banco de dados

- **N+1:** o Grox já usa `hibernate.default_batch_fetch_size: 16`: uma lista com associações lazy faz 2 consultas em vez de N+1. Para telas com muitas associações, aumente; para voltar ao comportamento do Hibernate, use `1`.
- **Proxies:** `grox.data.proxies: auto` (padrão) gera proxy lazy só para entidades alvo de many-to-one, o que encurta a subida. Só use `all` se o código depender de `load(id)` devolver proxy.
- **Sessão preguiçosa:** a sessão Hibernate de uma action só abre no primeiro acesso ao banco. Actions sem banco não pagam a abertura nem a resolução de tenant. Não toque no GORM sem necessidade em filtros e interceptors que rodam em toda requisição.
- **Projeções em vez de entidades:** para listas grandes e relatórios, use `projections { }` ou HQL com `select` das colunas necessárias.
- **`unique` custa uma consulta:** `validate()` com constraint `unique` consulta o banco. Command objects e domains como argumento de action já chamam `validate()`.
- **Pool do Hikari:** ajuste `dataSource.properties.maximumPoolSize` ao número de requisições que tocam o banco ao mesmo tempo. A conexão fica presa durante a renderização da view (open session in view).
- **SQL na mão:** `dataSource.logSql: true` em desenvolvimento mostra o SQL gerado; procure consultas repetidas.

## Views

- **GSPs pré-compiladas:** o plugin `org.grox.app` pré-compila as views no build (`compileGsp`). Em produção, a primeira requisição não paga a compilação, e um erro de GSP aparece no build.
- **Assets com digest:** `assetCompile` gera nomes com MD5, servidos com `Cache-Control: public, max-age=31536000, immutable` e `ETag`. Use sempre as tags `asset:` (que apontam para o nome com digest), não caminhos fixos.
- **Estáticos e assets fora da cadeia de segurança:** `/assets/**` nunca passa pela segurança, e `/static/**` em GET/HEAD também não quando a regra libera anônimos. Para outros caminhos públicos de arquivos, use `filterChain.chainMap` com `filters: 'none'`.
- **TagLibs:** tags são chamadas muitas vezes por página. Evite consultas ao banco dentro de tags usadas em loops; passe os dados pelo modelo.

## Sessão e segurança

- **Sessão no Redis:** a gravação é diferencial (só o que mudou, numa ida ao Redis por requisição). Não guarde objetos grandes na sessão, e não guarde entidades: guarde o id.
- **Tokens GORM:** o usuário de cada token fica em cache por `rest.token.storage.gorm.cacheSeconds` (padrão 30). Não desligue (`0`) sem motivo: sem o cache, cada requisição com token faz consultas.
- **JWT:** validar um JWT não consulta o banco. É a opção mais barata para APIs.
- **Regras de URL:** o `interceptUrlMap` é avaliado em ordem, regra a regra. Listas muito longas (centenas de regras) pesam em toda requisição: prefira padrões mais gerais e `@Secured` nas actions.

## Subida

- **Desenvolvimento:** `gradle run` usa JIT só C1 (`grox { fastDevStartup = true }`), de 25% a 40% mais rápido. O Hibernate sobe em paralelo com o resto (`grox.startup.parallel`).
- **Produção:** `./gradlew installDist aotCache` gera o AOT cache do JDK. No sample-app, a subida caiu de 12 s para 5 a 8 s.
- **Memória:** o Grox usa cerca de 2/3 da memória do Grails na mesma app. Heap, limites em contêiner e GC estão em [JVM e memória](implantacao.md#jvm-e-memória).
- O log mostra o tempo de cada fase (`Subida por fase`); os mesmos valores ficam em `groxRuntime.startupPhases`.

## Código da aplicação

- **Groovy dinâmico é o padrão**, como no Grails, e continua sendo a escolha certa para controllers, TagLibs e GSPs (que dependem de `params`, `render` e propriedades dinâmicas).
- **`@CompileStatic` em código quente sem dinamismo** (cálculos, parsers, utilitários em `src/main/groovy`) pode render muito: no framework, só isso deu +150% na página GSP. Não use em classes que dependem de `methodMissing`, dynamic finders ou da API injetada pela AST.
- **Exceções como fluxo de controle** custam caro no Groovy. Não lance exceções em toda requisição para sinalizar casos comuns.
- **Métodos `private` chamados de closures** em classes que têm subclasses passam por `MissingMethodException` a cada chamada: use `protected`.

## Como medir

O repositório traz o gerador de carga `bench/` e o script `bench/run.sh` (veja `docs/benchmark.md` no repositório):

```bash
./gradlew :minha-app:installDist :bench:installDist
bench/build/install/bench/bin/bench http://localhost:8080/rota 64 30 15 "Authorization: Bearer ..."
#                                    URL                      conexões segundos aquecimento cabeçalho
```

- Meça a aplicação **empacotada** (`installDist`), nunca o `run` (GSP recompilável, JIT só C1).
- Aqueça o JIT (15 a 30 s) antes de medir.
- Compare versões em rodadas A/B alternadas, na mesma máquina e sem outras cargas. Variações de ±15% numa máquina ocupada são ruído.
- Para páginas com login, mande o cookie de uma sessão (`"Cookie: GROXSESSIONID=..."`), não Basic: o Basic faz bcrypt a cada requisição e domina a medição. No repositório do Grox, o `bench/run-logado.sh` faz o login e mostra também o tempo de CPU do processo por requisição, que outros processos na máquina afetam menos que a vazão.
- Não use o `HttpClient` do JDK como gerador: ele vira o gargalo.

Para achar onde está o tempo, use o JFR:

```bash
jcmd <pid> JFR.start name=p settings=profile filename=/tmp/p.jfr duration=30s
jfr summary /tmp/p.jfr | grep -E 'ExceptionThrow|ExecutionSample'
jfr print --events jdk.JavaExceptionThrow --stack-depth 20 /tmp/p.jfr
jfr print --events jdk.ExecutionSample --stack-depth 200 /tmp/p.jfr
```

O esperado é quase nenhum `jdk.JavaExceptionThrow` por requisição. Se houver muitos, é o primeiro lugar para olhar.

## Veja também

- [Concorrência: atores e virtual threads](../recursos/concorrencia.md)
- [Actions assíncronas](../recursos/async.md)
- [Implantação em produção](implantacao.md)
- [Configuração](../referencia/configuracao.md)
