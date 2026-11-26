# Arquitetura

## Módulos

| Módulo | Papel |
|---|---|
| `framework/` | O framework, em subprojetos `framework/grox-*` (`grox-core`, `grox-gorm`, `grox-gsp`, `grox-assets`, `grox-security`, `grox-security-gorm`, `grox-grails`, `grox-grails-plugins`, `grox-dsl`, `grox-pug`, `grox-markdown`; ver [modularizacao.md](modularizacao.md)). O projeto `framework` agrega todos (as apps dependem dele) e guarda os testes. Pacote principal `org.grox`, mais as classes de compatibilidade em `grails.*` |
| `sample-app/` | Aplicação de exemplo no layout do Grails (`grails-app/...`) e testes de integração |
| `gradle-plugin/` | Plugin `org.grox.app` (build incluído): layout `grails-app/` e a tarefa `compileGsp` |
| `candidates/` | Lugar das apps Grails importadas localmente para migração (não versionadas; ver o capítulo "Migração" do livro) e o `sanitize-yml.py` |
| `sample-plain/` | Aplicação sem `grails-app/` nem o módulo Grails: core + gorm + gsp + security + `grox-dsl` (rotas por closure e anotação), com views também em Pug e Markdown |
| `bench/` | Gerador de carga (`LoadGen`) e `run.sh`. Ver [benchmark.md](benchmark.md) |

## Pacotes do framework

Cada pacote pertence a um módulo (subprojeto `framework/grox-*`; ver [modularizacao.md](modularizacao.md)); o `ArquiteturaTest` garante as fronteiras.

