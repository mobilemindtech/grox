# Roadmap

## Feito

| Fase | Entregue |
|---|---|
| 0: Estrutura | Módulos `framework` e `sample-app`, catálogo de versões, git |
| 1: HTTP e atores | Netty 4.2, pipelining, keep-alive, roteador, atores de controller (`pool` e `request`) |
| 2: Controllers e services | Trait por AST, render/redirect/respond/withFormat/bindData, command objects, `allowedMethods`, services-ator, sessão, flash, JSON, `application.yml`, BootStrap, erros 404/500 |
| 3: Views | Motor GSP próprio (sintaxe, tags de controle, codificação, erros com linha, reload), TagLibs (nativas e da app), templates, layouts SiteMesh 3, i18n, estáticos, `_method` |
| 4.1: Infra | Sessão plugável (memória/Redis via Lettuce), segurança estilo spring-security-core e rest (regras no yml, `@Secured`, form login, Basic, JWT, CSRF, bcrypt, usuários GORM/yml/plugável, tags `sec:`), multi-tenancy DISCRIMINATOR com tenant do usuário, propagação de contexto para services-ator |
| 6.2: Assíncrono | Actions assíncronas (`grails.async`: task/tasks/Promise, CompletableFuture, Awaitable) com propagação de contexto e timeout |
| 6.1: Console e assets | Console web (estilo plugin console), asset pipeline próprio compatível com o do Grails (diretivas, digest, manifest, tags `asset:`) |
| 6: Produção | Health, info, métricas (JSON e Prometheus), access log, desligamento gracioso, refresh e revogação de JWT, plugin Gradle `org.grox.app` com pré-compilação de GSPs, gerador de carga e otimizações guiadas por profiling (2,5× na página GSP) |
| 5: Essenciais | `g:paginate`, `g:sortableColumn`, interceptors, `where { }` (AST; caminhos de associação com vários níveis, como `livro.autor.nome`, conferidos nível a nível na compilação desde 2026-11-26) e `createCriteria()` com projeções, upload multipart, erros `typeMismatch`; correção: entidade inválida não é mais gravada no commit |
| 8: Piloto de migração (em andamento) | Um projeto Grails 8 real (app A) compila 100% (código, 193 GSPs, 200 assets), sobe, e 100 de 162 actions GET respondem 200 (os demais são 3xx/4xx esperados ou erros da própria app). Pacote `org.grox.plugins` (console, jobs no lugar do Quartz, excel-export em POI 5), many-to-many, eventos `@Listener`, dirty checking, Data Services, `resources.groovy`, placeholders no config e ~40 classes de compatibilidade |
| 4: Dados | GORM próprio sobre Hibernate 7.4 e HikariCP: domain classes por AST e `orm.xml`, API estática e de instância, dynamic finders, HQL, constraints e mensagens do Grails, `hasMany`/`belongsTo`, transações, services `@Transactional`, sessão por action propagada a services-ator, binding de associações, JSON de entidades e erros, tags de erro |

## Pendente: próximos passos (Fase 7)

Status: **não iniciado**. Ordem sugerida:

