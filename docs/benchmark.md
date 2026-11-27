# Benchmark

## Como rodar

```bash
./gradlew :sample-app:installDist :bench:installDist
CONN=64 SECS=10 bench/run.sh "" /static/css/app.css /actuator/health /ola/x /usuario/index /api/usuarios/1
bench/run.sh "-Dgrox.controllers.poolSize=64" /api/usuarios/1          # comparar configurações
bench/build/install/bench/bin/bench http://host:8080/x 128 30 5 "Authorization: Bearer ..."   # direto
```

- **Grails x Grox:** `bench/run-livros.sh grails|grox|plain` sobe a app de referência numa das três versões e mede texto, GSP e JSON com 64 e com 1 conexão (ver [abaixo](#grails-x-grox-compat-x-grox-puro-2026-10-10)).
- **O que o `run.sh` faz:** sobe o `sample-app` empacotado (views pré-compiladas, `grox.gsp.reload=false`, ambiente development com os dados do BootStrap), obtém um JWT de admin para as rotas `/api/**` e roda o `LoadGen` em cada URL.
- **Páginas com login:** `bench/run-logado.sh rotulo /usuario/index` faz login por formulário e mede com o cookie da sessão (o Basic faria bcrypt a cada requisição); mostra também o tempo de CPU do processo por requisição. Com `DIST=<installDist de outra versão>`, mede uma versão anterior para comparar em A/B.
- **O `LoadGen`** (`bench/`): N conexões HTTP/1.1 keep-alive (`Content-Length` ou `chunked`), uma virtual thread por conexão com socket bloqueante, uma requisição por vez, aquecimento separado da medição e histograma logarítmico de latência (p50, p90, p99, p99.9). Não use o `HttpClient` do JDK como gerador: ele é lento o bastante para virar o gargalo.

## Tempo de subida

A subida registra o tempo de cada fase no log (`Subida por fase (ms): scan=..., datastore (metadados)=..., ... JVM até aqui: ...`). Os mesmos valores ficam em `groxRuntime.startupPhases`.

Medido no piloto app-a (61 domain classes, 57 controllers, 193 GSPs), na mesma máquina carregada (load average de 6 a 10). Por isso os valores oscilam, e só a comparação em pares vale:

| Modo | Subida (JVM até pronto) |
|---|---:|
| Antes (Hibernate serial, proxy em todas as entidades, scan dinâmico) | 21 a 35 s |
| Desenvolvimento: `gradle run` (paralelo + JIT só C1) | 9,6 a 14,8 s |
| Produção: `installDist` + `aotCache` (paralelo + AOT cache do JDK) | cerca de 14,5 s; no sample-app, de 12 s para 5 a 8 s |

O que o profiling (JFR) mostrou e o que foi feito:

| Custo | Ação |
|---|---|
| Hibernate (~10 s) em série com o resto | O `SessionFactory` sobe numa thread própria (`grox-hibernate`). A `main` segue com segurança, services, beans, views e controllers, e só espera antes do BootStrap. `getSessionFactory()` bloqueia até ficar pronto, então nada depende da ordem |
| Proxies ByteBuddy: ~35% do Hibernate, uma classe por entidade, com os ~100 métodos dos traits do Grox | `grox.data.proxies: auto` gera proxy só para as entidades alvo de many-to-one (30 de 61 no piloto) |
| Metadados das domain classes (~3 s): inicialização das classes e das closures `mapping`/`constraints` | Feitos em paralelo, por classe |
| Scan de artefatos com Groovy dinâmico (1,8 s) | `@CompileStatic` em `ClassScanner`, `ClassUtils` e na classificação: 0,2 s |
| Carga e linking de classes (Hibernate, JAXB, Groovy, Netty) | AOT cache do JDK 25+ (tarefa `aotCache`). Em desenvolvimento, JIT só C1 (`grox { fastDevStartup = true }`, o padrão) |

Não atacado: o BootStrap (~2 s) é código da aplicação, e o parse/JAXB do `orm.xml` (~1,5 s) é interno do Hibernate.

## Grails x Grox compat x Grox puro (2026-10-10)

A **mesma aplicação** em três versões, uma de cada vez na mesma máquina e na mesma JVM (JDK 27), em ambiente `production`, com H2 em memória e 20 livros:

| Versão | O que é |
|---|---|
| Grails 8.0.0 | `bench/livros-grails`: build à parte do Grails 8 (Undertow, Spring Security, SiteMesh 3, Hibernate 7), jar do `bootJar` com as GSPs pré-compiladas. O `grails-app/` é um link simbólico para o de `bench/livros` |
| Grox compat | `bench/livros`: o **mesmo `grails-app/`** (controllers, `UrlMappings`, domain classes do s2-quickstart, service, BootStrap, views) no Grox com o módulo Grails e os plugins |
| Grox puro | `sample-plain`: as mesmas páginas e respostas em rotas do `grox-dsl`, sem o módulo Grails |

As três têm segurança com as mesmas regras (rotas medidas em `permitAll`), o mesmo layout e a mesma lista em GSP. Rotas: texto (`/ola/mundo`), a lista de livros em GSP com layout, `<g:link>` e `<g:form>` (`/livros`) e um livro em JSON (`/api/livros/7`). Duas rodadas intercaladas (Grails, compat, puro, puro, compat, Grails), cada uma com 20 s de aquecimento e 10 s de medição com 64 conexões, e logo depois 10 s com 1 conexão (latência sem fila). Sem erros nem respostas fora de 2xx.

```bash
./gradlew :bench:installDist :bench:livros:installDist :sample-plain:installDist
./gradlew -p bench/livros-grails bootJar
bench/run-livros.sh grails; bench/run-livros.sh grox; bench/run-livros.sh plain
```

| req/s com 64 conexões (duas rodadas) | Grails 8 | Grox compat | Grox puro |
|---|---:|---:|---:|
| Texto | 9.478 · 8.998 | 18.474 · 17.893 | 24.148 · 27.455 |
| GSP com layout | 1.182 · 1.536 | 1.676 · 2.193 | 2.403 · 3.107 |
| JSON | 10.695 · 10.859 | 12.612 · 13.323 | 13.627 · 13.986 |

| p50 com 1 conexão | Grails 8 | Grox compat | Grox puro |
|---|---:|---:|---:|
| Texto | 0,21 ms | 0,36 ms | 0,29 a 0,32 ms |
| GSP com layout | 1,37 a 2,20 ms | 0,88 a 1,09 ms | 0,79 a 0,82 ms |
| JSON | 0,37 a 0,38 ms | 0,44 a 0,66 ms | 0,37 a 0,40 ms |

| Subida (até responder, com a JVM) | Grails 8 | Grox compat | Grox puro |
|---|---:|---:|---:|
| | 17,9 a 22,8 s | 8,2 s | 7,2 a 8,0 s |

- **Sob carga, o Grox atende mais nas três rotas.** O compat faz cerca de 2× o Grails em texto, 1,4× na GSP e 1,2× em JSON; o puro, 2,8×, 2× e 1,3×.
- **Com 1 conexão, o Grails é mais rápido em texto** (0,21 contra 0,29 a 0,36 ms) e empata com o puro em JSON. Cada requisição no Grox passa por pelo menos dois saltos de thread (event loop do Netty, virtual thread ou ator, event loop de novo), e no compat também pelo ator do controller. Na GSP, onde a renderização pesa mais que o despacho, o Grox é de 1,5× a 2× mais rápido também com 1 conexão.
- **Grox puro x compat:** de 5% a 50% a mais, conforme a rota. O caminho Grails tem interceptors, `WebRequest`, binding dos argumentos da action e negociação de formato no `respond`.
- **Subida:** o Grox sobe em menos da metade do tempo. Os dois sobem o Hibernate; o Grails também sobe o contexto do Spring, o Undertow e o Spring Security.
- **Memória:** RSS de 1,2 a 1,6 GB nas três no fim da rodada, com o heap padrão da JVM (1/4 da RAM); não é uma comparação de memória (ver a seção seguinte).
- **A máquina estava ocupada:** o load average foi de 5 a 27 durante a rodada. Valem as razões entre versões vizinhas, não os números absolutos.

### O que a rodada mostrou e foi corrigido

Uma primeira rodada, antes das correções, deu outro quadro em texto: o compat com 8.525 a 11.971 req/s (abaixo do Grails) e, com 1 conexão, 0,4 ms no Grox puro. O perfil JFR do texto do `sample-plain` com 1 conexão mostrou:

| Custo | Amostras | Correção |
|---|---:|---|
| `SecurityFilter.process` dinâmico: cada `cfg.x`/`req.x` liga um call site indy | 25% | `@CompileStatic` no `process` e nos auxiliares de toda requisição (`chain`, `csrfRequired`, `crossSite`, `accessFor`, `ruleAccess`, `SecurityConfig.isStateless`) |
| Event loop: `NettyHttpHandler` dinâmico, `HeaderMap.add` com closure por cabeçalho | 26% (thread) | `NettyHttpHandler` em `@CompileStatic`; `HeaderMap.add` sem closure |
| Closures criadas por requisição (`withSecurityHeaders`, `isStateless`, `accessFor`): cada uma resolve a metaclass (`ClassInfo.lock`, 5% do topo) | | Laços simples |
| Saltos de thread: o filtro de segurança roda numa virtual thread, a rota `virtual` em outra e a gravação da sessão em outra | | A rota e a gravação da sessão rodam na virtual thread em que já estão |

A/B alternado do `sample-plain` (A, C, C, A): texto de 12.834 e 16.430 para 23.169 e 22.950 req/s, e de 0,38 a 0,40 ms para 0,27 ms com 1 conexão; JSON de 0,57 a 0,64 ms para 0,39 a 0,51 ms com 1 conexão; GSP sem cookie de 4.305 e 4.675 para 5.648 e 5.835 req/s. Só os saltos de thread, medidos antes, deram cerca de 10% com 1 conexão. O compat também ganhou, pelo filtro de segurança e pelo Netty: o texto foi de 8.525 a 11.971 para cerca de 18.000 req/s.

### Achados em aberto (ver o [roadmap](roadmap.md#pendente-benchmark-grails-x-grox-compat-x-grox-puro-2026-10-10))

- **Uma sessão por requisição anônima.** O `<g:form>` grava o token CSRF na sessão, e o gerador de carga, como um robô, não devolve o cookie: depois de cerca de 1 minuto na `/livros` havia 56.907 sessões em memória (95 mil numa rodada mais longa), cada uma com 30 min de timeout. No Redis, seria uma escrita por requisição. O Grails não tem CSRF no `<g:form>` e não cria nenhuma. Os números da GSP acima incluem esse custo.

### Depois: caminho dos controllers e a falsa disputa na sessão

- **Caminho dos controllers em `@CompileStatic`** (`ControllerExecutor`, `WebRequest`, `ActionMethod`, `ActorPool.submit`, `Metrics.end`, `Management.handles`, `Conversions.isSimple`, auxiliares do `HttpPipeline`). A/B alternado do `bench/livros` (A, B, B, A) com 1 conexão: texto de 0,35 e 0,36 para 0,31 ms; JSON de 0,49 e 0,57 para 0,39 e 0,46 ms; GSP de 0,85 e 0,86 para 0,76 e 0,80 ms. Com 64 conexões, texto cerca de +15%, o resto dentro do ruído. O salto para o ator do controller continua: é o modelo de concorrência.
- **"Requisições simultâneas na mesma sessão" era artefato de medição.** Uma rodada mostrou a GSP com 64 conexões e o mesmo cookie em cerca de 1.300 req/s, contra cerca de 5.700 sem cookie. O perfil JFR dos dois casos tem o mesmo custo por método, sem disputa de trava: o caso com cookie rodava sempre primeiro, com o JIT ainda frio. Alternando os dois na mesma JVM, depois de 50 s de aquecimento: 5.773, 5.983 e 5.777 req/s com cookie; 4.923, 6.399 e 6.723 sem.
- **Aquecimento da GSP:** a página só chega a cerca de 6.000 req/s depois de quase 1 minuto de carga; com os 20 s do `run-livros.sh`, os números da GSP saem mais baixos e mais instáveis (por isso o intervalo largo nas tabelas). Para comparar GSPs, use `WARM=60`.

## Tags nativas em `@CompileStatic` (2026-11-26)

`/usuario/index` do sample-app (lista com `g:link`, `g:form`, `g:paginate`, `g:sortableColumn`, `g:message`, `g:formatDate`), logado por sessão (cookie, sem o bcrypt do Basic). Perfil JFR antes da mudança, com 1 conexão, por camada: tags 20%, Hibernate 19%, segurança 9%, GSP 7,5%, SiteMesh 6,5%, Netty 6%. Nas tags: `g:link` 63 amostras, `g:form` 55, `g:paginate` 40, `g:message` 32, `g:formatDate` 18, `g:sortableColumn` 15.

As seis viraram métodos `@CompileStatic` (antes, closures: cada acesso a `webRequest`, `out`, `attrs` passava pelo despacho dinâmico). A base e a versão nova empacotadas, alternadas (A, B, A, B), 30 s de aquecimento e 15 s de medição; "CPU/req" é o tempo de CPU do processo dividido pelas requisições da janela (menos sensível a outros processos).

| | Dupla 1: A → B | Dupla 2: A → B |
|---|---:|---:|
| p50 com 1 conexão | 10,83 → 9,43 ms | 11,52 → 10,46 ms |
| CPU/req com 1 conexão | 19,47 → 17,95 ms | 21,76 → 21,62 ms |
| req/s com 64 conexões | 1.897 → 2.073 | 762 → 871 |
| CPU/req com 64 conexões | 3,50 → 3,15 ms | 8,71 → 7,88 ms |

- **Cerca de 10% na página**, nas duas duplas e nas duas métricas de carga.
- A segunda dupla rodou com a máquina mais lenta (provável redução de frequência da CPU depois de carga sustentada): vale a comparação dentro de cada dupla, não entre elas.
- Script: `bench/run-logado.sh rotulo /usuario/index` (login por formulário, cookie no gerador de carga, CPU do processo por `/proc/<pid>/stat`; `DIST=` aponta outra distribuição para o A/B). O `bench/run.sh` mede rotas sem login.

**Segunda rodada (2026-11-27):** os campos de formulário (`textField`, `hiddenField`, `passwordField`, `emailField`, `numberField`, `dateField`, `field`, `submitButton`), as tags de layout (`layoutTitle`, `layoutHead`, `layoutBody`, `pageProperty`, `ifPageProperty`), a `AssetTagLib` (`javascript`, `stylesheet`, `image`, `link`) e `sec:ifLoggedIn`/`ifNotLoggedIn` também viraram métodos `@CompileStatic`. A/B alternado (A, B, A, B), 1 conexão, 40 s com JFR:

| | Rodada 1: A → B | Rodada 2: A → B |
|---|---:|---:|
| Amostras dentro das tags (`TagLibRegistry.callTag`, inclui os corpos) | 21,2% → 19,5% | 20,2% → 17,6% |
| CPU/req | 14,2 → 10,8 ms | 12,3 → 11,3 ms |

- Cerca de 2 pontos a menos de amostras nas tags (≈10% do tempo delas). A CPU por requisição foi na mesma direção, mas a máquina estava com o navegador ativo: a fatia do JFR é a medida mais confiável.
- O que continua em closure é pouco usado nas páginas medidas (`select`, `checkBox`, `textArea`, `render`, `createLink`, as tags `sec:` de papéis...); converter quando aparecer no perfil.

## Religação de call sites do Groovy (2026-11-26)

No perfil da `/usuario/index` (1 conexão), 4,5% das amostras estavam em `IndyInterface.fallback`: o Groovy religando um call site dinâmico a cada troca do tipo que ele vê. Acontece quando o mesmo ponto do framework atende tipos diferentes: o proxy dos services (services diferentes), o `DomainEventBridge` (o `afterLoad` de cada entidade carregada), o `DetachedCriteria` (domains diferentes) e o construtor das domains (o Hibernate instancia a domain e o proxy dela, uma subclasse).

| Mudança | Amostras na página |
|---|---|
| Antes | religação 4,5%; `DomainEventBridge` 2,4% |
| `ServiceProxies` e `DomainEventBridge` em `@CompileStatic` | religação 2,8%; `DomainEventBridge` 0% |
| Execução do `DetachedCriteria` (`list`, `count`, `find`...) em `@CompileStatic` | religação 1,2% (só o construtor das domains) |
| `$init$` dos traits com chamada direta no construtor das domains (`TraitInitTransformation`, 2026-11-27) | religação 0,1% (antes 1,4% na mesma rodada, % de todas as amostras) |

- Medido pela fatia no JFR, e não pela vazão: nesta máquina (área de trabalho, redução de frequência da CPU), a vazão variou mais que o efeito entre rodadas iguais.
- Construtor das domains: as chamadas `$init$` dos traits `GormEntity` e `ValidationSupport` ficam no construtor da domain, que é dinâmico (código da app), e eram `invokedynamic` guardados pelo tipo de `this`. A `TraitInitTransformation` (global, em `INSTRUCTION_SELECTION`, depois da composição dos traits) liga essas chamadas ao método do helper, e o Groovy emite `invokestatic`; o resto do construtor continua dinâmico. A/B na mesma máquina ocupada: `IndyInterface.fallback` de 62 para 4 amostras em 4,5 mil; CPU de 9,5 para 8,7 ms por requisição (a máquina estava ocupada, então a CPU é só indicativa).

## HQL gerado no lugar da JPA Criteria (2026-11-26)

`where`/`DetachedCriteria`/`createCriteria` passaram a gerar HQL a partir da árvore de critérios (`HqlQuery`): joins com alias, valores como parâmetros nomeados. A mesma forma de consulta gera a mesma string, e o Hibernate a encontra no cache de planos; com a JPA Criteria, a consulta era montada e analisada a cada vez.

| Perfil da `/usuario/index` (1 conexão), % das amostras da página | Criteria | HQL |
|---|---:|---:|
| Banco (total) | 17,6 | 15,3 |
| Montagem da consulta (`SqmCriteriaNodeBuilder`, árvore SQM) | 1,5 | 0,2 |
| Tradução para SQL (`buildInterpretation`) | 4,4 | 4,2 |
| Hidratação | 5,2 | 5,8 |

- **A tradução para SQL continua** no sample-app: ele é multi-tenant, e o `@TenantId` do Hibernate é um filtro de sessão (`_tenantId`) sempre habilitado. O Hibernate 7.4 (`SqmInterpretationsKey.isCacheable`) não guarda o plano de consultas com filtro habilitado; só a análise do HQL vem do cache. Em apps sem multi-tenancy (e sem `hibernate-filter` ligado), o plano inteiro é reaproveitado (teste: `queryPlanCacheHitCount`).
- Em 2026-11-27, o `list(params)`/`count`/`exists`/`getAll` da domain, os dynamic finders e `findWhere`/`findAllWhere` passaram pelo mesmo HQL (o mesmo ganho por consulta; a `/usuario/index` já usava `where`). Projeções e `deleteAll`/`updateAll` continuam com a Criteria.

## Memória: Grails x Grox (2026-10-31)

A mesma app de livros da seção anterior, com o heap fixo (`-Xmx`), NMT ligado e 15 s de carga com 64 conexões na `/livros` (GSP) e depois na `/api/livros/7` (JSON). "Heap vivo" é o usado logo depois de um GC completo (`jcmd GC.run`). A máquina estava ocupada (load de 4 a 10), então os req/s não valem aqui.

```bash
bench/run-mem.sh grails 512m 15; bench/run-mem.sh grox 512m 15; bench/run-mem.sh plain 512m 15
EXTRA=-Dgrails.plugin.springsecurity.csrf.enabled=false bench/run-mem.sh grox 64m 15
```

| Com `-Xmx512m` | Grails 8 | Grox compat | Grox puro |
|---|---:|---:|---:|
| Heap vivo, parada | 101 MB | 76 MB | 74 MB |
| Heap vivo, depois da carga | 110 MB | 89 MB | 93 MB |
| Classes carregadas | 22.356 → 23.387 | 16.140 → 17.520 | 16.406 → 18.292 |
| Metaspace (NMT, committed) | 93 MB | 70 MB | — |
| Threads na carga | 113 | 58 | 57 |
| RSS, parada → carga | 639 → 732 MB | 470 → 675 MB | 478 → 643 MB |

| Menor heap | Grails 8 | Grox compat |
|---|---|---|
| Não sobe | 64 MB (OOM na inicialização do contexto do Spring) | 40 MB |
| Sobe, mas não aguenta a carga na GSP | 80 MB (17 req/s, p50 de 3,8 s) | 48 a 56 MB (OOM) |
| Aguenta a carga, sem erros | 96 MB | 64 MB (RSS de 326 → 516 MB) |

- **O Grox precisa de cerca de 2/3 da memória do Grails** na mesma app: heap vivo de 25 a 30% menor, um terço a menos de classes e de metaspace, metade das threads (o Undertow tem um pool de workers; o Grox usa o event loop e virtual threads). O heap mínimo que aguenta a carga caiu de 96 para 64 MB.
- **O que ocupa o heap** (histograma de classes depois de subir, `-Xmx256m`: Grails 104 MB, Grox 80 MB): nos dois, os maiores são as metaclasses do Groovy (`MetaMethodIndex$Cache`, `CachedMethod`, `java.lang.reflect.Method`), não o Hibernate. Com menos classes carregadas, o Grox cria menos metaclasses. Esse é o próximo alvo, se a memória importar: menos chamadas dinâmicas nas classes do framework, e `@CompileStatic` onde não há dinamismo.
- **RSS:** com heap pequeno, o RSS fica de 300 a 520 MB nos dois, dominado por metaspace, code cache do JIT, GC e arenas nativas, e não pelo heap. Para conter o RSS num contêiner, limite também `-XX:MaxMetaspaceSize` e `-XX:ReservedCodeCacheSize`, e use `-XX:+UseSerialGC` em instâncias pequenas.
- **Sessões do CSRF:** no Grox, cada GET anônimo da `/livros` cria uma sessão (o token do `<g:form>`), e o gerador de carga não devolve o cookie; com 48 MB e CSRF desligado ainda houve OOM, então não é isso que define o mínimo nesta app. Num robô de verdade, o peso das sessões vale a pena acompanhar (ver acima).
- **Apps reais:** o app A e o app C carregam muito mais classes (o app-c-core sozinho tem milhares), e não foram medidos: a diferença acima vem da pilha (Spring, Undertow, Spring Security, GORM), que é a mesma, mas as classes da app pesam mais. Para medir, use o `grox/` do build paralelo e o `bootJar` do Grails com o mesmo `-Xmx`.

## Grails compat x grox-dsl (2026-10-09)

As mesmas três respostas na **mesma aplicação** (o `sample-app`, que pelo agregador `framework` tem também o `grox-dsl`): as actions dos controllers e rotas equivalentes em `BenchRoutes` (`/dsl/...` em virtual threads, `/dsl-ator/...` num pool de 16 atores, o tamanho do pool dos controllers). Os dados, o banco, a segurança, a sessão e a JVM são os mesmos. Muda só o caminho até o handler, e numa app com os dois a rota da DSL ainda paga a tentativa de casar com os controllers primeiro. Foi uma subida com aquecimento de 20 s por rota, duas rodadas intercaladas de 10 s com 64 conexões e uma com 1 conexão, sem erros nem respostas fora de 2xx.

| Rota (req/s, 64 conexões; p50 com 1 conexão) | Grails (controller) | DSL, virtual thread | DSL, ator |
|---|---:|---:|---:|
| Texto (`/ola/x`, controller `concurrency: 'request'`) | 16.280 · 0,57 ms | 22.579 · 0,44 ms | 21.949 · 0,48 ms |
| GSP com layout (lista de usuários, mesmo HTML) | 1.505 · 4,73 ms | 2.257 · 2,30 ms | 2.610 · 2,32 ms |
| API JSON com JWT (`/api/usuarios/1`) | 3.386 · 1,97 ms | 6.947 · 0,78 ms | 6.994 · 0,78 ms |

- **A rota da DSL atende de 1,4× a 2× mais**, com o mesmo HTML e o mesmo JSON.
- **O caminho Grails faz mais trabalho:** interceptors (o `AuditoriaInterceptor` roda em toda action), o `WebRequest` com os parâmetros, o binding dos argumentos da action, o `respond` com negociação de formato e, na API, o `show(Usuario usuario)`, que carrega por binding e roda o `validate()`, com uma consulta a mais para o `unique` (pendência conhecida do roadmap).
- **O perfil com JFR do `/ola/x` achou um custo evitável:** com `concurrency: 'request'`, cada requisição cria o controller e chama o `Autowirer.autowire`, cujo `setProperty` dinâmico era religado pelo Groovy a cada injeção. Eram 891 de 977 amostras do roteador, cerca de 30% do custo da action. Com `@CompileStatic` no `autowire`, o `/ola/x` dos controllers foi de 11.468 para 16.280 req/s, e o p50 com 1 conexão de 0,75 para 0,57 ms.
- **Virtual thread x ator na DSL:** empatam em texto e JSON. Na página GSP, o pool de atores rende ~15% mais com 64 conexões, porque limita a concorrência de renderização a 16.

## Modularização: antes x depois (2026-10-09)

A base é o commit `ccde730` (fase 1: só o teste de fronteiras, o runtime de antes da modularização). O atual é o `HEAD` da branch `modularizacao`: subprojetos `grox-*`, `HttpPipeline` no core, `grox-dsl` e as DSLs. Foi o `sample-app` empacotado nas duas versões, na mesma máquina (Ryzen 7 3700U, notebook), com 30 s de aquecimento e 10 s de medição, alternando a ordem (A, B, B, A).

| Rota (req/s, 64 conexões) | A1 | B1 | B2 | A2 |
|---|---:|---:|---:|---:|
| `/ola/x` | 15.660 | 6.641 | 10.191 | 9.136 |
| `/usuario/index` | 777 | 660 | 680 | 637 |
| `/api/usuarios/1` | 3.514 | 3.221 | 3.149 | 2.593 |
| `/actuator/health` | 28.472 | 25.439 | 25.914 | 26.602 |

| Rota (p50, 1 conexão) | A | B |
|---|---:|---:|
| `/ola/x` | 0,71 ms | 0,66 ms |
| `/usuario/index` | 4,53 ms | 4,66 ms |
| `/actuator/health` | 0,35 ms | 0,36 ms |

- **Atendimento: sem diferença mensurável.** A1 foi a única rodada com a máquina fria; nas outras, já com o processador quente, A e B ficam dentro do ruído. Com 1 conexão, que mede o custo por requisição sem saturação, as duas versões empatam.
- **Subida: igual no total.** Duas subidas de cada sem JFR sugeriam 0,5 s a mais (6,9 s contra 6,3 s), mas não se confirmou: com JFR (amostragem de 1 ms, `stackdepth=1024`), A ficou em 6,47 e 7,41 s e B em 6,84 e 6,89 s, e a thread `main` tem o mesmo número de amostras nas duas (cerca de 2.350). O tempo mudou de fase: os metadados do datastore caíram (~1.000 → ~660 ms) e o `scan` subiu (~90 → ~300 ms). A busca de classes em vários jars custa pouco (~150 → ~190 amostras de `URLClassPath`, uns 20 ms).
- **O `scan` foi corrigido:** 103 das 151 amostras eram o `find` com closure por classe e por tipo de artefato, mais a ligação das chamadas dinâmicas dos predicados dos módulos (`.&isDataService as Predicate`). Com um laço simples, o teste de classe concreta feito uma vez por classe e os `getArtefactTypes` em `@CompileStatic`, o `scan` caiu para ~150 ms. A sobra é a carga das classes dos módulos (`ServiceLoader`, ~60 ms).
- Uma tentativa anterior, com outra compilação na máquina (load average 16) e 15 s de aquecimento, deu diferenças de 2× a 3× nos dois sentidos: não compare versões com a máquina ocupada.

## Grox x Grails (2026-09-30)

A mesma página (`/login/auth` do app-a: GSP com layout SiteMesh, tags de formulário e sessão com CSRF) nas duas aplicações, **empacotadas** (jar do Grails com `-Dgrails.env=development`; `installDist` do piloto), no **mesmo MySQL e no mesmo Redis**, na mesma máquina, uma de cada vez. O gerador faz 10 s de aquecimento e 15 s de medição.

| | Rodada 1: 1 conexão | Rodada 1: 64 conexões | Rodada 2: 1 conexão | Rodada 2: 64 conexões |
|---|---:|---:|---:|---:|
| Grails 8.0.0-M5 | 149 req/s (p50 6,0 ms) | 615 | 243 | 1.562 |
| Grox (piloto) | 164 req/s (p50 5,6 ms) | 1.238 | 588 | 3.733 |
| Grox ÷ Grails | 1,1× | 2,0× | 2,4× | 2,4× |

- O jar do Grails é de 31/08: o `bootJar` atual do app A falha no `assetCompile` (conflito `groovy-xml` 5.1.0/5.0.8). A página de login é a mesma.
- Em `bootRun` (devtools, GSP recompilável), o Grails faz 30 req/s com 1 conexão e 94 com 64: não use o modo de desenvolvimento para comparar.
- A rodada 2 foi mais rápida para os dois (JIT mais aquecido e máquina menos carregada). A razão entre eles é o que vale.

**Aquecimento:** o `run.sh` usava 3 s de aquecimento, pouco para o JIT. Com 30 s, a `/usuario/index` do sample-app faz 724 req/s com 64 conexões (antes medíamos ~140) e ~13 ms por requisição com 1 conexão; `/ola/x` faz 12.728 req/s (0,5 ms). O padrão agora é `WARM=15`.

## Resultados indicativos (2026-09-29)

AMD Ryzen 7 3700U (4 núcleos / 8 threads), JDK 27, gerador na **mesma máquina** e a máquina **em uso** (IDE e navegador abertos, load average de 8 a 15). Servem para comparar versões entre si, não como números absolutos.

| Cenário | Antes das otimizações | Depois | O que passa |
|---|---:|---:|---|
| `/actuator/health` | 7.366 req/s | 6.698 req/s | Segurança, `SELECT 1` no pool |
| `/static/css/app.css` | 2.741 | 3.614 | Segurança, cache de estáticos |
| `/ola/x` | 2.740 | 2.534 | Segurança, ator por requisição, interceptors, texto |
| `/api/usuarios/1` (JWT) | 382 | 634 | JWT, ator do pool, GORM `get`, JSON |
| `/usuario/index` | **80** | **199** | Página GSP com layout, TagLibs, i18n, 2 consultas, links, paginação |

As variações de ±10% entre rodadas iguais se devem à máquina compartilhada. Uma segunda rodada, com carga maior na máquina, deu entre 150 e 280 req/s para a API: foi descartada.

## O que o profiling mostrou (JFR)

As otimizações vieram de medir, não de adivinhar:

1. **Exceções por requisição:**
   - toda action abria uma sessão Hibernate, cuja abertura consultava o resolver de tenant, que lançava `TenantNotFoundException` para usuários anônimos;
   - o resolver aninhado chamava métodos estáticos sem qualificar (mais duas `MissingMethodException` por requisição);
   - soluções: sessão Hibernate **preguiçosa** (aberta no primeiro uso), `OptionalTenantResolver` (sem exceção) e chamadas qualificadas.
2. **Exceções do despacho dinâmico:**
   - métodos `private` chamados de closures em classes que têm subclasses (`GspPage`);
   - `obj.invokeMethod()` com o alvo tipado como `Object` (`IncompatibleClassChangeError` interno do indy);
   - `v == false` comparando String e Boolean.
3. **Despacho dinâmico do Groovy:** cerca de 60% da CPU da página GSP ia para `MetaClassImpl`/reflexão. A solução foi `@CompileStatic` nas classes quentes e sem dinamismo (`Html`, `MessageSource`, `UrlMapping`, `UrlMappingsHolder`, `LinkGenerator`, `TagLibRegistry`, `GspRenderContext`). Isso sozinho deu +150% na página.
4. **Caches:** plano de autowiring por classe, loggers, nome lógico dos interceptors e expressões de acesso compiladas.
5. **Revisão de 2026-09-30** (`/usuario/index`, JFR em condições iguais: de 168 para 201 req/s, +20%; em todas as rodadas A/B alternadas contra o commit anterior, a página ficou à frente):
   - **Call sites religados a cada requisição** (`IndyInterface.selectMethodHandle`, de 6,5% para 3,1% inclusivo): `GspPage.invokeTag` passava o corpo da tag às vezes `null`, às vezes closure, e o guard do indy falhava. Os métodos chamados pelo código gerado (`printText`, `printEncoded`, `invokeTag`...) passaram a `@CompileStatic`; `getProperty`/`methodMissing` continuam dinâmicos para as expressões do usuário.
   - **`switch` sobre `char`/`String` em `@CompileStatic`** (`Html.escape`, `GspBinding`): cada `case` passa por `isCase`, que é despacho dinâmico, por caractere escapado. Trocado por comparações (`Html.escape` de 3,6% para 0,2%).
   - **`closure as ScopedValue.CallableOp`**: um `java.lang.reflect.Proxy` por chamada, de 3 a 6 aninhados por requisição. Trocado por `org.grox.core.Scoped` (implementação concreta).
   - **Variável ausente na GSP** lançava `MissingPropertyException`; **i18n** sem cache (lista de sufixos e `MessageFormat` novos a cada mensagem); ETag MD5 do asset a cada requisição; regex dos interceptors com `uri:` compilada a cada requisição; `StringWriter` (sincronizado) nas capturas; o access log copiava o corpo para medir o tamanho.
   - **Estáticos sem segurança nem sessão** (grupo 2 da revisão): `/static/css/app.css` passou de 3.614 para 5.940 req/s na medição de base, com a máquina menos carregada.
   - O que sobra do despacho dinâmico está espalhado (nenhuma classe acima de 3%): taglibs (closures, por desenho, como no Grails), código gerado das GSPs e services.

6. **Medido e não adotado (2026-09-30), sempre em A/B alternado:**
   - `hibernate.criteria.plan_cache_enabled=true`: 139 contra 139 req/s na página e +1% na API (ruído). As Criteria do GORM são montadas a cada chamada, e o cache de plano não ajudou.
   - `grox.controllers.poolSize` (padrão 2×CPU = 16 aqui): 16×64 deu +16% na página e +33% na API; 16×32 deu −12%; 64×128, ±9%. O ruído da máquina (±15%) é maior que o efeito: padrão mantido, a medir numa máquina dedicada e com banco remoto (onde a espera de JDBC prende o ator).
   - Espera por conexão no Hikari: nenhum evento no JFR com H2 em memória (pool de 10). Com banco remoto, rever junto com o `poolSize`.
   - SiteMesh (4% da página) e casamento/reverse de rotas (cerca de 5%): ganho máximo pequeno para a mudança; ficaram para depois.
7. **N+1:** `hibernate.default_batch_fetch_size: 16` passou a ser o padrão (a app pode sobrescrever no yml). Uma lista com N associações lazy distintas faz 2 consultas em vez de N+1 (teste em `GormTest`).

**Como comparar versões:** a máquina compartilhada varia muito (load average de 1 a 24 na mesma tarde). Só valem rodadas A/B alternadas: um `git worktree` com a versão anterior, `installDist` nos dois e o `run.sh` de cada um intercalado, várias vezes, comparando as medianas.

Receita de profiling:

```bash
jcmd <pid> JFR.start name=p settings=profile filename=/tmp/p.jfr duration=10s
jfr summary /tmp/p.jfr | grep -E 'ExceptionThrow|ExecutionSample'
jfr print --events jdk.JavaExceptionThrow --stack-depth 20 /tmp/p.jfr
jfr print --events jdk.ExecutionSample --stack-depth 200 /tmp/p.jfr
```

## Próximos alvos (pendentes, ver [roadmap](roadmap.md#pendente-próximos-passos-fase-7))

Perfil da `/usuario/index` do sample-app com 1 conexão (JFR, só a janela de medição): ~13 ms de CPU por página, contra ~1,7 ms da página de login do piloto. Por fase, em ms por requisição:

| Fase | ms | Melhoria prevista |
|---|---:|---|
| Tags | 2,7 | Tags nativas em `@CompileStatic`: feito para `g:link`, `form`, `paginate`, `message`, `formatDate` e `sortableColumn` (cerca de 10% na página, ver acima) e, em 2026-11-27, para os campos de formulário, as tags de layout, a `AssetTagLib` e `sec:ifLoggedIn` (mais ≈10% do tempo das tags) |
| Banco | 2,5 | HQL gerado e em cache para consultas repetidas (a interpretação SQM e a hidratação dominam; o H2 é 0,3 ms) |
| Action | 2,1 | Proxy dos services, `DetachedCriteria`, `respond`: espalhado, nenhum item acima de 0,15 ms |
| Virtual threads e despacho solto | 1,8 | Menos saltos de thread nas rotas simples |
| Netty | 1,3 | `@CompileStatic` em `NettyHttpHandler` (`convert`, `enqueue`, `HeaderMap` com closures) |
| GSP sem tags | 1,2 | |
| SiteMesh | 0,8 | Preprocess na compilação da GSP |
| Segurança e sessão | 0,7 | |

- Tags nativas em `@CompileStatic` (maior fatia).
- `@CompileStatic` em `NettyHttpHandler`, `WebDispatcher`, `ControllerSupport` e `SecurityFilter`, com lambdas nativas no lugar das closures passadas ao `CompletableFuture`.
- Consultas: HQL em cache para `DetachedCriteria`/`where`/finders.
- Rotas: indexar os mapeamentos por primeiro segmento literal (hoje o match e o reverse são lineares).
- Conexão JDBC presa durante a renderização da view (open session in view): liberar após cada transação.
- Rodar numa máquina dedicada, com o gerador em outra máquina, e escolher o `poolSize` padrão com dados.