| Pacote | Responsabilidade |
|---|---|
| `org.grox` | `GroxApp.run(Application, args)`, o ponto de entrada |
| `org.grox.core` | `GroxRuntime` (orquestra a subida pelas fases dos módulos `GroxModule`), `GroxApplication` (configuração, ambiente e artefatos por tipo; o bean `grailsApplication` é a fachada `org.grox.web.GroxGrailsApplication`), `ArtefactType`, `Config` (YAML), `ClassScanner`, `Autowirer` (injeção por nome), `ClassUtils` |
| `org.grox.core.reload` | Recarga em desenvolvimento: `DevReloader` (gerações, monitoramento e Gradle contínuo), `AppClasses` (retrato das classes com ASM: camadas e diferenças), `AppClassLoader`, `SwitchableDispatcher` (entrada do Netty entre gerações), `@Reloadable` |
| `org.grox.http` | Netty (`NettyServer`, `NettyHttpHandler`), `GroxRequest`/`GroxResponse`, `GroxParams`, sessão (`GroxSession`, `SessionStore`), `FlashScope`, `StaticResources`, `RequestScope`/`CurrentRequest` (requisição corrente), `Endpoint` (endpoints especiais: management, console), `Html`/`SafeHtml` |
| `org.grox.routing` | DSL do `UrlMappings` (`UrlMappingsBuilder`), `UrlMapping` (padrão → regex, reverse), `UrlMappingsHolder` (match e reverse), `LinkGenerator`, `RouteTarget` (destino de rota; o `ControllerArtefact` é um) |
| `org.grox.web` | `WebDispatcher` (roteador), `ControllerArtefact`, dispatchers de atores, `ControllerExecutor`, `ControllerSupport` (render/redirect/respond/withFormat), `CustomEditors`, `Formats`, `WebRequest`, `RequestContext` |
| `org.grox.web.api` | Traits injetados por AST: `ControllerApi`, `ServiceApi` |
| `org.grox.web.json` | `JsonSupport` e o extension module `obj as JSON` |
| `org.grox.service` | `ServiceRegistry` e `ServiceActorProxy` (service como ator) |
| `org.grox.view` | `GspViews` (renderiza views e templates), `ViewRequest` (a requisição vista pelas views; o `WebRequest` a implementa), `GspContributor` (TagLibs e imports das GSPs vindos dos módulos), `CodecExtensions` (`encodeAsHTML()`...) |
| `org.grox.view.gsp` | Motor GSP: `GspParser` → `GspCompiler` → classe `GspPage`; `GspTemplateEngine` (cache e reload), `GspRenderContext`, `ViewLocator` |
| `org.grox.view.taglib` | `TagLibRegistry`, `TagNamespace`, trait `TagLibApi`, TagLibs nativas (`ApplicationTagLib`, `FormTagLib`, `RenderTagLib`, `ValidationTagLib`, `RenderingTagLib`). As dos módulos: `SecurityTagLib` (`org.grox.plugins.springsecurity`), `AssetTagLib` e `AssetMethodTagLib` (`org.grox.plugins.assetpipeline`) |
| `org.grox.view.layout` | `SiteMeshLayouts`, a integração com o SiteMesh 3 |
| `org.grox.session` | `SessionRepository` (`MemorySessionRepository`, `RedisSessionRepository` com Lettuce), `RequestSession` (carrega uma vez e grava no fim), `SessionSerializer` |
| `org.grox.security` | `SecurityFilter` (cadeia de segurança), `SecurityConfig`, `AccessRule`/`AccessExpression`, `Jwt`, `Passwords` (bcrypt), `UserDetailsService` (memória, composto), `SecurityContextHolder`, `LoginPage`; pontos de extensão `PasswordCodec`, `PasswordUpdater`, `SecurityListener`, `AccessRuleSource`, `TokenStore` |
| `org.grox.security.gorm` | Segurança sobre o GORM: `GormUserDetailsService`, `GormTokenStorage`, `UserTenantResolver` |
| `org.grox.plugins.springsecurity` | API do spring-security-core/rest sobre o motor: `SpringSecurityModule`, adaptadores de senha e de `userDetailsService`, exceção do login na sessão, `RestTokenCreationEvent`, `@Secured`, `principal as GrailsUser` |
| `org.grox.data.tenant` | `TenantSupport`: ponte entre `Tenants` e o `CurrentTenantIdentifierResolver` do Hibernate |
| `org.grox.management` | `Management` (health, info, metrics, prometheus), `Metrics`, `AccessLog` |
| `org.grox.plugins.*` | Reimplementações mínimas dos plugins do Grails (só o que os projetos usam): `console` (`WebConsole`, página em `resources/org/grox/plugins/console/console.html`), `quartz` (`JobScheduler`, `CronExpression`, trait `JobApi`), `excel` (a API fica em `pl.touk.excel.export`, sobre o POI) |
| `org.grox.assets` | `AssetProcessor` (diretivas e `url()`), `AssetCompiler` (build: digest e manifest), `AssetServer` (`/assets/**`: dev na hora, produção pré-compilado) |
| `org.grox.async` | `AsyncSupport` (tarefas em virtual threads com propagação de contexto), `GroxPromise`; API `grails.async.Promise`/`Promises` |
| `org.grox.i18n` | `MessageSource`: `messages*.properties` da app, mais as mensagens padrão de validação do framework |
| `org.grox.data` | Camada estilo GORM: `DomainClass` (metadados), `OrmXml` (mapeamento Hibernate), `Datastore` (Hibernate + HikariCP), `Persistence` (sessão e transação via `ScopedValue`), `GormEntity` (trait), `GormStaticApi`, `GormInstanceApi`, `DynamicFinder`, `DomainJson`; em `org.grox.data.binding`, o `DataBinder` (binding de mapas, `bindData`, `@BindingFormat`, `typeMismatch`) |
| `org.grox.data.validation` | `ConstraintsBuilder`, `Validator`, `ValidationErrors`, `FieldError`, trait `ValidationSupport` |
| `org.grox.ast` | `ArtefactTransformation`, a transformação AST global |
| `grails.boot`, `grails.core` (`GrailsApplication`, `GrailsClass`, `ApplicationContext`), `grails.gsp.PageRenderer`, `grails.rest.RestfulController`, `grails.databinding`, `grails.gorm.services`, `grails.events.annotation.gorm`, `org.grails.web.json`, `org.grails.web.util`, `org.grails.datastore.mapping.engine.event`, `org.springframework.*` (`HttpStatus`, `@Autowired`, `MessageSource`, `RequestContextHolder`...), `pl.touk.excel.export` | Compatibilidade levantada no piloto |
| `grails.converters`, `grails.util`, `grails.validation`, `grails.gorm.transactions`, `grails.gorm` (`MultiTenant`), `grails.gorm.multitenancy` (`Tenants`, `@CurrentTenant`, `@WithoutTenant`, `@Tenant`), `grails.plugin.springsecurity` (`SpringSecurityService`, `SpringSecurityUtils`, `@Secured`), `org.grails.datastore.mapping.multitenancy` (`TenantResolver`, resolvers, `TenantNotFoundException`) | Classes com os nomes do Grails e dos plugins, para o código com esses imports compilar sem mudanças |