- [ ] **Desempenho** (perfil da `/usuario/index` com 1 conexão, ~13 ms de CPU por página: ver [benchmark.md](benchmark.md#próximos-alvos-pendentes-ver-roadmap)). Medir cada passo em A/B
  - [x] Tags como métodos (`def tag(Map attrs, Closure body)`, como no Grails 3+), além de closures: base para tipar as tags nativas
  - [ ] Tags nativas em `@CompileStatic` (`ApplicationTagLib`, `FormTagLib`, `RenderTagLib`, `AssetTagLib`): ~2,7 ms por página, a maior fatia. **Feito em parte (2026-11-26):** `g:link`, `form`, `paginate`, `message`, `formatDate` e `sortableColumn` viraram métodos `@CompileStatic`, cerca de 10% na `/usuario/index` (ver [benchmark.md](benchmark.md#tags-nativas-em-compilestatic-2026-11-26)). Faltam as demais e a `AssetTagLib`. A API das tags para as apps não muda
  - [ ] `@CompileStatic` em `NettyHttpHandler` (`convert`, `enqueue`, `HeaderMap`), `WebDispatcher`, `ControllerSupport` e `SecurityFilter`, com lambdas nativas no lugar das closures do `CompletableFuture`: ~1,3 ms de Netty mais o custo fixo de toda requisição. **Feito em parte (2026-10-10):** `NettyHttpHandler`, `HeaderMap.add`, o `SecurityFilter.process` e os auxiliares, `SecurityConfig.isStateless` e `HttpPipeline.withSecurityHeaders`; rotas `virtual` do `grox-dsl` e a gravação da sessão sem salto de thread extra. Faltam `WebDispatcher` e `ControllerSupport` (caminho dos controllers)
  - [x] Consultas: HQL gerado para `where`/`DetachedCriteria`/`createCriteria` (`list`, `count`, `get`), no cache de planos do Hibernate (2026-11-26): banco de 17,6% para 15,3% da `/usuario/index` (ver [benchmark.md](benchmark.md#hql-gerado-no-lugar-da-jpa-criteria-2026-11-26)). Com o filtro `_tenantId` do `@TenantId` habilitado, o Hibernate 7.4 não guarda a tradução para SQL (só a análise do HQL)
  - [x] Consultas: o mesmo HQL para o `list(params)`/`count`/`exists`/`getAll` da domain, os dynamic finders e `findWhere`/`findAllWhere` (2026-11-27)
  - [x] Helpers do `GspPage` em `@CompileStatic` (feito na revisão de 2026-09-30)
  - [x] Religação de call sites do Groovy (2026-11-26): `ServiceProxies`, `DomainEventBridge` e a execução do `DetachedCriteria` em `@CompileStatic`; de 4,5% para 1,2% da página (ver [benchmark.md](benchmark.md#religação-de-call-sites-do-groovy-2026-11-26))
  - [x] Construtor das domains: as chamadas `$init$` dos traits religavam (a domain e o proxy do Hibernate passam pelo mesmo call site); a `TraitInitTransformation` as torna chamadas diretas (2026-11-27): religação de 1,4% para 0,1% das amostras
  - [ ] Índice de rotas por primeiro segmento literal (match e reverse hoje são lineares)
  - [ ] Liberar a conexão JDBC após cada transação durante a renderização da view
  - [ ] Benchmark em máquina dedicada (gerador em outra máquina) e escolha do `grox.controllers.poolSize` padrão
- [ ] **Dados**
  - [x] Many-to-many (`hasMany` nos dois lados), feito no piloto
  - [ ] `hasOne` e `embedded`
  - [ ] Herança de domain classes
  - [x] Dirty checking (`isDirty`, `getPersistentValue`) e listeners `Pre/Post*` (`@Listener`), feitos no piloto
  - [x] Eventos de domain `after*` e `beforeValidate`; `Pre*Update` também no flush automático (2026-11-26: `DomainEventBridge`)
- [ ] **Desenvolvimento**
  - [x] Hot reload de controllers, services, taglibs, domain classes e `src/` no `run`, com classloaders por geração, recarga leve e completa, e Gradle contínuo incremental (ver [guia](guia.md#recarga-de-classes-em-desenvolvimento))
  - [x] Recarga das bibliotecas `org.grox.lib` do mesmo build (diretas e transitivas), como o `app-c-core` do app-c: classes, resources, classpath do `run` sem os jars delas e Gradle contínuo; com fontes fora da raiz do build (build paralelo), Gradle sob demanda (`grox.reload.sources`), porque o contínuo não as vê (2026-11-04)
  - [ ] Recarga das views das raízes externas do build paralelo (hoje chegam só pelo `processResources`)
  - [ ] Respostas em streaming e Server-Sent Events
  - [ ] Assets: minificação e gzip no `assetCompile`; avaliar o asset-pipeline 5.2.0 quando sair a versão estável

## Pendente: pedidos de usuários (2026-10-11)

- [x] DSL de rotas: `get('{id}')` dentro de um grupo vira `/grupo/{id}` (faltava a barra) e verbos sem caminho (`post { }`) atendem o caminho do grupo
- [x] `ns` como nome alternativo de `group`
- [x] `request.json` (minúsculo), o mesmo que `request.JSON`
- [x] BootStrap no Grox puro: classe `BootStrap` no pacote da app (com `environments { }`) e `bootstrap { init { } destroy { } }` na DSL de projeto
- [x] Interceptores no Grox puro: `before`/`after`/`error` na DSL de rotas (por escopo; globais na raiz do `static app`; padrão Ant opcional) e `@Before`/`@After`/`@OnError` nos `@Handler`
- [x] Autocomplete no IntelliJ IDEA: descritores GDSL nos jars (`gorm.gdsl`: GORM tipado, `id`/`version`, `hasMany`, dynamic finders; `grails.gdsl`: traits de controllers, interceptors, services, TagLibs, `init/`, jobs e scaffold; `dsl.gdsl`: `@Handler`) e `GormEntity<D>` genérica (a AST aplica `GormEntity<Livro>`). `AutocompleteTest` roda os descritores com um `psiClass` simulado. A validar no IDEA de verdade
- [x] Nome alternativo para `grails-app/`: `grox-app/` detectado ou `grox { appDir }` (`AppLayout` no core; o plugin passa `-Dgrox.appDir` à compilação, ao `run` e aos testes). O `sample-lib` usa `grox-app/`
- [x] Testes com o `groovy.http.HttpBuilder` no lugar do `java.net.http.HttpClient` (`TestClient` do sample-app, `SamplePlainTest` e o livro). Cuidados: `followRedirects false`, URL absoluta (com `baseUri`, `//host/x` vai para outro host) e corpo binário lido em ISO-8859-1
- [x] Scaffold dinâmico na versão compat: `static scaffold = X` (AST → `RestfulController<X>`), `RestfulController` com flash/redirect para formulários e 422 nos erros, views geradas dos templates (sobrescrevíveis em `src/main/templates/scaffolding/`) e tags `f:` (subconjunto do plugin fields). Geração de código fica de fora
- [ ] Módulo Pug (pug4j) como motor de views alternativo à GSP: adiado (2026-10-11); avaliar quando houver uso

## Pendente: benchmark Grails x Grox compat x Grox puro (2026-10-10)

A mesma app (`bench/livros`) no Grails 8.0.0, no Grox com o módulo Grails e no `sample-plain` (ver [benchmark.md](benchmark.md#grails-x-grox-compat-x-grox-puro-2026-10-10)).

- [x] App de referência: o mesmo `grails-app/` no Grails 8 (`bench/livros-grails`) e no Grox (`bench/livros`); `bench/run-livros.sh`
- [x] Caminho quente em `@CompileStatic` e menos saltos de thread (texto do `sample-plain`: de ~0,39 para ~0,27 ms com 1 conexão)
- [ ] **Sessão por visitante anônimo:** uma página com `<g:form>` grava o token CSRF na sessão, e cada requisição sem cookie cria uma sessão nova (57 mil em 1 minuto de carga na memória; no Redis, uma escrita por requisição). O Grails, sem CSRF, não cria nenhuma. Opções: token sem sessão para anônimos (cookie assinado, *double submit*), timeout curto para sessões que só têm o token, ou limite de sessões anônimas. Decisão de segurança: pendente
- [x] **Custo por requisição dos controllers:** `@CompileStatic` no `ControllerExecutor`, `WebRequest`, `ActionMethod.bindArguments`/`invoke`, `ActorPool.submit`, `Metrics.end`, `Management.handles`, `Conversions.isSimple` e nos auxiliares do `HttpPipeline` (`safePath` sem regex no caso comum). Texto com 1 conexão de 0,36 para 0,31 ms, JSON de ~0,53 para ~0,43 ms (Grails: 0,21 e 0,37). O salto para o ator do controller fica: é o modelo de concorrência (uma instância por ator)
- [x] **Requisições simultâneas na mesma sessão:** não era disputa. O perfil com e sem cookie tem o mesmo custo por método; a queda vinha da ordem da medição (o caso com cookie rodava logo após a subida, com o JIT frio). Alternando na mesma JVM: 5.773 a 5.983 req/s com cookie, 4.923 a 6.723 sem
- [x] Sem `grox-grails-plugins`, o `springSecurityService` não existe: com a segurança ativa, a subida avisa no log quais artefatos e beans declaram a propriedade (`SecurityModule.beansReady`)

## Pendente: revisão de segurança e desempenho (2026-09-30)

Levantamento feito por revisão do código e testes no sample-app. Ordem sugerida: grupos 1 a 4.

**Grupo 1 (segurança, maior risco):**
- [x] Console web: `Content-Type: application/json`, `Origin`/`Sec-Fetch-Site` e, sem segurança, `Host` localhost e sem proxy (antes, um `<form enctype="text/plain">` de qualquer site executava código)
- [x] Ambiente padrão `production` (`grox.env.default`); o plugin define `development` no `run` e `test` nos testes
- [x] CSRF com Basic quando o navegador envia de outro site (`Sec-Fetch-Site`/`Origin`); clientes de API seguem sem CSRF
- [x] `bindable: false` nas constraints. Proteção padrão dos campos do usuário **não** foi feita: o piloto vincula `userTenantId`/`enabled` na tela de administração, como no Grails; cabe à app usar `bindable`/`include`

**Grupo 2 (desempenho, também resolve a sessão que revive após o logout):**
- [x] `filterChain.chainMap` com `filters: 'none'`; assets sempre e estáticos liberados para anônimos sem segurança nem sessão
- [x] `Authentication` e flash não regravados na sessão a cada requisição (`SessionValue`); gravação no Redis por script Lua, numa ida e só se a sessão ainda existir
- [x] Cache `token → usuário` no GORM token storage (`cacheSeconds`, padrão 30), invalidado no logout; roles numa consulta só (também no login)

**Grupo 3 (segurança):**
- [x] Netty: prazo para receber a requisição (slowloris), conexão ociosa, limite de conexões e de requisições pipelined em voo; 100-continue respeita o limite de corpo. O `maxContentLength` continua 10 MB (uploads do piloto)
- [x] Open redirect pela requisição salva (`//evil.com`)
- [x] Cabeçalhos de segurança padrão (`grox.http.securityHeaders`; HSTS pelo mapa) e `grox.session.cookie.secure`
- [x] Exceção ao montar a resposta vira 500 sem travar a conexão; mensagem de exceção fora das respostas em produção
- [x] Desserialização da sessão com `ObjectInputFilter` (allowlist)
- [x] Tempo de login igual para usuário inexistente (dummy bcrypt com o mesmo custo) e limite de tentativas (`grox.security.loginThrottle`)
- [x] Menores: cache de estáticos sem misses e sem symlink para fora, `/health` sem mensagem de erro fora de dev, `X-Forwarded-*` só de proxy confiável, métodos HTTP desconhecidos → 405/501, escape no `Content-Disposition` e nos links reversos, `</` no JSON, charset/multipart inválido → 400, SQL nativo e tenant documentados. Tokens GORM **não** foram para hash: quebraria os já emitidos pelo Grails no mesmo banco

**Grupo 4 (desempenho, medir antes com JFR/bench):**
- [x] Baratos: ETag de asset em cache, regex dos interceptors pré-compilada, cache no i18n, GSP sem `MissingPropertyException` para variável ausente, menos cópias do HTML, access log sem copiar o corpo. Também: call sites da GSP religados a cada requisição, `switch` com `isCase` dinâmico e proxies de `ScopedValue.CallableOp` (ver [benchmark.md](benchmark.md#o-que-o-profiling-mostrou-jfr)): página GSP +20%
- [ ] `@CompileStatic` no caminho quente (`NettyHttpHandler`, `GroxRequest/Response/Params`, `WebDispatcher`, `SecurityFilter`, `RequestSession`, `Metrics`) com lambdas nativas no lugar de closures convertidas. Feito em parte (`GspPage`, `GspBinding`, getters do `TagLibApi`, atributos do `GroxRequest`, `Persistence.withSession`); o resto mede pouco no JFR
- [x] Medido: `default_batch_fetch_size` adotado (16); cache de plano das Criteria sem ganho; pool de atores e Hikari inconclusivos nesta máquina (ver [benchmark.md](benchmark.md#o-que-o-profiling-mostrou-jfr))
- [ ] Numa máquina dedicada e com banco remoto: `grox.controllers.poolSize`, pool do Hikari e conexão presa na renderização; depois, preprocess do SiteMesh e índice de rotas (hoje ~4% e ~5% da página)

## Revisão de segurança e desempenho (2026-10-02)

Revisão das mudanças desde `14c439e` (compatibilidades do app B e do app C, chainMap, hasOne multi-tenant).

**Segurança (feito):**
- [x] Login CSRF: POST anônimo sem sessão só dispensa o token se não vier de outro site; Basic de outro site recusado também em caminho stateless
- [x] Console e actuator conferem o acesso em cadeia sem `filterInvocationInterceptor` ou com `filters: 'none'`
- [x] Regras do `interceptUrlMap` também pelo caminho canônico da action (`/admin.json` e `/admin/` não escapam de `/admin/**`)
- [x] LogoutController embutido desloga direto (antes o 302 virava GET no `/logoff`, recusado com `postOnly`, e a sessão continuava)
- [x] Session fixation e token CSRF renovados em `reauthenticate` e `SecurityContextHolder.context.authentication = ...`
- [x] `SessionInformation.expireNow()` apaga a sessão (antes só marcava)
- [x] `addCookie` com encoder estrito (sem injeção de atributos por `;`)
- [x] Tempo de login igual com `passwordEncoder` da app (dummy pelo mesmo encoder) e upgrade do hash só depois das checagens de conta
- [x] Campo `private` com closure não vira action roteável
- [x] Filtro `_tenantId` recusado por `enable/disable/with/withoutHibernateFilter` e no DSL `hibernateFilters`
- [x] Fórmula do hasOne multi-tenant com o tenant do dono
- [x] `executeQuery`/`findAll`: `lock`, `readOnly`, `cache`, `fetchSize`, `timeout` e `flushMode` aplicados (antes descartados; `lock: true` rodava sem `FOR UPDATE`)
- [x] TLS do RabbitMQ com validação do certificado e do host; Redis com verificação do host
- [x] `Content-Disposition` escapado no PDF (`renderPdf`) e no excel-export; `grailsResourceLocator` sem `..`

**Segurança (feito em 2026-11-26):**
- [x] PDF (Flying Saucer): `UserAgent` próprio que só aceite `data:` e o host da `base` (hoje um template com URL do usuário em `<img src>` faz o servidor buscar URLs internas ou `file:`)
- [x] Audit log com `asyncWrite` ou dentro de `Tenants.withoutId`: gravar com o tenant e o usuário capturados (domain de auditoria multi-tenant perde o registro)
- [x] Bean `consoleService` existe mesmo com o console desligado (o app-c-web o usa; só é risco se a app passar entrada do usuário ao `eval`): mantido como no plugin web-console, com o aviso no livro (plugins e checklist de segurança)

**Desempenho (feito, baratos):**
- [x] `methodMissing`/`propertyMissing` de controllers, taglibs, GSP e `grailsApplication` com exceções sem stack (builders tentam o owner primeiro)
- [x] Proxy de service: métodos sem transação ou tenancy só delegam
- [x] Cache do HQL com `?0` convertido; `SpringSecurityUtils.securityConfig` montado uma vez; `isTenantHasOne` em cache; entrada do chainMap calculada uma vez por requisição; `TimeZone` do `formatDate` em cache
- [x] Campo vazio vira null antes dos `PropertyEditor` da app (sem exceção por campo de data vazio)

- [x] `dbCreate: update` no modo safe também com `hibernate.dialect` explícito (o app C declara `MySQLDialect`): antes o safe era desligado em silêncio e o update fazia `alter table ... modify` nas colunas diferentes, o que segurava a subida do app-c-api contra o MySQL

**Desempenho (medir antes, JFR/bench no app-c-web):**
- [ ] hasOne multi-tenant: subselect correlacionado e carga eager por hasOne (o `Cliente` tem 4). Alternativa: `fetch LAZY` sem `not-found` e o filho como alvo de proxy
- [ ] Proxy de service com `MethodHandle` em vez de `InvokerHelper` (1 a 2 µs por chamada)
- [ ] `interceptUrlMap` do app-c-web (~288 regras) linear por requisição: índice por primeiro segmento, junto com o índice de rotas da Fase 7
- [ ] Audit log síncrono pede uma segunda conexão com a da requisição presa: risco de esgotar o Hikari sob carga (`asyncWrite` como recomendação)

## Revisão de qualidade, desempenho e segurança (2026-11-26)

Revisão do framework inteiro (a modularização moveu todos os fontes desde a de 2026-10-02), por área: núcleo HTTP e DSL, GORM, web e views, segurança e plugins. Cada achado foi reproduzido com script ou confirmado no código.

**Prioridade 1 (segurança, feito):**
- [x] `_action_X` (`<g:actionSubmit>`) resolvido no `match`, antes da segurança: antes, o `@Secured` avaliado era o da action da URL e rodava outra
- [x] Controller só com actions closure: os métodos do trait `ControllerApi` (`redirect`, `render`...) viravam actions (`/velho/redirect?url=...` era open redirect)
- [x] Interceptor da DSL casa o caminho com barra final, como a rota (`before('/admin/x')` era contornado com `/admin/x/`)
- [x] `@Secured` da action com outro `httpMethod`: vale o do controller (antes caía no `interceptUrlMap`)
- [x] Logout REST revoga também o refresh token que gerou o access token (claim `rid`); refresh recusado com `passwordExpired`
- [x] `Passwords.isEncoded` só reconhece hashes (antes, `{noop}segredo` vindo do usuário era gravado em texto puro); o padrão da documentação e do sample passou a codificar quando `isDirty('password')`
- [x] `X-Forwarded-For` lido da direita para a esquerda, pulando os proxies confiáveis (o valor da esquerda pode ser forjado)

**Prioridade 2 (disponibilidade e exceções por requisição, feito):**
- [x] Multipart com mais de 128 campos: `TooManyFormFieldsException` fecha a conexão sem resposta e deixa o `inFlight` preso; `grox.http.maxFormFields`
- [x] `Accept` com `q` inválido: 500, três stack traces e o contador `active` das métricas preso (`MimeTypes.fromAccept`)
- [x] JWT com cabeçalho que não é objeto JSON: 500 com stack trace (`Jwt.verify`)
- [x] Caches do `MessageSource` crescem um mapa por locale vindo de `?lang=`/`Accept-Language`
- [x] Sessões em memória sem limite (`grox.session.maxActive`); GET anônimo numa página protegida cria sessão para guardar a URL
- [x] `maxPipelined` não limita as requisições decodificadas do mesmo read (`maxPipelineDepth` do codec)
- [x] Rotas da DSL carregam a sessão (Redis) no event loop, ao montar o `RouteContext`
- [x] `<g:datePicker>` com valor inválido: 500 em vez de `typeMismatch`

**Prioridade 3 (correção, comparado ao Grails, feito):**
- [x] Exceção numa transação participante não marca rollback-only: commit parcial silencioso
- [x] `Tenants.withId`/`@CurrentTenant` antes da primeira consulta da requisição: entidades destacadas (lazy falha na view)
- [x] Binding de `hasMany` por ids: `String` no `Set` da associação, e o `save` quebra
- [x] `sort` por caminho de associação: inner join (somem linhas, `totalCount` diverge); sort inválido dá 500
- [x] Violação de constraint no `persist` ou no commit não vira `DataIntegrityViolationException`
- [x] `${x.encodeAsHTML()}` e os outros `encodeAs*` escapados de novo pelo `${}`
- [x] `afterView` dos interceptors não roda quando a action ou a view lança exceção
- [x] Pug: `#{tag(...)}` escapa de novo o `SafeHtml` das tags
- [x] `@ReadOnly` faz flush das alterações (FlushMode MANUAL)

**Prioridade 4 (desempenho e manutenção, feito):**
- [x] Recarga: pools do Redis e conexões do RabbitMQ das gerações antigas não são fechados; `Logs.CACHE` e `ScaffoldViews.DOMAINS` retêm os classloaders
- [x] Pug em desenvolvimento recompila cada expressão (1,7 s contra 52 ms numa lista de 100)
- [x] Corpo da resposta copiado 2 a 3 vezes (`GroxResponse`, `NettyHttpHandler`, estáticos)
- [x] Reflexão por requisição: `layout` estático do controller e construtor da GSP
- [x] Regex das `constraints` de URL compilada a cada casamento
- [x] `getAll(ids)` com uma consulta por id; `exists(id)` carrega a entidade
- [x] `RabbitPublisher` abre e fecha um canal por mensagem
- [x] Views `.md`: o `${}` não escapa a sintaxe Markdown (links e imagens injetados)
- [x] `render(file: InputStream)` não fecha o stream
- [x] `PatchTask` do plugin Gradle aceita `..` nos caminhos dos patches

## Migração de apps reais

Três aplicações Grails 8 privadas (apps A, B e C, de 61 a 404 domain classes) servem de candidatas à migração. O código delas não fica neste repositório; o que cada uma exigiu do Grox está em [Compatibilidade](compatibilidade.md) e no capítulo "Casos reais" do livro.

## Modularização

Plano em [modularizacao.md](modularizacao.md) (branch `modularizacao`): núcleo `grox-core` sem Grails, `grox-gorm`, `grox-gsp`, `grox-assets`, `grox-security`, `grox-grails`, `grox-grails-plugins` e, depois, uma DSL própria.

- [x] Fase 1: `ArquiteturaTest` com o mapa pacote → módulo e a lista de violações conhecidas (206)
- [x] Fase 2: quebrar os ciclos no mesmo jar (206 → 0 violações)
  - [x] SPI `GroxModule` e `GroxRuntime` só orquestrando: 12 módulos por `ServiceLoader`; de 206 para 131 violações
  - [x] Segurança sem spring-security nem GORM: pontos de extensão no motor, adaptadores em `org.grox.plugins.springsecurity`, módulo `org.grox.security.gorm` (131 → 99 violações; `UserTenantResolver` passou para `org.grox.security.gorm`)
  - [x] Interceptadores de service registrados pelos módulos (`ServiceInterceptor`; transação e tenant no `GormServiceInterceptor`)
  - [x] Artefatos por tipo no `GroxApplication`: `ArtefactType` registrado por módulo; `grailsApplication` como fachada no módulo Grails (67 → 60 violações)
  - [x] Ambiente no core (`org.grox.core.Env`); `grails.util.Environment` delega para ele (60 → 55 violações)
  - [x] http do core sem Grails (`MimeTypes`, `Conversions`, `sessionProvider`, `withFormat` por extensão, `jsonView`); interfaces servlet e marcadores como API do core (81 → 67 violações)
  - [x] Interfaces servlet do jar oficial `jakarta.servlet-api` 6.1 no lugar das cópias parciais: request, response e sessão implementam a API inteira (o que depende de container lança `UnsupportedOperationException`)
  - [x] Propagação de contexto por registro (`ContextProvider` de cada módulo; 99 → 81 violações)
  - [x] Taglibs registradas pelo módulo dono (`GspContributor`: validação no Grails, `sec:` no spring-security, `asset:` no asset-pipeline); CSRF do `g:form` por gancho (`FormTagLib.hiddenFields`) (20 → 11 violações)
  - [x] GSP sem `WebRequest`/Grails: `ViewRequest`, `GrailsTagLibApi`, imports das GSPs e `encodeAsJSON` pelos módulos (11 → 0 violações)
  - [x] `DataBinder` no gorm (`org.grox.data.binding`); JSON dos erros sem `RequestContext`; `SessionTenantResolver` com o módulo Grails (55 → 46 violações)
  - [x] i18n, health e access log sem GORM nem segurança: `MessageResolvable`, `GroxModule.healthChecks`, `request.userPrincipal` (46 → 40 violações)
  - [x] Classificação das APIs sem lógica (`org.springframework.context`, `grails.async`, `@Listener` do GORM) e referências a plugins por registro (`AssetPipelineModule`, `authenticatedUser` por extensão, `ArtefactTrait` dos jobs) (40 → 28 violações)
  - [x] Roteamento e segurança sem Grails: `RouteTarget`, `Endpoint` (management e console), `RequestScope`/`CurrentRequest` no core, `Html`/`SafeHtml` em `org.grox.http` (28 → 20 violações)
- [x] Fase 3: subprojetos Gradle `framework/grox-*`, com `framework` como agregador (mapa só por pacote, `META-INF` por jar, módulos sem as ASTs globais)
- [x] Fase 4: `sample-plain` (core + gorm + gsp + security, sem `grails-app/`)
  - [x] Pipeline HTTP do core (`HttpPipeline`/`HttpModule`): sessões, estáticos, management, access log, cabeçalhos, erros e segurança por `RequestFilter`; o Grails só pluga o `Router` dos controllers
  - [x] Rotas por closure e por anotação (`grox-dsl`), em virtual thread ou ator (padrão na configuração, troca por rota ou grupo), respostas no estilo Grails; `@Entity` para domain classes fora do `grails-app`
  - [x] `sample-plain`: core + gorm + gsp + security + dsl, sem `grails-app/` nem o módulo Grails
- [x] `application.groovy` e `runtime.groovy` como no Grails (`ConfigSlurper`, `environments { }`, mesclados ao yml; o Groovy vence o yml, como na ordem dos carregadores do Grails) e `controllerAnnotations.staticRules` na segurança; o `sample-plain` usa o `application.groovy` para as regras de acesso
- [ ] Tags `sec:` para apps sem o plugin spring-security
- [ ] Interceptors e recarga de classes nas apps do `grox-dsl`
- [x] DSL de HTML com o `MarkupBuilder` (ajustado para HTML5: elementos vazios, escape, `SafeHtml`), com layout SiteMesh e tags `g.*`, nos controllers (`render { }`, como no Grails) e nas rotas do `grox-dsl`
- [x] Views em Pug (`grox-pug`, pug4j com expressões em Groovy e TagLibs como elementos `ns:tag`) e Markdown (`grox-markdown`, `.md` como GSP + commonmark, front matter, `<md:render>`), pelo ponto de extensão `ViewEngine` do `grox-gsp`; no `sample-plain` (2026-10-13)
- [ ] Avaliar os jars de API do Spring (`spring-tx`, `spring-security-core`/`crypto`) no lugar dos shims, quando um candidato precisar
- [x] DSL de projeto (`grox-dsl`): `static app = { }` na classe da aplicação ou script com `GroxScript`; blocos `config` (padrões sob os arquivos), `security` (`user`, `rule`, chaves do plugin), `routes` (com `handlers` e `access:` por rota, grupo ou anotação, como `@Secured`) e `beans`
- [x] Benchmark A/B da modularização (base `ccde730` x `HEAD`, ver [benchmark.md](benchmark.md#modularização-antes-x-depois-2026-10-09)): atendimento sem diferença mensurável (64 conexões e latência com 1 conexão); subida igual no total (ver o perfil com JFR)
- [x] Subida perfilada com JFR: total igual ao de antes da modularização; o `scan` (closure por classe e predicados dinâmicos dos módulos) caiu de ~300 para ~150 ms
- [x] Benchmark Grails compat x `grox-dsl` na mesma app ([benchmark.md](benchmark.md#grails-compat-x-grox-dsl-2026-10-09)): a DSL atende de 1,4× a 2× mais; o `Autowirer.autowire` estático tirou ~30% do custo das actions com `concurrency: 'request'`
- [ ] Plugin Gradle `org.grox.plain` para apps sem `grails-app/`: `grox { modules 'gorm', 'gsp', 'security' }` vira as dependências dos módulos, `groovyOptions.parameters` e as pastas de views e recursos

## Documentação do usuário em mdBook

Iniciada em 2026-10-10 em `docs/livro/` (`docs/livro/build.sh` gera e confere links e âncoras). Regra: toda funcionalidade nova ou mudança de comportamento atualiza o livro no mesmo commit (mapa em `docs/livro/src/apendices/manutencao.md`). Livro completo em formato [mdBook](https://rust-lang.github.io/mdBook/) (`SUMMARY.md` + capítulos), para novos usuários e como referência para quem já usa. Os `docs/` atuais (arquitetura, guia, compatibilidade, desenvolvimento) são a fonte.

- [x] Estrutura do livro (`book.toml`, `src/SUMMARY.md`) e onde fica no repositório
- [x] Introdução: o que é o Grox, quando usar, visão da arquitetura
- [x] Trilha "migrar um projeto Grails": passo a passo, o que é compatível, diferenças, armadilhas, exemplos dos candidatos (apps A, B e C)
- [x] Trilha "usar sem Grails": core + gorm + gsp + security + `grox-dsl` (como o `sample-plain`), do zero até uma app completa
- [x] Referência de configuração: todas as chaves (Grox, `grails.plugin.springsecurity.*`, `grails.gorm.multiTenancy.*`, sessão, Redis, assets...) com padrões e exemplos
- [x] Guias por recurso (controllers, GORM, GSP/SiteMesh, segurança, multi-tenancy, services-ator, plugins reimplementados, recarga)
- [x] Recomendações (desempenho, implantação, segurança), FAQ e solução de problemas
- [x] JVM e memória na implantação (2026-11-26): onde passar as opções, heap e RSS de referência, limites em contêiner, GC e como medir
- [x] Exemplos completos e build do livro (`build.sh`; tutorial da livraria na trilha sem Grails)
- [ ] Revisão do livro por um leitor novo (seguir o início rápido e o tutorial do zero) e exemplos do livro compilados num teste
- [ ] Lacunas achadas ao escrever o livro, nas apps do `grox-dsl`: dados iniciais sem `BootStrap`, `grox.gsp.viewsDir` padrão `grails-app/views` (sem reload de views), binding de objeto em `@Handler` sem `validate()`/JSON, `grox.serverURL` separado de `grails.serverURL`
- [ ] `withHibernateFilter(nome) { }` do plugin hibernate-filter (hoje só `enableHibernateFilter`)

## Depois (sem prioridade definida)

- `hasOne`, `embedded`, herança de domain classes.
- Migrações de esquema (Flyway ou Liquibase) no lugar do `dbCreate: update`.
- `as XML`, marshallers de JSON customizados.
- Módulo separado para as domain classes, para compilar mais rápido em projetos grandes. No app A (1.382 classes, um módulo), mudar um controller, service ou classe de `src/` recompila 1 classe (2–3 s). Mudar a domain `Cidade`, referenciada por 16 arquivos, recompila **656** classes (~24 s): o Groovy é dinâmico, e o Gradle recompila tudo que depende da classe, direta ou indiretamente. Separar só as domain classes (a camada estável da recarga) resolveria; services, taglibs e controllers em módulos próprios não ganham nada. É preciso:
  - ~~um modo biblioteca~~: o plugin `org.grox.lib`;
  - ~~a recarga acompanhar também as classes desse módulo~~: feito (2026-11-04) para as bibliotecas `org.grox.lib` do mesmo build, ver `ReloadLibs` no plugin;
  - ligar a *compilation avoidance* do Groovy no Gradle (recurso em preview): mudança sem efeito na API (corpo de método, constraints, mapping) não recompila o módulo da app. No app-c, mudar um service do `app-c-core` recompila o `app-c-web` inteiro (2 min de ~3 min até a recarga).
- Recarga com HotSwap (a "camada 1", acima da recarga por gerações): um agente Java em development aplica `Instrumentation.redefineClasses` nas classes que mudaram. Depois disso, o framework limpa os caches que ficariam velhos: o metaclass do Groovy, as actions e o `allowedMethods` do `ControllerArtefact`, o autowiring de campos novos e os interceptors. Só volta à recarga por gerações quando a JVM recusar a mudança. A JVM padrão só troca corpo de método. Mudanças estruturais (métodos, campos e closures novos, o caso comum em Groovy) exigem o JetBrains Runtime com `-XX:+AllowEnhancedClassRedefinition`, que ainda não tem versão para o JDK 27. Ganho esperado: manter o estado dos services e a sessão intactos em mudanças de corpo de método.

## Pendências conhecidas

- Chamadas cíclicas entre services-ator travam (A → B → A).
- Fora do `run` com recarga, mudanças em `messages*.properties` exigem reiniciar (cache sem reload).
- O `servletContext` do BootStrap é `null`.
- Datas no JSON usam `yyyy-MM-dd'T'HH:mm:ssXXX` e `java.time` usa `toString()`. Ainda não é configurável.
- Tokens JWT revogados em memória valem só para um nó; use `grox.security.tokenRevocation.store: redis` com vários nós.
- Basic auth faz bcrypt a cada requisição (custo 10, ~50 a 100 ms). Para APIs de alto volume, prefira JWT.
- O GORM é global na JVM (`Gorm`/`Persistence` estáticos): só uma aplicação por processo.
- O `validate()` de um argumento domain de action também roda em ações de leitura (`show`), o que custa uma consulta de `unique`.
