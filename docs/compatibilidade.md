# Compatibilidade com o Grails

Legenda: ✅ suportado · 🟡 parcial · ❌ ausente (ver [roadmap](roadmap.md))

## Estrutura e convenções

| Recurso | | Observação |
|---|---|---|
| Layout `grails-app/{controllers,services,taglib,views,i18n,conf,init}` | ✅ | Configurado no `build.gradle` |
| Descoberta por sufixo (`*Controller`, `*Service`, `*TagLib`, `UrlMappings`, `BootStrap`) | ✅ | No pacote da classe `Application` |
| Layout `grails-app/` | ✅ | Também `grox-app/` (detectado) ou outro nome com `grox { appDir }` (extensão Grox) |
| Autocomplete no IDE (o plugin Grails do IntelliJ) | 🟡 | Descritores GDSL nos jars: GORM tipado e dynamic finders nas domains, a API de controllers/services/TagLibs/jobs e dos `@Handler`. `GormEntity<D>` genérica para `implements` explícito. Finders combinados (`And`/`Or`) não aparecem |
| `grails-app/domain` | ✅ | GORM próprio sobre Hibernate 7.4 |
| `application.yml` + `environments`, `application.groovy` e `runtime.groovy` (`ConfigSlurper`, mesclados ao yml) | ✅ | |
| `spring.config.import` (`classpath:`, `file:`, `optional:`; `.yml` e `.groovy`; string com vírgulas ou lista) | ✅ | O importado vence quem o importou, como no Spring Boot. Sem `configtree:`, curingas nem perfis do Spring |
| Perfil `grox` (`application-grox.yml`/`.groovy`) | ✅ (extensão Grox) | Sempre ativo no Grox, nunca no Grails; vence os demais arquivos. Outros perfis do Spring (`spring.profiles.active`) ❌ |
| Build paralelo dentro do repositório Grails (`grox { source(dir) }`, `patches`) | ✅ (extensão Grox) | Plugin Gradle: compila o código original sem cópia, com overrides no projeto e patches em `build/grox-patched` |
| `grails.util.Environment` | ✅ | `current`, `executeForCurrentEnvironment`; `environments {}` no BootStrap |
| Plugins, profiles, `grails` CLI | ❌ | O plugin Gradle `org.grox.app` cobre o layout e o build. Artefatos compartilhados (o papel de um plugin do Grails): biblioteca `org.grox.lib` |
| Scaffolding dinâmico (`static scaffold = Livro`) e tags `f:` do plugin fields | 🟡 | Actions do `RestfulController` e views geradas dos templates (`src/main/templates/scaffolding/`). Geração de código (`generate-all`), `scaffold = true` e os templates `_fields/` por propriedade ❌ |
| Artefatos fora do pacote da aplicação; `messages.properties` de plugins mesclados | ✅ | Pelo índice `META-INF/grox/packages` gerado pelos plugins Gradle |
| Actuator (health, info, metrics, prometheus) | ✅ | Formato próprio (não é o do Micrometer) |
| Plugin console (`/console`) | ✅ | Editor simples (sem CodeMirror), histórico no navegador, rollback opcional. Arquivos salvos no servidor ❌. Ativado por `grox.console.enabled` ou `grails.plugin.console.enabled` |
| Plugin quartz (jobs em `grails-app/jobs`) | ✅ | `static triggers` com `cron` (formato do Quartz, sem `L`/`W`/`#`) e `simple`, `static concurrent`; jobs como beans (nome completo da classe) e `getArtefacts(JobArtefactHandler.TYPE)` com `GrailsJobClass`. Trigger `custom`, `JobExecutionContext`, `quartzScheduler` e clustering ❌ |
| Plugin audit-logging (`Auditable`, `grails.plugin.auditLog.*`, `auditRequestResolver`, `AuditLogContext`, gravação depois do commit, `asyncWrite`) | ✅ | `Stampable` compila, mas não grava `createdBy`/`lastUpdatedBy`. Vários datasources ❌ |
| Plugin redis (`redisService`: `withRedis`, `memoize*`, `memoizeObject`, `withPipeline`, `withTransaction`, conexões e sentinel) | ✅ | A app declara o jedis. `memoizeDomainList`/`memoizeDomainObject` e anotações `@Memoize` ❌ |
| Plugin rabbitmq-native (`rabbitMessagePublisher.send`, `rabbitmq.connections/exchanges/queues`) | 🟡 | A app declara o amqp-client. Consumers, RPC e confirms ❌ |
| Plugin rendering (`pdfRenderingService.render`, `renderPdf`) | ✅ | Sobre Flying Saucer (a app declara o flying-saucer-pdf). Imagens (GIF/PNG/JPEG) ❌. Recursos do template só do próprio servidor, de `data:` e de `grox.rendering.allowedUrls` |
| Plugin excel-export (`XlsxExporter`, `WebXlsxExporter`) | ✅ | Sobre POI 5 (a app declara `poi-ooxml`). Subconjunto: `putCellValue`, estilos `bold`/`centre`/`thickBorder`/`left`/`right`, `setWorksheetName`, `setResponseHeaders`, `save` |
| `grails-app/conf/spring/resources.groovy` | ✅ | `nome(Classe)`, argumentos de construtor, `{ prop = ...; dep = ref('x') }`. Outros recursos do DSL do Spring ❌ |
| `application.yml` com `${VAR}` / `${VAR:padrão}` | ✅ | Propriedade de sistema, variável de ambiente ou outra chave |
| `grails.boot.GrailsApp`, `Holders`, `grailsApplication.controllerClasses`/`getArtefacts(tipo)` (`GrailsClass[]`)/`config.getProperty('a.b')`, `applicationContext.getBean(Tipo)` | ✅ | |
| Beans `messageSource`, `groovyPageRenderer`, `assetResourceLocator`, `sessionFactory` (`currentSession`), `dataSource` | ✅ | |