## Fluxo de uma requisição

```
 Netty event loop                 Atores (virtual threads)
 ─────────────────                ───────────────────────────────────────────────
 HttpServerCodec
 HttpObjectAggregator
 NettyHttpHandler ──GroxRequest──► WebDispatcher.dispatch()   (no event loop; só CPU)
   │                               ├─ _method / X-HTTP-Method-Override, caminho seguro
   │                               ├─ SecurityFilter (virtual thread): Bearer/Basic/sessão,
   │                               │    login/logout/JWT, CSRF, @Secured → interceptUrlMap
   │                               ├─ /static/** → StaticResources (virtual thread)
   │                               ├─ UrlMappingsHolder.match()  → RouteMatch
   │                               └─ ControllerDispatcher.submit(Invocation)
   │                                        │ sendAndGet
   │                                        ▼
   │                               Ator do controller (pool ou por requisição)
   │                                 ControllerExecutor.execute()
   │                                   WebRequest + ScopedValue (RequestContext, autenticação, sessão Hibernate)
   │                                   allowedMethods → interceptors.before → binding → action
   │                                   └─ service-ator? proxy → sendAndGet → ator do service
   │                                   action devolveu Promise? libera o ator; ao completar (virtual thread):
   │                                   view adiada → interceptors.after (model/view) → GSP + SiteMesh
   │                                   → interceptors.afterView
   │                                        │
   │◄──── CompletableFuture<GroxResponse> ───┘  (erros → mapeamentos "403"/"404"/"500")
   │      RequestSession.commit() (virtual thread): grava a sessão (memória/Redis) + cookie
 escreve na ordem de chegada (pipelining) e fecha ou mantém a conexão (keep-alive)
```

Pontos importantes:
- **O roteador não é um ator.** Resolver a rota é uma função pura sobre dados imutáveis, e um ator único serializaria todo o tráfego.
- **As respostas saem na ordem das requisições.** O `NettyHttpHandler` numera as requisições de cada conexão e segura as respostas até poder escrevê-las em ordem.
- **Os erros viram novo despacho.** `sendError(404)` ou uma exceção fazem o `WebDispatcher` procurar o mapeamento `"404"`/`"500"`. Se ele aponta para um controller, a requisição é redespachada para ele. Senão, sai a página de erro padrão (HTML ou JSON, conforme o `Accept`), com stack trace em desenvolvimento.

## Concorrência

| Peça | Modelo |
|---|---|
| Netty | Event loops. Nunca bloqueiam: não executam actions, GSP nem IO de arquivo |
| Controller, modo `pool` (padrão) | N atores (`groovy.concurrent.Actor.reactor`), cada um com **sua instância** do controller. O despacho escolhe o ator com menos mensagens pendentes, a partir de um índice round-robin |
| Controller, modo `request` | Um ator e uma instância novos por requisição, descartados no fim |
| Service comum | Singleton compartilhado. Precisa ser thread-safe |
| Service com `static actor = true` | A instância real fica atrás de um ator. Quem injeta recebe um **proxy-subclasse** (gerado com `ProxyGenerator`), então a injeção tipada funciona. Cada chamada vira `sendAndGet` e **bloqueia a virtual thread** até a resposta. O estado interno dispensa sincronização. A sessão de dados de quem chama vai junto na mensagem |
| Service com `@Transactional` | Proxy-subclasse. Os métodos transacionais rodam em `Persistence.withTransaction`, que participa de uma transação já aberta ou abre uma nova |
| Sessão Hibernate | **Uma por invocação de action**, incluindo a renderização da view (lazy loading funciona na GSP), vinculada em `Persistence.CURRENT` (`ScopedValue`). Ela é propagada para services-ator: como quem chama fica bloqueado, a sessão nunca é usada por duas threads ao mesmo tempo. Fora de uma action (BootStrap, testes, threads próprias), cada chamada do GORM abre e fecha a própria sessão |
| TagLibs | Singletons. O `out` vem do `GspRenderContext` via `ScopedValue` |

Os atores rodam em virtual threads, então bloquear dentro de uma action (JDBC, chamada a um service-ator) é aceitável.

**Risco de deadlock:** um service-ator A que chama um service-ator B, que por sua vez chama A de volta, trava. Evite ciclos entre services-ator.

O contexto de execução vive em `ScopedValue` (Java 25+):

| Valor | O que guarda | Vai para services-ator? |
|---|---|---|
| `CurrentRequest.VALUE` (= `RequestContext.CURRENT`) | requisição (`RequestScope`; no Grails, o `WebRequest`) | não |
| `GspRenderContext.CURRENT` | renderização | não |
| `Persistence.CURRENT` | sessão Hibernate | sim |
| `SecurityContextHolder.CURRENT` | autenticação | sim |
| `Tenants.CURRENT` | tenant forçado por `withId`/`withoutId` | sim |

Os três últimos são capturados por `ContextPropagation` na chamada a um service-ator e vinculados na thread do ator. Nas tarefas assíncronas (`grails.async`) vão a requisição, a autenticação e o tenant, sem a sessão Hibernate: cada tarefa abre a sua sob demanda. Cada módulo declara os seus valores num `org.grox.core.ContextProvider` (`GormContextProvider`, `SecurityContextProvider`, `RequestContextProvider`), registrado em `META-INF/services`.

Transação e tenant dos services (`@Transactional`, `@ReadOnly`, `@WithoutTenant`, `@CurrentTenant`, `@Tenant`) vêm do `GormServiceInterceptor`, um `org.grox.service.ServiceInterceptor` do módulo GORM; o `ServiceProxies` do core só monta o proxy, o ator e a cadeia de interceptadores.

## Descoberta de artefatos e AST

1. **Varredura em runtime:** `GroxApplication.scan()` varre o pacote da classe `Application` e classifica pelo sufixo do nome: `*Controller`, `*Service`, `*TagLib`, `*UrlMappings`, `*BootStrap`.
2. **AST na compilação:** a transformação global `ArtefactTransformation` roda em `SEMANTIC_ANALYSIS` e atua pelo **diretório do fonte**:
   - `grails-app/controllers/*Controller` ganha o trait `ControllerApi`, e cada método público recebe `@org.grox.web.Action(parameterNames = [...])`, porque os nomes dos parâmetros são usados no binding;
   - `grails-app/services/*Service` ganha o trait `ServiceApi` (`log`);
   - `grails-app/taglib/*TagLib` ganha o trait `TagLibApi`.

   - `grails-app/controllers/*Interceptor` ganha o trait `InterceptorApi` (e as constantes de `Ordered`).
   - `grails-app/domain/*` ganha o trait `GormEntity`, `id`/`version`/`tenantId`, as associações e os construtores.

   Uma segunda transformação global, `WhereQueryTransformation`, reescreve os closures de `where`/`whereAny`/`find`/`findAll` em **qualquer** código (controllers, services, testes) para montar critérios (`org.grox.data.query.Where`). A domain class é reconhecida pela interface `GormEntity` ou pelo diretório `grails-app/domain`.

   Cada transformação é registrada no `META-INF/services/org.codehaus.groovy.transform.ASTTransformation` do seu módulo (`grox-grails` e `grox-gorm`) e vale para qualquer projeto que tenha o módulo no classpath de compilação. Os próprios módulos do framework compilam sem elas (`framework/gradle/compilador-modulos.groovy`).