## Controllers

| Recurso | | Observação |
|---|---|---|
| Actions como métodos, parâmetros por nome, command objects | ✅ | Actions como propriedades closure (Grails 2) também, sem parâmetros |
| `params` (conversões, aninhado), `request.JSON`, `session`, `flash` | ✅ | `request.json` também (extensão Grox) |
| Upload multipart: `request.getFile`, `MultipartFile` (nome do Spring), `g:uploadForm`, binding em `byte[]` | ✅ | Em memória (limite `grox.http.maxContentLength`); streaming para disco ❌ |
| `render` (text, view, template, status, contentType, file, `as JSON`; `text: null` envia `null`, como no Grails) | ✅ | |
| Views em Pug (`.pug`, expressões em Groovy, TagLibs como elementos) e Markdown (`.md` como GSP + commonmark, `<md:render>`) | ✅ | Extensão Grox (`grox-pug`, `grox-markdown`); o Grails não tem |
| `render { }` com closure: HTML pelo `MarkupBuilder` (ajustado para HTML5, com layout e tags em `g.*`), XML com `contentType` XML e JSON com `contentType` JSON | ✅ | Sem `contentType`, é HTML, como no Grails (antes o Grox tratava como JSON) |
| `redirect`, `respond`, `withFormat`, `bindData`, `createLink`, `g.*`, `message` | ✅ | `chain`, `forward` ❌ |
| Constraint `bindable: false` (domain e command objects; `include` explícito vence) | ✅ | Transients e propriedades `def` ainda são vinculados (o Grails não vincula por padrão) |
| `allowedMethods`, `responseFormats`, `defaultAction`, `static layout` | ✅ | |
| Actions assíncronas (`grails.async`: `task`, `tasks`, `Promise`, `then`, `waitAll`...) e retorno de `CompletableFuture`/`Awaitable` | ✅ | `startAsync()` da Servlet API, streaming e SSE ❌ |
| Interceptors (`*Interceptor`: `match`/`matchAll`/`excludes`, `before`/`after`/`afterView`, `order`, `model`/`view`) | ✅ | `beforeInterceptor`/`afterInterceptor` (Grails 2) ❌ |
| Command objects com constraints (`Validateable`) e domain classes como argumento (carregadas por id) | ✅ | |
| Erros de conversão no binding (`typeMismatch`) | ✅ | Parâmetros simples de action (`show(Long id)`) com valor inválido viram `null` sem erro |
| Namespaces de controller | ❌ | |
| `@grails.gorm.annotation.Entity` (domain class fora de `grails-app/domain`) | ✅ | Para apps sem o layout Grails (`grox-dsl`) |
| `BootStrap` sem o módulo Grails (`grox-dsl`) | ✅ | Classe `BootStrap` no pacote da app (com `environments {}`) ou `bootstrap { init { } destroy { } }` da DSL de projeto |
| Interceptors sem o módulo Grails (`grox-dsl`) | 🟡 | `before`/`after`/`error` na DSL de rotas (por escopo e padrão de caminho) e `@Before`/`@After`/`@OnError` nos `@Handler` (extensão Grox); classes `*Interceptor` ❌ |
| `resources.groovy` com `nome = instancia` | ✅ | Extensão do Grox; o BeanBuilder do Spring não tem |

## UrlMappings

| Recurso | | Observação |
|---|---|---|
| Variáveis, opcionais, `(.$format)?`, `**`, `$path**` | ✅ | |
| `constraints { x(matches:) }`, params extras, `action = [GET:, POST:]` | ✅ | Outras constraints ❌ |
| Verbos (`get`, `post`, ...), `method:`, `group`, `redirect:`, `view:` | ✅ | Outro verbo dá 405 só se o mapeamento levaria a um destino; 404 se nenhum leva a um controller e action existentes |
| `resources:` | ✅ | `resource:` (singular), aninhamento ❌ |
| Códigos de erro (`"404"`, `"500"`) | ✅ | `exception:` ❌ |
| Mapeamentos nomeados (`name x: ...`), `uri:` | ❌ | |