## Ciclo de vida (`GroxRuntime.start`)

O `GroxRuntime` só orquestra. O trabalho de cada parte fica num **módulo** (`org.grox.core.GroxModule`), descoberto por `ServiceLoader` (`META-INF/services/org.grox.core.GroxModule`) e chamado em cada fase na ordem de `order`:

| Ordem | Módulo | Pacote |
|---:|---|---|
| 50 | `HttpModule`: o `HttpPipeline` (atendimento HTTP comum), sessões, estáticos, management, access log, cabeçalhos de segurança | `org.grox.http` |
| 100 | `GormModule`: datastore, beans de dados, Data Services, `@Listener`, resolver de tenant | `org.grox.data` |
| 200 | `SecurityModule`: `SecurityFilter`, senhas, fontes de usuários | `org.grox.security` |
| 300 | `GspModule`: TagLibs e motor GSP | `org.grox.view` |
| 350 | `AssetsModule`: servidor de assets e `assetResourceLocator` | `org.grox.assets` |
| 500 | `GrailsModule`: plugins `*GrailsPlugin`, `resources.groovy`, `ApplicationContext`, UrlMappings, `WebContext`, interceptors, controllers, BootStrap; pluga no pipeline o `ControllerRouter` e a página de login (`GrailsLoginView`) | `org.grox.web` |
| 600–700 | Plugins: spring-security, asset-pipeline (`assetResourceLocator`), redis, rabbitmq, rendering, auditlog, jobs, console | `org.grox.plugins.*` |

Fases, em ordem:

1. Ambiente (`org.grox.core.Env`; o `grails.util.Environment` delega para ele), `Config` (`application.yml`) e carga dos módulos; varredura dos artefatos pelos tipos (`ArtefactType`) que os módulos registram em `artefactTypes`: o core reconhece só `Service`, o GORM `DataService` e `Domain`, o GSP `TagLib`, o Grails `Plugin`, `UrlMappings`, `BootStrap`, `Controller` e `Interceptor`, e o quartz `Job`. Cada classe fica no primeiro tipo que a aceitar (marcadores antes de sufixos). Depois, `prepare` (o Grails cria o `grailsApplication` e o `Holders`).
2. `initialize`: o Hibernate começa a subir em paralelo (só se houver domain classes ou `dataSource`), a segurança é configurada.
3. Beans: `contributeBeans` dos módulos, os services da aplicação (instanciados, com proxy se forem ator ou `@Transactional`; um service prevalece sobre um bean de módulo com o mesmo nome), `contributeApplicationBeans` (Data Services, `doWithSpring`, `resources.groovy`), injeção, `beansReady`, `@PostConstruct` e `contextReady`.
4. `configureWeb`: o `HttpModule` monta o `HttpPipeline` (sessões, estáticos, management), a segurança pluga o filtro (`SecurityRequestFilter`), os assets se somam aos recursos, depois TagLibs e GSP, UrlMappings, `WebContext`, interceptors, controllers e o roteador do Grails.
5. `awaitReady` (espera o Hibernate), `bootStrap` (`BootStrap.init` numa sessão de dados, com `environments { }`), `started` (jobs).
6. O primeiro `createDispatcher` não nulo (o do `HttpModule`: o pipeline) atende as requisições; Netty.

O `HttpPipeline` faz o que é comum a qualquer aplicação: `_method`, caminho seguro, métodos conhecidos, o `RequestFilter` (segurança) fora do event loop, os `ResourceHandler` (estáticos, assets), os `Endpoint` (management, console), a rota do `Router`, páginas de erro (as do roteador ou a padrão), exceções com status próprio (`GroxModule.exceptionStatuses`, como o tenant ausente → 403), gravação da sessão, cabeçalhos, access log e métricas. O `Router` do módulo Grails (`ControllerRouter`) casa o UrlMappings com os controllers e envia a invocação aos atores.

O `close()` (também registrado como shutdown hook) chama `shuttingDown`, para de aceitar conexões e espera as requisições em andamento; depois, na ordem inversa dos módulos, `stop` (jobs, `BootStrap.destroy`, atores de controller), fecha o Netty e os services, e `release` (sessões HTTP, conexões dos plugins, `Gorm.reset()`). A recarga em desenvolvimento usa as mesmas fases com `Shutdown.reload`.

O runtime expõe os módulos com `runtime.module(GormModule)`; por compatibilidade, as propriedades dos módulos também são lidas direto (`runtime.datastore`, `runtime.webContext`).

## Camada de dados

```
 grails-app/domain/Usuario.groovy
   AST (compilação): trait GormEntity, Long id/version, Set das coleções de hasMany,
                     referências de belongsTo, construtores () e (Map)
   runtime: DomainClass.buildAll()   lê hasMany, belongsTo, mappedBy, transients, mapping, constraints
            OrmXml.generate()        orm.xml Jakarta Persistence 3.2 (access FIELD, metadata-complete)
            Datastore                HikariCP + MetadataSources.addInputStream(orm.xml) → SessionFactory
            Gorm.init()              GormStaticApi por classe e verificador de unique
```

- **Mapeamento:** as entidades são POGOs sem anotações, mapeadas por um `orm.xml` gerado em runtime. Os nomes físicos ficam em snake_case (`CamelCaseToUnderscoresNamingStrategy`) e palavras reservadas são citadas.
- **Métodos estáticos:** os do trait (`Usuario.get`, `list`, dynamic finders via `$static_methodMissing`) delegam para `Gorm.api(this)`. Em método estático de trait, `this` é a classe concreta.
- **Consultas:** `list`, os finders e `findAllWhere` usam a Criteria API. `sort` só aceita propriedades persistentes. `executeQuery`, `findAll(hql)` e `executeUpdate` usam HQL.
- **`save()`:** timestamps, `beforeInsert`/`beforeUpdate`, `validate()` e depois `persist`, ou `merge` se a entidade estiver destacada. Tudo em `withTransaction`: participa da transação aberta ou faz commit ao final.
- **Validação:** `Validator` aplica as constraints com os mesmos códigos e argumentos do Grails. Em domain classes, as propriedades são `nullable: false` por padrão (menos `dateCreated`/`lastUpdated`). As mensagens vêm da aplicação e, na falta delas, de `org/grox/i18n/messages*.properties`.

## Pipeline de views

```
 action retorna Map / respond / render(view:)
   → GspViews.render(path, model, wr)
       GspTemplateEngine.find(path)            cache; reload se o arquivo mudou (dev)
         ViewLocator: grails-app/views (dev) ou classpath views/
         GspParser  → nós (texto, ${}, <% %>, <%@ %>, <ns:tag>)
         GspCompiler → script Groovy (base GspPage), linhas marcadas //@gsp:N
         GroovyClassLoader → classe
       GspPage.run() com GspBinding (modelo + implícitos) dentro de GspRenderContext
       se text/html: SiteMeshLayouts.decorate()
         TagBasedContentProcessor extrai title/head/body/meta.*/page.*
         escolhe o layout → renderiza /layouts/<nome>.gsp com o conteúdo da página
         o SiteMesh processa <sitemesh:write> e faz merge dos atributos do <body>
   → response
```

**Encoding:** `${expr}` e `<%= %>` são codificados em HTML. Não passam pela codificação:
- `SafeHtml`: o `raw(x)`, a saída de tags e `encodeAsRaw()`;
- o texto literal da página.

**Tags de sintaxe:** `g:if`, `g:elseif`, `g:else`, `g:unless`, `g:while`, `g:each` e `g:set` são compiladas como estruturas de controle Groovy, não como chamadas de tag. As demais tags viram `invokeTag(ns, nome, attrs, corpo)`.