## Services

| Recurso | | Observação |
|---|---|---|
| Singleton com injeção por nome (`def x` / tipado) | ✅ | |
| `static actor = true` (extensão Grox) | ✅ | |
| `@Transactional`, `@ReadOnly` (`grails.gorm.transactions`) | ✅ | Por proxy (chamadas externas). `static transactional` ❌. `readOnly`: `FlushMode.MANUAL` e entidades somente leitura. Participante que falha marca a transação inteira (`UnexpectedRollbackException`), como no Spring |
| Escopos (`static scope`) | ❌ | |

## Views, GSP e TagLibs

| Recurso | | Observação |
|---|---|---|
| `${}`, `<% %>`, `<%= %>`, `<%@ page import contentType %>`, comentários | ✅ | Codificação HTML por padrão |
| `g:if/elseif/else/unless/while/each/set` | ✅ | `g:collect`, `g:findAll`, `g:grep` ❌ |
| Links, formulários, `render`, layout, `message`, `formatDate/Number`, `join` | ✅ | Lista completa no [guia](guia.md#tags-g-nativas) |
| `g:paginate`, `g:sortableColumn`, `g:uploadForm` | ✅ | |
| `g:remoteLink`, `g:javascript`, `g:img` | ❌ | |
| `g:hasErrors`, `g:eachError`, `g:renderErrors`, `g:fieldValue`, `g:message error=` | ✅ | |
| TagLibs da aplicação, `namespace`, `returnObjectForTags`, `body(args)`; tags como closure ou como método (`def tag(Map attrs, Closure body)`, Grails 3+); namespace como propriedade no controller e na TagLib (`auth.isAdmin()`) | ✅ | `defaultEncodeAs`, `encodeAsForTags` ❌ |
| Layouts SiteMesh (meta, convenção, `pageProperty`, `<content tag>`, `applyLayout`) | ✅ | SiteMesh 3 em vez do 2 do Grails. `<sitemesh:write>` também funciona |
| Codecs `encodeAsHTML/URL/JSON/JavaScript/Raw`, `encodeAsMD5/SHA1/SHA256` (e `...Bytes`), `encodeAsBase64/Hex` | ✅ | Configuração de codec padrão ❌. Como no Grails, o que `encodeAsHTML/JavaScript/URL` devolveu não é codificado de novo pelo `${}` nem pelas tags (registro por requisição) |
| Asset pipeline: `grails-app/assets`, `require`/`require_tree`/`require_self`, `url()` no CSS, digest e manifest no build, tags `asset:javascript/stylesheet/image/link/assetPath/assetPathExists` | ✅ | Implementação própria: o asset-pipeline-core 5.0.9 não roda no Groovy 6 e a 5.2.0-RC exige o GraalVM. Sem Babel/ES6, SASS, LESS, minificação, gzip nem modo "bundle=false" |
| Pré-compilação das GSPs no build | ✅ | Plugin `org.grox.app` (`compileGsp`). Em desenvolvimento, compilação sob demanda com reload |
| Recarga de controllers, services e domain classes no `run` | ✅ | Com classloaders por geração, sem spring-loaded nem devtools. Recarga leve para artefatos e completa para domain e `src/` (ver guia). Também acompanha as bibliotecas `org.grox.lib` do mesmo build (no Grails, plugins locais com `grails { plugins { implementation project(...) } }`) |

## Dados

| Recurso | | Observação |
|---|---|---|
| Domain classes, `save`/`delete`/`get`/`list`/`count`, dynamic finders (inclusive os booleanos: `findAllAtivo()`, `findAtivoByNome`), `findWhere`, HQL | ✅ | |
| `constraints` (todas as nativas do Grails, `validator`, `unique` com escopo), `errors` | ✅ | `creditCard`, `scale`, `notEqual`, `shared`/`importFrom` ❌ |
| `hasMany`, `belongsTo`, `mappedBy`, `transients`, `addTo`/`removeFrom`, cascata | ✅ | many-to-many ✅: dono é o lado de quem o outro faz `belongsTo`, tabela `dono_propriedade`, colunas `dono_id`/`alvo_id`. `hasOne` ✅ (ver a seção do app C); `embedded` e herança ✅ (abaixo); coleções de tipos básicos ❌ |
| `static mapping` (table, version, sort, id generator, column/length/sqlType, autoTimestamp) | ✅ | Índices, cache, lazy/eager por propriedade ❌ |
| `withTransaction`, `withNewTransaction`, `withSession`, `withNewSession` | ✅ | |
| Eventos `beforeInsert`/`beforeUpdate`/`beforeDelete`/`beforeValidate`, `afterInsert`/`afterUpdate`/`afterDelete`/`afterLoad`, `dateCreated`/`lastUpdated` | ✅ | `before*` e `Pre*` também no flush de alterações sem `save()`. `onLoad` ❌ |
| Listeners `@Listener(Dominio)` com `PreInsertEvent`, `PreUpdateEvent`, `PreDeleteEvent` e `Post*` (em beans do `resources.groovy` e services); subclasses de `AbstractPersistenceEventListener` (bean `hibernateDatastore`) | ✅ | Disparados pelo `save()`/`delete()` do Grox; alterações só por flush automático não disparam `PreUpdate` |
| `isDirty()`, `isDirty('p')`, `getPersistentValue`, `getDirtyPropertyNames`, `merge`, `attach` | ✅ | Instância destacada ou nova conta como alterada |
| GORM Data Services (`@Service(Dominio) interface XService`) | ✅ | Por convenção: `get`, `read`, `list`, `count`, `save` (com `failOnError`), `delete`, `find*`/`findAll*`/`count*`. Classes abstratas ❌ |
| `grails.gorm.DetachedCriteria` (`new DetachedCriteria(D).distinct('id').list()`) | ✅ | |
| `load(id)` devolve proxy lazy | 🟡 | Só em entidades alvo de many-to-one (`grox.data.proxies: auto`); nas demais, carrega na hora. `grox.data.proxies: all` restaura o comportamento do GORM |
| `static mapping`: `formula`, `sqlType`, `type: 'text'`, `column`, `version false`; `static indexes` | ✅ | `static indexes` e `fetchMode` são ignorados |
| `id composite: ['role', 'user']` (ex.: `UserRole` do spring-security) | ✅ | Sem classe de id: a entidade é a chave. `save()` insere ou mescla conforme a existência (ou `insert: true`), e `ident()` devolve a instância. `idEq` ❌ |
| Nomes de tabela e coluna do GORM (snake_case), inclusive com palavras reservadas (`User` → `` `user` ``, `userCriado` → `user_criado_id`), com a regra do `ImprovedNamingStrategy`: dígito e sigla não separam (`valor10Meses` → `valor10meses_id`, `codigoERP` → `codigoerp`) | ✅ | |
| `dbCreate: update` | 🟡 | Por padrão só adiciona (`grox.schema.update: safe`), como o GORM sobre Hibernate 5. O Hibernate 7 puro também altera colunas existentes |
| `where { }` / `whereAny` / `find { }` / `findAll { }` (AST), `DetachedCriteria` (list, find, count, exists, deleteAll, updateAll, sort, max, encadeamento), caminhos de associação com vários níveis (`livro.autor.nome`), conferidos nível a nível na compilação | ✅ | Funções (`year(data)`), subqueries, `property.size()` ❌ (erro de compilação) |
| `createCriteria()` / `withCriteria` (restrições, and/or/not, associações, order, paginação, projeções) | ✅ | `createAlias` ✅, projeções fora de `projections {}` ✅ (como no GORM), associação dentro de `projections {}` ✅, `gte`/`lte` ✅. `fetchMode`, `sqlRestriction`, `scroll` ❌ |
| `PagedResultList` (`totalCount`) em `list(max: n)`, `createCriteria().list(max: n) { }` e `where { }.list(max: n)` | ✅ | Como no GORM: só com `max` nos parâmetros; o `count` roda na primeira leitura de `totalCount` |
| Named queries | ❌ | |
| Multi-datasource, MongoDB e outros datastores | ❌ | |
| JSON (`as JSON`, `JSON.parse`, `respond`) | ✅ | Marshallers customizados ❌. Como no Grails, `render x as JSON` fecha o writer: um segundo render na mesma resposta é descartado |
| XML (`as XML`) | ❌ | |

## Sessão

| Recurso | | Observação |
|---|---|---|
| Sessão em memória e no Redis (`grox.session.store`) | ✅ | Gravação diferencial, TTL, troca de id no login |
| Atributos não serializáveis no Redis | ❌ | Erro claro ao gravar |
| Outros stores (JDBC, Hazelcast) | ❌ | Implemente `org.grox.session.SessionRepository` |

## Segurança (spring-security-core / spring-security-rest)

| Recurso | | Observação |
|---|---|---|
| `interceptUrlMap` e `controllerAnnotations.staticRules` (no yml ou no `application.groovy`), `rejectIfNoRule`, expressões de acesso, `httpMethod` | ✅ | As `staticRules` valem depois do `interceptUrlMap`, sem o `securityConfigType` |
| `@Secured` em controller e action (String/lista, `httpMethod`) | ✅ | `@Secured(closure = {...})` ❌. Tem precedência sobre as regras (no plugin, os modos são exclusivos) |
| Form login, logout POST, URL salva, página `login/auth.gsp`, mensagens `springSecurity.*` | ✅ | `LoginController` do plugin ❌: a página é do framework ou da app |
| User/Role/UserRole (GORM), `userLookup.*`, `authority.*`, bcrypt | ✅ | Hierarquia de roles, grupos (`useRoleGroups`), ACL ❌ |
| `password.algorithm` (`bcrypt`, `SHA-256`, `SHA-512`, `SHA-1`, `MD5`), `password.hash.iterations`, `encodeHashAsBase64`, prefixos `{bcrypt}`/`{SHA-256}`/`{noop}` | ✅ | Formato do `DelegatingPasswordEncoder`: Grails e Grox leem o mesmo banco. pbkdf2, scrypt, argon2 ❌ |
| `userDetailsService` no estilo Spring (`UserDetailsService`, `GrailsUserDetailsService`, `GrailsUser`, `User`, `SimpleGrantedAuthority`, `UsernameNotFoundException`, `NoStackUsernameNotFoundException`), inclusive do `resources.groovy` | ✅ | |
| `springSecurityService` (`currentUser`, `principal`, `loggedIn`, `encodePassword`, `reauthenticate`); `principal` sem login é `'anonymousUser'` | ✅ | `authentication` sem login é null (no Spring, um `AnonymousAuthenticationToken`) |
| Tags `sec:` (`ifLoggedIn`, `ifAnyGranted`, `access`, `username`, `loggedInUserInfo`...) | ✅ | `sec:link` ❌ |
| HTTP Basic | ✅ | Digest ❌ |
| spring-security-rest: `/api/login`, JWT HS256, `Bearer`, `refresh_token` + `/oauth/access_token`, `/api/logout` com revogação (memória/Redis) | ✅ | `/api/validate`, JWE/RSA ❌ |
| spring-security-rest: tokens em GORM (`useGorm`, `tokenDomainClassName`) e cabeçalho próprio (`useBearerToken: false`, `headerName`, como `X-Auth-Token`) | ✅ | Refresh só com JWT, como no plugin. Tokens gravados sem hash, como no plugin, para os já emitidos pelo Grails no mesmo banco continuarem valendo. Parâmetro `access_token` no corpo do formulário ❌ |
| `filterChain.chainMap` com `filters: 'none'` (sem segurança nem sessão) | ✅ | Assets sempre sem segurança, como no Grails |
| `filterChain.chainMap` com listas de filtros (`JOINED_FILTERS,-x`), primeira entrada que casa | ✅ | Interpretados: sessão, token REST, Basic, anônimo, autorização e tradução de exceção (ver o guia). `rememberMe`, `logoutFilter` e `authenticationProcessingFilter` não mudam nada |
| Limite de tentativas de login, cabeçalhos de segurança, `Secure` no cookie, allowlist na sessão do Redis | ✅ | Extensões Grox (o Grails não faz por padrão); ver `docs/guia.md` |
| CSRF (token de sessão, `<g:form>` automático) | ✅ | Extensão Grox: o plugin do Grails não liga CSRF por padrão. Sem sessão, o token vai no cookie `GROX-CSRF` (*double submit*): páginas públicas com formulário não criam sessão, como no Grails |
| Remember-me, switch user, OAuth2/OIDC, LDAP | ❌ | LDAP é possível via `UserDetailsService` próprio |

## Multi-tenancy (GORM)

| Recurso | | Observação |
|---|---|---|
| `MultiTenant<T>`, modo DISCRIMINATOR, `mapping { tenantId name: 'x' }` | ✅ | Filtro nativo do Hibernate em todas as consultas |
| `Tenants.currentId/withId/withCurrent/withoutId`, `withTenant` | ✅ | `eachTenant` ❌ |
| `@CurrentTenant`, `@WithoutTenant`, `@Tenant` em services | ✅ | Via proxy (chamadas externas) |
| `TenantResolver`, `FixedTenantResolver`, `SystemPropertyTenantResolver`, `SessionTenantResolver` | ✅ | `SubDomainTenantResolver`, `HttpHeaderTenantResolver`, `CookieTenantResolver` ❌ (fáceis de implementar) |
| Tenant do usuário logado (`UserTenantResolver`) | ✅ | Extensão Grox |
| Modos DATABASE e SCHEMA | ❌ | |

## Diferenças intencionais

- **Concorrência por atores:** actions rodam dentro de atores (virtual threads). Em modo `pool`, cada ator tem sua instância do controller, então campos de instância não são compartilhados entre atores (no Grails o controller é singleton por padrão).
- **Services-ator:** `static actor = true` não existe no Grails.
- **Sessão Hibernate:** uma por action, incluindo a view, e propagada aos services-ator. Alterações em entidades sem `save()` e fora de transação não são gravadas no fim da requisição.
- **Sem container de Servlet:** `request`, `response` e `session` são objetos próprios que implementam as interfaces completas do jar oficial `jakarta.servlet-api` (6.1), então bibliotecas que recebem `HttpServletRequest` funcionam. Parâmetros, cabeçalhos (inclusive `getDateHeader`/`getIntHeader`), locale pelo `Accept-Language`, corpo (`inputStream`/`reader`), cookies, atributos e sessão funcionam; o que depende de container (`getServletContext`, `startAsync`, `getRequestDispatcher`, `getParts`, `login`/`logout`/`authenticate`, `upgrade`) lança `UnsupportedOperationException`, `getUserPrincipal` e `getRemoteUser` devolvem o usuário autenticado pela segurança do Grox, e `isUserInRole` é sempre `false` (use o `springSecurityService` ou `SpringSecurityUtils.ifAllGranted`). O `servletContext` do BootStrap é `null`.

## Adicionado pelo piloto de migração

Na web e nas views, estes itens entraram:

| Recurso | Status | Observações |
|---|---|---|
| `render(status: NOT_FOUND)` com `org.springframework.http.HttpStatus` | ✅ | |
| `render(404)`, `render(obj)` | ✅ | Escreve o texto do objeto |
| `render(status:, contentType: 'application/json') { campo = valor; bloco { ... } }` | ✅ | Estilo JSONBuilder do Grails |
| `respond x` com `grails.scaffolding.templates.domainSuffix` | ✅ | `usuarioInstance`, `usuarioInstanceList` |
| `request.withFormat`, `request.getRequestURL()`, `request.reader`, `request.exception` | ✅ | `request.withFormat` e `request.format` usam o Content-Type da requisição (`form`, `multipartForm`, `json`), como no Grails; sem corpo, vale o primeiro bloco |
| `createLink`/`g:form` com `resource: instancia` ou `url: [resource: x, action: 'update']` | ✅ | Controller pelo nome da classe e id da instância (`/cedente/update/1`) |
| `request.JSON` e `JSON.parse` devolvem `JSONObject`/`JSONArray` | ✅ | São `Map`/`List`; `opt*`/`get*` do org.json (`optInt`, `optLong`, `optDouble`, `optBoolean`, `optString`, `optJSONObject`, `getInt`...), com `JSONException` |
| `grails.rest.RestfulController` | ✅ | Formulários com flash e redirect, 422 com a view nos erros, ganchos `*Resource`. Sem `patch`, `Location` só no `save` |
| `@BindingFormat('dd/MM/yyyy')` | ✅ | Date, java.time e números |
| `grails.databinding.dateFormats` | ✅ | Formatos de `Date` tentados na ordem, antes dos padrões ISO do Grox |
| Template GSP enxerga o modelo da página que o renderizou | ✅ | Como no Grails |
| `<content tag="include.bottom">` (nome com ponto) e `pageProperty(name: 'page.include.bottom')` | ✅ | |
| Atributo literal de lista ou mapa avaliado (`noSelection="['':'-']"`, `params="[a: 1]"`) | ✅ | |
| `<g:if env="development">`, `${assetPath(...)}` e outras tags sem prefixo de namespace único | ✅ | |
| `attrs.int('x')`, `attrs.boolean('y')` nas taglibs | ✅ | |
| `grailsAttributes` em controllers e taglibs | 🟡 | `messageSource`, `applicationContext`, `grailsApplication`, `getApplicationUri`/`getControllerUri`/`getControllerActionUri(request)`, `getFlashScope(request)`. O context path é sempre a raiz |
| `g:actionSubmit`, `g:datePicker` (com binding `nome_day/_month/_year`), `g:formatBoolean`, `g:renderException`, `asset:script`, `asset:deferredScripts` | ✅ | |
| `require` de asset inexistente | ✅ | Só aviso, como no asset-pipeline |
| Tag GSP sem `>` antes do próximo `<` | 🟡 | Aceita com aviso (o Grails aceita em silêncio) |
| `LoginController` do plugin (`/login`, `/login/index`, `/login/authfail`, `flash.message` na falha) | ✅ | Embutido quando a app não tem o seu |
| `params.controller`/`params.action` (e `controllerName`/`actionName`) na página de login embutida | ✅ | `login`/`auth`, como no controller do plugin (achado no app B) |
| `LogoutController` do plugin (`/logout`, `/logout/index` → `filterProcessesUrl`; 405 a GET com `postOnly`) | ✅ | Embutido quando a app não tem o seu (achado no app B) |
| CSRF | 🟡 | O Grox ativa por padrão e o spring-security-core não. Use `<g:form>` ou `grails.plugin.springsecurity.csrf.enabled: false` |

## Adicionado pelos candidatos (app B)

| Recurso | Status | Observações |
|---|---|---|
| `@GrailsCompileStatic`, `@GrailsTypeChecked` | 🟡 | Aceitos, mas a classe compila de forma dinâmica |
| Descritores `*GrailsPlugin` (`doWithSpring`, `doWithApplicationContext`, `onShutdown`) e `Application.doWithSpring()` | ✅ | Metadados do plugin ignorados; `doWithWebDescriptor` ❌ |
| DSL de beans: `{ bean -> bean.autowire = true }`, `ref()` a bean declarado depois | ✅ | Em propriedades; no construtor o bean precisa vir antes |
| `@PostConstruct` em services e beans | ✅ | |
| API de transação do Spring: `@Transactional(propagation)`, `PlatformTransactionManager`, `TransactionAspectSupport.currentTransactionStatus()` | ✅ | Uso programático só REQUIRED/SUPPORTS/MANDATORY; `isolation`/`timeout` sem efeito |
| `PasswordEncoder` do Spring Security (`DelegatingPasswordEncoder`, `BCryptPasswordEncoder`, `MessageDigestPasswordEncoder`), `UserDetailsPasswordService` (upgrade do hash no login), `DaoAuthenticationProvider`/`ProviderManager` como configuração | ✅ | pbkdf2, scrypt, argon2 ❌ |
| Modelo de mapeamento do GORM (`PersistentEntity`, `PersistentProperty`, `OneToMany`, `ManyToOne`, `ManyToMany`), `HibernateDatastore.mappingContext`, `GrailsHibernateUtil.unwrapIfProxy` | 🟡 | Somente leitura; one-to-one aparece como `ManyToOne` |
| `PropertyEditorRegistrar`, `CustomDateEditor`, `StructuredDateEditor` | ✅ | Editor por tipo (o caminho da propriedade é ignorado) |
| `GrailsParameterMap`, `grails.util.Holder`, `SessionLocaleResolver`, `SecurityRequestHolder` | ✅ | |
| GPars: `GParsPool.withPool { collectParallel/eachParallel/findAllParallel }`, `DefaultPGroup.task` | 🟡 | Sobre o `groovy.concurrent` do Groovy 6 (`ParallelScope.withPool` e as extensões `*Parallel` nativas; o jar do GPars não roda no Groovy 6). Atores e dataflow do GPars ❌: use os do `groovy.concurrent` |
| Actions como propriedades closure (`def list = { }`) | ✅ | Sem parâmetros |
| `config.chaveInexistente.x` null-safe (`NullSafeNavigator`) | ✅ | |
| HQL com `?0` (base zero) ou `?` | ✅ | Renumerados para `?1...` (o Hibernate 7 do Grails 8 recusa); parâmetro solto (`executeQuery(hql, id)`) ou array também valem; `GString` vira `String` nos parâmetros, finders, criteria e `where` (o Hibernate 7 recusa) |
| hasMany unidirecional (e auto-referente) com a tabela de junção do GORM (`book_author`, `book_authors_id`, `author_id`) | ✅ | Tabela de junção com o nome de uma entidade: aviso; ao gerar o schema, renomeada |
| Mensagem inexistente em `messageSource.getMessage(code, args, null, locale)` | ✅ | `null`, como no Spring (o `g:message` devolve o código) |
| `@Transactional(rollbackFor/noRollbackFor/timeout/...)` | 🟡 | Atributos aceitos, ainda sem efeito |
| `org.springframework.validation.Errors`, `FieldError`, `ObjectError` | ✅ | Os erros de validação do Grox os implementam/estendem |
| `hasMany` de valores simples (`hasMany = [tags: String]`) | ✅ | Tabelas e colunas com os nomes do GORM |
| Namespace de TagLib como propriedade (`auth.isAdmin()`) em controllers e TagLibs | ✅ | |
| Finders booleanos (`findAllEnabled()`, `findNotEnabled()`, `findEnabledByNome`) | ✅ | |
| Vírgula entre atributos de tag GSP | 🟡 | Tolerada com aviso |
| `assetPath`/`assetPathExists` no namespace `g` | ✅ | Como no asset-pipeline |

## Adicionado pelos candidatos (app C)

| Recurso | Status | Observações |
|---|---|---|
| Artefatos no pacote padrão (`UrlMappings`, `LoginController` sem `package`) | ✅ | Indexados pelo plugin Gradle (`class:` em `META-INF/grox/packages`) |
| Herança de domain classes | ✅ | Tabela única com a coluna `class` (nome da classe, ou `discriminator`), como no GORM, ou uma tabela por subclasse (`tablePerHierarchy false`). Consultas polimórficas pela raiz; cada subclasse com a própria API estática (a AST reaplica o trait). Classe abstrata em `grails-app/domain` é entidade (marca `@AbstractEntity`); classe base em `src` vira `<mapped-superclass>` (antes, um campo declarado nela falhava na subida). `tablePerConcreteClass` ❌ |
| `embedded` (componente nas colunas do dono) | ✅ | Colunas `endereco_rua` (GORM); a classe do componente em `grails-app/domain` não vira entidade. Consulta pelo caminho, binding, JSON e scaffold (`fieldset`). Constraints do componente validadas no dono (campo `endereco.rua`; propriedades opcionais por padrão). Limites: um nível, só tipos simples |
| `hasOne` (one-to-one com a FK no filho) | ✅ | Cascade all a partir do dono. Com filho multi-tenant, filho ausente vira `null` (many-to-one por `join-formula`; sem orphan removal) |
| One-to-one sem `hasOne` (`Face { Nose nose }`, `Nose { belongsTo = [face: Face] }`) | ✅ | FK no dono (`face.nose_id`); o `belongsTo` do filho é o lado inverso, sem coluna. Com `hasMany` do mesmo tipo no dono ou mais de uma referência, o `belongsTo` fica many-to-one |
| `principal as GrailsUser` (ou `as UserDetails`) | ✅ | Convertido a partir do `GroxUser`, com o id; sem a senha |
| Plugin redis sem `grails.redis` (o app C configura `redis:` fora de `grails`) | ✅ | `redisService` em `localhost:6379`, como o plugin |
| `SessionInformation.expireNow()` (`sessionRegistry.getAllSessions(u, false)*.expireNow()`) | ✅ | Apaga a sessão na hora (no Spring, o `ConcurrentSessionFilter` desloga na próxima requisição) |
| `response.addCookie` com nome ou valor inválido (`;`, espaço, aspas) | ✅ | `IllegalArgumentException`, como a Servlet API |
| Actions closure | ✅ | Só propriedades (`def x = {}`); campo `private` com closure não é action, como no Grails |
| Plugin hibernate-filter: `static hibernateFilters`, `withoutHibernateFilters`, `withoutHibernateFilter`, `withHibernateFilters`, `enable/disableHibernateFilter` | ✅ | `withHibernateFilter(nome) { }` (use `enableHibernateFilter`), `aliasDomain` e filtros em coleções ❌ |
| `first(Map)`/`last(Map)`, opções (`max`, `offset`, `readOnly`...) no mapa de parâmetros do `executeQuery`/`findAll` | ✅ | Separadas dos parâmetros nomeados |
| `offset` negativo | ✅ | Tratado como 0, como no GORM |
| Config aninhada null-safe (`grailsApplication.config.a.b.c` com chave inexistente) | ✅ | |
| spring-security-core para LoginController próprio: `SecurityContextHolder`, `HttpSessionRequestCache`, `WebAttributes`, exceções de conta, `securityConfig`, `authenticationTrustResolver`, `isAjax` | ✅ | |
| `SessionRegistryImpl` (`sessionRegistry`), `ConcurrentSessionControlAuthenticationStrategy` | 🟡 | O registry lê as sessões ativas; a estratégia é só configuração (limite de sessões ❌) |
| Servlet API (jar `jakarta.servlet-api` 6.1): `Cookie`, `request.cookies` (cookies com nome inválido para a Servlet API ficam de fora), `response.addCookie`, `response.headerNames`, `request.parameterMap`, `request.locale`, `response.outputStream`, atributos `javax/jakarta.servlet.error.*`, atributos do request como propriedades (`request.x`) | ✅ | Container (async, dispatcher, partes, login): `UnsupportedOperationException` |
| `MultipartHttpServletRequest`, `g:cookie` | ✅ | |
| Interceptor: `modelAndView` e `throwable` | ✅ | |
| Tags `g:` chamadas como métodos nas actions (`formatDate(...)`) | ✅ | |
| `g:formatDate` com `timeZone` e `locale` | ✅ | |
| excel-export: `fillHeader`, `fillRow`, `add(lista, propriedades)` | ✅ | |
| rendering: `rendering:inlinePng`, `inlineJpeg`, `inlineGif` | ✅ | Data URI no `<img>` |
| `ApplicationListener<E>` em beans e `applicationContext.publishEvent(evento)` | ✅ | Síncrono, filtrado pelo tipo genérico; falha num listener vai para o log |
| `RestTokenCreationEvent` (spring-security-rest) com `accessToken` (`accessToken`, `refreshToken`, `expiration`, `principal`) | ✅ | Publicado no `POST /api/login` (JWT e GORM), já com o usuário no contexto de segurança |
| Métodos sem `@Transactional` em service com outros métodos transacionais | ✅ | Correção: rodavam no proxy, sem as dependências injetadas |
| Bean `pluginManager` (`GrailsPluginManager.allPlugins`, `hasGrailsPlugin`), `PluginManagerAware` | ✅ | Os descritores `*GrailsPlugin` das bibliotecas |
| `GrailsClass.namespace` (`grailsApplication.controllerClasses*.namespace`) | ✅ | |
| Context classloader da thread = classloader da aplicação nas actions e views | ✅ | Bibliotecas que varrem pacotes (swagger) enxergam as classes recarregáveis |
| JSON Views (`.gson`) | ❌ | Não planejado (o app-c-api não usa; as do perfil rest-api saíram) |
| `org.springframework.security.web.http.SecurityHeaders` | 🟡 | `bearerToken(token)` preenche um mapa de cabeçalhos (no Spring, `HttpHeaders`) |
| Open session in view na página de login e nas views de erro (layout ou TagLib com associação lazy) | ✅ | Achado no app B (`layouts/blog.gsp`) |
| GSP: `g:each var="${x}"`, `in="${lista?}"`, `g:each` aninhado sem `var`, aspas dentro de atributos | ✅ | |
| Métodos de `String` em valores `raw()`/codificados (`SafeHtml`) | ✅ | |