## Decisões registradas

| # | Decisão | Motivo |
|---|---|---|
| D1 | Emular a API do Grails em vez de reusar os jars | Os jars do Grails 8 (GSP, GORM) são compilados com Groovy 5 e usam `org.codehaus.groovy.runtime.callsite.CallSite`, que **não existe no Groovy 6**. Foi testado: o GORM Hibernate7 falha ao compilar uma `@Entity`. Também trariam o Spring junto |
| D2 | Netty 4.2 (estável) | A 5.0 ainda é alpha |
| D3 | Versões estáveis: Hibernate 7.4.x, SiteMesh 3.2.3 | Pedido do projeto |
| D4 | Roteador fora de ator | Evita um gargalo serial |
| D5 | Concorrência do controller configurável (`pool`/`request`) | Pedido do projeto. `pool` reaproveita instâncias |
| D6 | Só services marcados (`static actor = true`) viram atores | Pedido do projeto. Ator serializa as chamadas, o que é bom para estado e ruim para throughput |
| D7 | AST global pelo diretório `grails-app/...` | Mesma convenção do Grails, sem herança nem anotações no código do usuário |
| D8 | Motor GSP próprio, com SiteMesh só para extração e decoração | O GSP do Grails depende de Spring e Groovy 5. O núcleo do SiteMesh 3 funciona sem Servlet |
| D9 | Suporte a JSON na primeira versão (`respond`, `as JSON`, `withFormat`) | Pedido do projeto |
| D10 | GORM próprio mapeado por `orm.xml` gerado em runtime (não por anotações geradas pela AST) | O `static mapping` e as constraints são closures, avaliáveis só em runtime. Assim a AST fica mínima e o código do usuário, sem anotações |
| D11 | Sessão por invocação de action e propagada aos services-ator | Lazy loading nas views (open session in view) sem `ThreadLocal`. Como o chamador está bloqueado, o uso é sequencial |
| D12 | HikariCP como pool | Estável e padrão de mercado. O pool interno do Hibernate não é para produção |
| D13 | Sessão plugável com gravação diferencial no fim da requisição (modelo Spring Session). Redis via Lettuce, serialização Java | Lettuce usa Netty, então não traz outra pilha de IO. Serialização Java mantém a semântica de "qualquer objeto `Serializable`" |
| D14 | Segurança com as chaves e a API do spring-security-core e do spring-security-rest, executada como etapa assíncrona do `WebDispatcher` | Compatibilidade de configuração e código com apps Grails. Sessão, bcrypt e GORM não podem bloquear o event loop |
| D15 | `@Secured` antes do `interceptUrlMap`, que é avaliado na primeira regra que casa | A anotação é mais específica. A ordem explícita no yml é previsível |
| D16 | Multi-tenancy só DISCRIMINATOR, com `@TenantId` nativo do Hibernate via `<tenant-id>` no `orm.xml` e o tenant vindo do usuário | Pedido do projeto. O filtro nativo cobre HQL, Criteria e carga por id, sem reescrever consultas |
| D17 | JWT HS256 implementado sem biblioteca | Tokens simples (assinatura e expiração). Evita uma dependência a mais |
| D18 | `where { }` por AST que reescreve o **corpo** do closure (a closure é mantida) | O `==` e o `&&` não são capturáveis em runtime. Manter a closure preserva o escopo de variáveis locais. Com a domain class conhecida os erros são de compilação; em receptores desconhecidos, a reescrita é silenciosa |
| D19 | Um motor único (`QueryRunner` + `Criterion`, JPA Criteria) para where, createCriteria, finders e tenancy | Joins, conversão de tipos e paginação num só lugar. O filtro de tenant do Hibernate vale para todos |
| D20 | Views das actions adiadas até depois dos interceptors | Permite `after()` alterar `model`/`view`, como no Grails |
| D22 | Sessão Hibernate preguiçosa por action | Actions sem banco não pagam abertura de sessão nem resolução de tenant (medido: era o maior custo por requisição) |
| D23 | Desligamento conta requisições no handler do Netty até a escrita terminar | O contador das métricas zerava antes da escrita, e o fechamento dos event loops descartava a resposta |
| D24 | `@CompileStatic` só no código quente e sem dinamismo do framework; o código do usuário continua dinâmico | O despacho dinâmico era cerca de 60% da CPU da página GSP. Anotar só onde não há API dinâmica mantém a compatibilidade com o estilo Grails |
| D25 | GSPs pré-compiladas por tarefa Gradle e índice JSON no classpath | Erro de GSP no build, sem custo de compilação na primeira requisição e sem precisar das fontes em produção |
| D27 | Actions assíncronas pela API `grails.async`; tarefas com contexto propagado, mas sessão de dados própria | Libera o ator durante esperas longas, como o async do Grails. A Session do Hibernate não é thread-safe, e a action e a tarefa podem rodar ao mesmo tempo |
| D26 | Asset pipeline próprio com as convenções do Grails | O asset-pipeline-core estável é incompatível com o Groovy 6 (`CallSite`) e a 5.2.0-RC exige GraalVM (~130 MB). Decisão do projeto: compatibilidade de arquivos e tags sem os pré-processadores |
| D21 | Validação com flush manual; entidade inválida fica somente leitura | Senão o auto-flush da consulta de `unique`, ou o commit, gravaria o estado inválido (bug encontrado nos testes) |
| D28 | Plugins do Grails reimplementados em `org.grox.plugins.*`, só no que os projetos usam; a API pública mantém o pacote original (ex.: `pl.touk.excel.export`) | Os jars dos plugins dependem de Spring e do Groovy 5. Reimplementar só o usado mantém o núcleo pequeno. Dependências pesadas (POI) ficam `compileOnly` e a app as declara |
| D29 | Jobs com agendador próprio (cron no formato do Quartz) em vez do Quartz | O uso real é `cron` e `simple`. Um `ScheduledExecutorService` com virtual threads resolve sem a dependência nem o seu modelo de threads |
| D30 | Piloto de migração com o código importado fora do git, e as mudanças da app como patches | O repositório da app continua sendo a fonte. As mudanças necessárias ficam explícitas, e o objetivo é zerá-las no framework |
| D31 | O resolver de tenant da aplicação roda numa sessão de dados isolada | O Hibernate pede o tenant ao abrir a sessão. Resolvers que consultam o banco (usuário logado) abririam outra sessão recursivamente |
| D32 | CSRF continua ativo por padrão (o Grails não ativa) | Segurança primeiro. A migração documenta `csrf.enabled: false` ou o uso de `<g:form>` |
| D33 | Hibernate sobe numa thread própria, em paralelo com o resto da inicialização; `getSessionFactory()` espera | Era metade da subida, em série. Bloquear no acesso deixa a ordem de inicialização correta sem coordenação explícita |
| D34 | Proxy lazy só para entidades alvo de many-to-one (`grox.data.proxies: auto`) | Com os traits do Grox, cada proxy sobrescreve ~100 métodos. A geração era o maior custo do Hibernate, e proxy só é usado nessas associações e no `load()` |
| D35 | Subida rápida pela JVM: JIT só C1 no `run` e AOT cache (JDK 25+) na distribuição | Sem mudança de código nem de comportamento. O AOT cache exige classpath de JARs, por isso fica na distribuição e não no `run` |
| D36 | `dbCreate: update` seguro por padrão: cria, mas nunca altera colunas (dialects com `supportsAlterColumnType() = false`) | No piloto, o `update` do Hibernate 7 tentou `modify column` num banco legado (truncamento, `not null`). O GORM clássico nunca fazia isso. `grox.schema.updateScript` mostra o DDL sem executar |
| D37 | Naming em snake_case também para identificadores entre aspas | Com `auto_quote_keyword`, `User` virava a tabela `` `User` `` e `userCriado` virava `userCriado_id`, divergindo do GORM e criando tabelas e colunas novas num banco existente |
