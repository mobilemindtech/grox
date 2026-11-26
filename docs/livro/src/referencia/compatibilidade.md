# Compatibilidade com o Grails

A matriz completa do que o Grox suporta da API do Grails e dos plugins, organizada por área. Ela junta o que foi planejado desde o início com o que entrou durante as migrações reais (apps A, B e C).

Legenda: ✅ suportado · 🟡 parcial · ❌ ausente (veja o `docs/roadmap.md` no repositório).

## Estrutura, build e configuração

| Recurso | | Observação |
|---|---|---|
| Layout `grails-app/{controllers,services,taglib,views,i18n,conf,init}` | ✅ | Plugin Gradle `org.grox.app` |
| `grails-app/domain` | ✅ | GORM próprio sobre Hibernate 7.4 |
| Descoberta por sufixo (`*Controller`, `*Service`, `*TagLib`, `UrlMappings`, `BootStrap`) | ✅ | No pacote da classe `Application` e nos do índice de pacotes |
| Layout `grails-app/` | ✅ | Também `grox-app/` (detectado) ou outro nome com `grox { appDir }` (extensão Grox) |
| Autocomplete no IntelliJ IDEA | 🟡 | Descritores GDSL nos jars; `GormEntity<D>` genérica para `implements` explícito. Ver [Estrutura](../recursos/estrutura.md#autocomplete-no-intellij-idea) |
| Artefatos fora do pacote da aplicação; `messages.properties` de plugins mesclados | ✅ | Pelo índice `META-INF/grox/packages` gerado pelos plugins Gradle |
| Artefatos no pacote padrão (`UrlMappings`, `LoginController` sem `package`) | ✅ | Indexados pelo plugin Gradle (`class:` no índice) |
| Plugins, profiles, `grails` CLI | ❌ | O plugin Gradle cobre o layout e o build. Artefatos compartilhados: biblioteca `org.grox.lib` |
| Scaffolding dinâmico (`static scaffold = Livro`) e tags `f:` do plugin fields | 🟡 | Actions do `RestfulController` e views geradas dos templates (`src/main/templates/scaffolding/`). Geração de código (`generate-all`), `scaffold = true` e os templates `_fields/` ❌ |
| `application.yml` + `environments`, `application.groovy` e `runtime.groovy` (`ConfigSlurper`, mesclados ao yml) | ✅ | |
| `spring.config.import` (`classpath:`, `file:`, `optional:`; `.yml` e `.groovy`) | ✅ | O importado vence quem o importou. Sem `configtree:`, curingas nem perfis do Spring |
| Perfil `grox` (`application-grox.yml`/`.groovy`) | ✅ | Extensão Grox: sempre ativo no Grox, nunca no Grails. Outros perfis (`spring.profiles.active`) ❌ |
| `application.yml` com `${VAR}` / `${VAR:padrão}` | ✅ | Propriedade de sistema, variável de ambiente ou outra chave; também em listas de mapas |
| `config.chaveInexistente.x` null-safe (`NullSafeNavigator`), inclusive aninhada | ✅ | |
| `grails.util.Environment` | ✅ | `current`, `executeForCurrentEnvironment`; `environments {}` no BootStrap |
| `grails.boot.GrailsApp`, `GrailsAutoConfiguration`, `Holders`, `grails.util.Holder`, `grailsApplication.controllerClasses`/`getArtefacts(tipo)` (`GrailsClass[]`)/`config.getProperty('a.b')`, `applicationContext.getBean(Tipo)` | ✅ | |
| `GrailsClass.namespace` (`grailsApplication.controllerClasses*.namespace`) | ✅ | |
| `grails-app/conf/spring/resources.groovy` | ✅ | `nome(Classe)`, argumentos de construtor, `{ prop = ...; dep = ref('x') }`. Outros recursos do DSL do Spring ❌ |
| DSL de beans: `{ bean -> bean.autowire = true }`, `ref()` a bean declarado depois | ✅ | Em propriedades; no construtor o bean precisa vir antes |
| `resources.groovy` com `nome = instancia` | ✅ | Extensão do Grox; o BeanBuilder do Spring não tem |
| Descritores `*GrailsPlugin` (`doWithSpring`, `doWithApplicationContext`, `onShutdown`) e `Application.doWithSpring()` | ✅ | Metadados do plugin ignorados; `doWithWebDescriptor` ❌ |
| Bean `pluginManager` (`GrailsPluginManager.allPlugins`, `hasGrailsPlugin`), `PluginManagerAware` | ✅ | Os descritores `*GrailsPlugin` das bibliotecas |
| `@PostConstruct` em services e beans | ✅ | |
| Beans `messageSource`, `groovyPageRenderer`, `assetResourceLocator`, `sessionFactory` (`currentSession`), `dataSource` | ✅ | |
| `ApplicationListener<E>` em beans e `applicationContext.publishEvent(evento)` | ✅ | Síncrono, filtrado pelo tipo genérico; falha num listener vai para o log |
| `@GrailsCompileStatic`, `@GrailsTypeChecked` | 🟡 | Aceitos, mas a classe compila de forma dinâmica |
| Context classloader da thread = classloader da aplicação nas actions e views | ✅ | Bibliotecas que varrem pacotes (swagger) enxergam as classes recarregáveis |
| Recarga de controllers, services e domain classes no `run` | ✅ | Classloaders por geração, sem spring-loaded nem devtools. Recarga leve para artefatos e completa para domain e `src/` |
| Actuator (health, info, metrics, prometheus) | ✅ | Formato próprio (não é o do Micrometer) |
| `@grails.gorm.annotation.Entity` (domain class fora de `grails-app/domain`) | ✅ | Para apps sem o layout Grails (`grox-dsl`) |
| `BootStrap` sem o módulo Grails (`grox-dsl`) | ✅ | Classe `BootStrap` no pacote da app (com `environments {}`) ou `bootstrap { init { } destroy { } }` da DSL de projeto |
| Interceptors sem o módulo Grails (`grox-dsl`) | 🟡 | `before`/`after`/`error` na DSL de rotas (por escopo e padrão de caminho) e `@Before`/`@After`/`@OnError` nos `@Handler` (extensão Grox); classes `*Interceptor` ❌ |

## Controllers

| Recurso | | Observação |
|---|---|---|
| Actions como métodos, parâmetros por nome, command objects | ✅ | |
| Actions como propriedades closure (`def list = { }`) | ✅ | Sem parâmetros. Campo `private` com closure não é action, como no Grails |
| `params` (conversões, aninhado), `request.JSON`, `session`, `flash` | ✅ | `request.json` também (extensão Grox) |
| `request.JSON` e `JSON.parse` devolvem `JSONObject`/`JSONArray` | ✅ | São `Map`/`List`; `opt*`/`get*` do org.json (`optInt`, `optLong`, `optDouble`, `optBoolean`, `optString`, `optJSONObject`, `getInt`...), com `JSONException` |
| `GrailsParameterMap`, `SessionLocaleResolver`, `SecurityRequestHolder` | ✅ | |
| Upload multipart: `request.getFile`, `MultipartFile` (nome do Spring), `g:uploadForm`, binding em `byte[]` | ✅ | Em memória (limite `grox.http.maxContentLength`); streaming para disco ❌ |
| `MultipartHttpServletRequest`, `g:cookie` | ✅ | |
| `render` (text, view, template, status, contentType, file, `as JSON`; `text: null` envia `null`, como no Grails) | ✅ | |
| `render(status: NOT_FOUND)` com `org.springframework.http.HttpStatus` | ✅ | |
| `render(404)`, `render(obj)` | ✅ | Escreve o texto do objeto |
| Views em Pug e Markdown (`grox-pug`, `grox-markdown`) | ✅ | Extensão Grox, ver [Views em Pug e Markdown](../recursos/pug-markdown.md) |
| `render { }` com closure: HTML pelo `MarkupBuilder` (HTML5, com layout e tags em `g.*`), XML com `contentType` XML e JSON com `contentType` JSON | ✅ | Sem `contentType`, é HTML, como no Grails |
| `render(status:, contentType: 'application/json') { campo = valor; bloco { ... } }` | ✅ | Estilo JSONBuilder do Grails |
| `redirect`, `respond`, `withFormat`, `bindData`, `createLink`, `g.*`, `message` | ✅ | `chain`, `forward` ❌ |
| `respond x` com `grails.scaffolding.templates.domainSuffix` | ✅ | `usuarioInstance`, `usuarioInstanceList` |
| Tags `g:` chamadas como métodos nas actions (`formatDate(...)`) | ✅ | |
| Namespace de TagLib como propriedade (`auth.isAdmin()`) em controllers e TagLibs | ✅ | |
| `request.withFormat`, `request.getRequestURL()`, `request.reader`, `request.exception` | ✅ | `request.withFormat` e `request.format` usam o Content-Type da requisição (`form`, `multipartForm`, `json`); sem corpo, vale o primeiro bloco |
| `createLink`/`g:form` com `resource: instancia` ou `url: [resource: x, action: 'update']` | ✅ | Controller pelo nome da classe e id da instância (`/cedente/update/1`) |
| Constraint `bindable: false` (domain e command objects; `include` explícito vence) | ✅ | Transients e propriedades `def` ainda são vinculados (o Grails não vincula por padrão) |
| `allowedMethods`, `responseFormats`, `defaultAction`, `static layout` | ✅ | |
| Actions assíncronas (`grails.async`: `task`, `tasks`, `Promise`, `then`, `waitAll`...) e retorno de `CompletableFuture`/`Awaitable` | ✅ | `startAsync()` da Servlet API, streaming e SSE ❌ |
| GPars: `GParsPool.withPool { collectParallel/eachParallel/findAllParallel }`, `DefaultPGroup.task` | 🟡 | Sobre o `groovy.concurrent` do Groovy 6 (o jar do GPars não roda no Groovy 6). Atores e dataflow do GPars ❌: use os do `groovy.concurrent` |
| Interceptors (`*Interceptor`: `match`/`matchAll`/`excludes`, `before`/`after`/`afterView`, `order`, `model`/`view`) | ✅ | `beforeInterceptor`/`afterInterceptor` (Grails 2) ❌ |
| Interceptor: `modelAndView` e `throwable` | ✅ | |
| Command objects com constraints (`Validateable`) e domain classes como argumento (carregadas por id) | ✅ | |
| Erros de conversão no binding (`typeMismatch`) | ✅ | Parâmetros simples de action (`show(Long id)`) com valor inválido viram `null` sem erro |
| `@BindingFormat('dd/MM/yyyy')` | ✅ | Date, java.time e números |
| `grails.databinding.dateFormats` | ✅ | Formatos de `Date` tentados na ordem, antes dos padrões ISO do Grox |
| `PropertyEditorRegistrar`, `CustomDateEditor`, `StructuredDateEditor` | ✅ | Editor por tipo (o caminho da propriedade é ignorado) |
| `grails.rest.RestfulController` | ✅ | Formulários com flash e redirect, 422 com a view nos erros, ganchos `*Resource`. Sem `patch` |
| `grailsAttributes` em controllers e taglibs | 🟡 | `messageSource`, `applicationContext`, `grailsApplication`, `getApplicationUri`/`getControllerUri`/`getControllerActionUri(request)`, `getFlashScope(request)`. O context path é sempre a raiz |
| Namespaces de controller | ❌ | |
| JSON Views (`.gson`) | ❌ | Não planejado |

## Servlet API e Spring

| Recurso | | Observação |
|---|---|---|
| `request`, `response` e `session` implementam as interfaces do jar oficial `jakarta.servlet-api` 6.1 | ✅ | Parâmetros, cabeçalhos (inclusive `getDateHeader`/`getIntHeader`), locale, corpo, cookies, atributos e sessão. O que depende de container (`getServletContext`, `startAsync`, `getRequestDispatcher`, `getParts`, `login`/`logout`/`authenticate`, `upgrade`) lança `UnsupportedOperationException`. `getUserPrincipal`/`getRemoteUser` devolvem o usuário do Grox; `isUserInRole` é sempre `false`. O `servletContext` do BootStrap é `null` |
| `Cookie`, `request.cookies` (cookies com nome inválido ficam de fora), `response.addCookie`, `response.headerNames`, `request.parameterMap`, `request.locale`, `response.outputStream`, atributos `javax/jakarta.servlet.error.*`, atributos do request como propriedades (`request.x`) | ✅ | |
| `response.addCookie` com nome ou valor inválido (`;`, espaço, aspas) | ✅ | `IllegalArgumentException`, como a Servlet API |
| Spring: `HttpStatus`, `@Autowired`, `@Lazy`, `MessageSource`, `LocaleContextHolder`, `RequestContextHolder`, `ServletWebRequest`, `RequestContextUtils`, `DataIntegrityViolationException`, `AccessDeniedException`, `UserDetails`, `PasswordEncoder` | ✅ | Classes de compatibilidade, sem o Spring |
| `org.springframework.validation.Errors`, `FieldError`, `ObjectError` | ✅ | Os erros de validação do Grox os implementam ou estendem |
| Mensagem inexistente em `messageSource.getMessage(code, args, null, locale)` | ✅ | `null`, como no Spring (o `g:message` devolve o código) |
| API de transação do Spring: `@Transactional(propagation)`, `PlatformTransactionManager`, `TransactionAspectSupport.currentTransactionStatus()` | ✅ | Uso programático só REQUIRED/SUPPORTS/MANDATORY; `isolation`/`timeout` sem efeito |
| `@Transactional(rollbackFor/noRollbackFor/timeout/...)` | 🟡 | Atributos aceitos, ainda sem efeito |

## UrlMappings

| Recurso | | Observação |
|---|---|---|
| Variáveis, opcionais, `(.$format)?`, `**`, `$path**` | ✅ | |
| `constraints { x(matches:) }`, params extras, `action = [GET:, POST:]` | ✅ | Outras constraints ❌ |
| Verbos (`get`, `post`, ...), `method:`, `group`, `redirect:`, `view:` | ✅ | Outro verbo dá 405 só se o mapeamento levaria a um destino |
| `resources:` | ✅ | `resource:` (singular), aninhamento ❌ |
| Códigos de erro (`"404"`, `"500"`) | ✅ | `exception:` ❌ |
| UrlMappings chamando métodos da própria classe (`findControllersInPackage`) | ✅ | |
| Mapeamentos nomeados (`name x: ...`), `uri:` | ❌ | |

## Services

| Recurso | | Observação |
|---|---|---|
| Singleton com injeção por nome (`def x` / tipado) | ✅ | |
| `static actor = true` | ✅ | Extensão Grox |
| `@Transactional`, `@ReadOnly` (`grails.gorm.transactions`) | ✅ | Por proxy (chamadas externas). `static transactional` ❌. `readOnly`: `FlushMode.MANUAL` e entidades somente leitura. Participante que falha marca a transação inteira (`UnexpectedRollbackException`), como no Spring |
| Métodos sem `@Transactional` em service com outros métodos transacionais | ✅ | Rodam na instância real, com as dependências injetadas |
| Escopos (`static scope`) | ❌ | |

## Views, GSP e TagLibs

| Recurso | | Observação |
|---|---|---|
| `${}`, `<% %>`, `<%= %>`, `<%@ page import contentType %>`, comentários | ✅ | Codificação HTML por padrão |
| `g:if/elseif/else/unless/while/each/set` | ✅ | `g:collect`, `g:findAll`, `g:grep` ❌ |
| `g:each var="${x}"`, `in="${lista?}"`, `g:each` aninhado sem `var`, aspas dentro de atributos | ✅ | |
| Links, formulários, `render`, layout, `message`, `formatDate/Number`, `join` | ✅ | Lista completa em [Tags GSP](tags.md) |
| `g:paginate`, `g:sortableColumn`, `g:uploadForm` | ✅ | |
| `g:actionSubmit`, `g:datePicker` (com binding `nome_day/_month/_year`), `g:formatBoolean`, `g:renderException`, `asset:script`, `asset:deferredScripts` | ✅ | |
| `g:formatDate` com `timeZone` e `locale` | ✅ | |
| `g:hasErrors`, `g:eachError`, `g:renderErrors`, `g:fieldValue`, `g:message error=` | ✅ | |
| `g:remoteLink`, `g:javascript`, `g:img` | ❌ | |
| `<g:if env="development">`, `${assetPath(...)}` e outras tags sem prefixo de namespace único | ✅ | |
| `assetPath`/`assetPathExists` no namespace `g` | ✅ | Como no asset-pipeline |
| Atributo literal de lista ou mapa avaliado (`noSelection="['':'-']"`, `params="[a: 1]"`) | ✅ | |
| `attrs.int('x')`, `attrs.boolean('y')` nas taglibs | ✅ | |
| Template GSP enxerga o modelo da página que o renderizou | ✅ | |
| `<content tag="include.bottom">` (nome com ponto) e `pageProperty(name: 'page.include.bottom')` | ✅ | |
| Tag GSP sem `>` antes do próximo `<` | 🟡 | Aceita com aviso (o Grails aceita em silêncio) |
| Vírgula entre atributos de tag GSP | 🟡 | Tolerada com aviso |
| TagLibs da aplicação, `namespace`, `returnObjectForTags`, `body(args)`; tags como closure ou como método (`def tag(Map attrs, Closure body)`); namespace como propriedade | ✅ | `defaultEncodeAs`, `encodeAsForTags` ❌ |
| Métodos de `String` em valores `raw()`/codificados (`SafeHtml`) | ✅ | |
| Layouts SiteMesh (meta, convenção, `pageProperty`, `<content tag>`, `applyLayout`) | ✅ | SiteMesh 3 em vez do 2 do Grails. `<sitemesh:write>` também funciona |
| Codecs `encodeAsHTML/URL/JSON/JavaScript/Raw`, `encodeAsMD5/SHA1/SHA256` (e `...Bytes`), `encodeAsBase64/Hex` | ✅ | Configuração de codec padrão ❌ |
| Asset pipeline: `grails-app/assets`, `require`/`require_tree`/`require_self`, `url()` no CSS, digest e manifest no build, tags `asset:` | ✅ | Implementação própria. Sem Babel/ES6, SASS, LESS, minificação, gzip nem modo "bundle=false" |
| `require` de asset inexistente | ✅ | Só aviso, como no asset-pipeline (o build com `assetCompile` falha) |
| Pré-compilação das GSPs no build | ✅ | `compileGsp`. Em desenvolvimento, compilação sob demanda com reload |
| Open session in view na página de login e nas views de erro | ✅ | |

## Dados (GORM)

| Recurso | | Observação |
|---|---|---|
| Domain classes, `save`/`delete`/`get`/`list`/`count`, dynamic finders, `findWhere`, HQL | ✅ | |
| Finders booleanos (`findAllEnabled()`, `findNotEnabled()`, `findEnabledByNome`) | ✅ | |
| `constraints` (todas as nativas do Grails, `validator`, `unique` com escopo), `errors` | ✅ | `creditCard`, `scale`, `notEqual`, `shared`/`importFrom` ❌ |
| `hasMany`, `belongsTo`, `mappedBy`, `transients`, `addTo`/`removeFrom`, cascata | ✅ | |
| Many-to-many | ✅ | Dono é o lado de quem o outro faz `belongsTo`; tabela `dono_propriedade`, colunas `dono_id`/`alvo_id` |
| hasMany unidirecional (e auto-referente) com a tabela de junção do GORM (`book_author`, `book_authors_id`, `author_id`) | ✅ | Tabela de junção com o nome de uma entidade: aviso; ao gerar o schema, renomeada |
| `hasMany` de valores simples (`hasMany = [tags: String]`) | ✅ | Tabelas e colunas com os nomes do GORM |
| `hasOne` (one-to-one com a FK no filho) | ✅ | Cascade all a partir do dono. Com filho multi-tenant, filho ausente vira `null` (many-to-one por `join-formula`; sem orphan removal) |
| One-to-one sem `hasOne` (`Face { Nose nose }`, `Nose { belongsTo = [face: Face] }`) | ✅ | FK no dono; o `belongsTo` do filho é o lado inverso. Com `hasMany` do mesmo tipo no dono ou mais de uma referência, o `belongsTo` fica many-to-one |
| `embedded`, herança, coleções de tipos básicos além de `hasMany` | ❌ | |
| `static mapping` (table, version, sort, id generator, column/length/sqlType, autoTimestamp) | ✅ | Índices, cache, lazy/eager por propriedade ❌ |
| `static mapping`: `formula`, `sqlType`, `type: 'text'`, `column`, `version false`; `static indexes` | ✅ | `static indexes` e `fetchMode` são ignorados |
| `id composite: ['role', 'user']` (ex.: `UserRole`) | ✅ | Sem classe de id: a entidade é a chave. `save()` insere ou mescla (ou `insert: true`), e `ident()` devolve a instância. `idEq` ❌ |
| Nomes de tabela e coluna do GORM (snake_case), inclusive palavras reservadas (`User` → `` `user` ``, `userCriado` → `user_criado_id`); dígito e sigla não separam (`valor10Meses` → `valor10meses_id`, `codigoERP` → `codigoerp`) | ✅ | |
| `dbCreate: update` | 🟡 | Por padrão só adiciona (`grox.schema.update: safe`), como o GORM sobre Hibernate 5, também com `hibernate.dialect` explícito |
| `withTransaction`, `withNewTransaction`, `withSession`, `withNewSession` | ✅ | |
| Eventos `beforeInsert`/`beforeUpdate`/`beforeDelete`, `dateCreated`/`lastUpdated` | ✅ | `after*` e `beforeValidate` ❌ |
| Listeners `@Listener(Dominio)` com `PreInsertEvent`, `PreUpdateEvent`, `PreDeleteEvent` e `Post*`; subclasses de `AbstractPersistenceEventListener` (bean `hibernateDatastore`) | ✅ | Disparados pelo `save()`/`delete()` do Grox; alterações só por flush automático não disparam `PreUpdate` |
| `isDirty()`, `isDirty('p')`, `getPersistentValue`, `getDirtyPropertyNames`, `merge`, `attach` | ✅ | Instância destacada ou nova conta como alterada |
| GORM Data Services (`@Service(Dominio) interface XService`) | ✅ | Por convenção: `get`, `read`, `list`, `count`, `save`, `delete`, `find*`/`findAll*`/`count*`. Classes abstratas ❌ |
| `grails.gorm.DetachedCriteria` (`new DetachedCriteria(D).distinct('id').list()`) | ✅ | |
| `load(id)` devolve proxy lazy | 🟡 | Só em entidades alvo de many-to-one (`grox.data.proxies: auto`); `all` restaura o comportamento do GORM |
| `where { }` / `whereAny` / `find { }` / `findAll { }` (AST), `DetachedCriteria` (list, find, count, exists, deleteAll, updateAll, sort, max, encadeamento), caminhos de associação com vários níveis (`livro.autor.nome`), conferidos nível a nível na compilação | ✅ | Funções (`year(data)`), subqueries, `property.size()` ❌ (erro de compilação) |
| `createCriteria()` / `withCriteria` (restrições, and/or/not, associações, order, paginação, projeções) | ✅ | `createAlias`, projeções fora de `projections {}`, associação dentro de `projections {}`, `gte`/`lte` ✅. `fetchMode`, `sqlRestriction`, `scroll` ❌ |
| `PagedResultList` (`totalCount`) em `list(max: n)`, `createCriteria().list(max: n) { }` e `where { }.list(max: n)` | ✅ | Só com `max`; o `count` roda na primeira leitura de `totalCount` |
| `first(Map)`/`last(Map)`, opções (`max`, `offset`, `readOnly`...) no mapa de parâmetros do `executeQuery`/`findAll` | ✅ | Separadas dos parâmetros nomeados |
| HQL com `?0` (base zero) ou `?` | ✅ | Renumerados para `?1...`; parâmetro solto ou array também valem; `GString` vira `String` em parâmetros, finders, criteria e `where` |
| `offset` negativo | ✅ | Tratado como 0 |
| Plugin hibernate-filter: `static hibernateFilters`, `withoutHibernateFilters`, `enable/disableHibernateFilter` | ✅ | `aliasDomain` e filtros em coleções ❌ |
| Modelo de mapeamento do GORM (`PersistentEntity`, `PersistentProperty`, `OneToMany`, `ManyToOne`, `ManyToMany`), `HibernateDatastore.mappingContext`, `GrailsHibernateUtil.unwrapIfProxy` | 🟡 | Somente leitura; one-to-one aparece como `ManyToOne` |
| Named queries | ❌ | |
| Multi-datasource, MongoDB e outros datastores | ❌ | |
| JSON (`as JSON`, `JSON.parse`, `respond`) | ✅ | Marshallers customizados ❌. `render x as JSON` fecha o writer: um segundo render é descartado |
| XML (`as XML`) | ❌ | |

## Sessão HTTP

| Recurso | | Observação |
|---|---|---|
| Sessão em memória e no Redis (`grox.session.store`) | ✅ | Gravação diferencial, TTL, troca de id no login |
| Atributos não serializáveis no Redis | ❌ | Erro claro ao gravar |
| Outros stores (JDBC, Hazelcast) | ❌ | Implemente `org.grox.session.SessionRepository` |

## Segurança (spring-security-core e spring-security-rest)

| Recurso | | Observação |
|---|---|---|
| `interceptUrlMap` e `controllerAnnotations.staticRules` (no yml ou no `application.groovy`), `rejectIfNoRule`, expressões de acesso, `httpMethod` | ✅ | As `staticRules` valem depois do `interceptUrlMap`, sem o `securityConfigType` |
| `@Secured` em controller e action (String/lista, `httpMethod`) | ✅ | `@Secured(closure = {...})` ❌. Tem precedência sobre as regras (no plugin, os modos são exclusivos) |
| Form login, logout POST, URL salva, página `login/auth.gsp`, mensagens `springSecurity.*` | ✅ | |
| `LoginController` do plugin (`/login`, `/login/index`, `/login/authfail`, `flash.message` na falha) | ✅ | Embutido quando a app não tem o seu |
| `params.controller`/`params.action` (e `controllerName`/`actionName`) na página de login embutida | ✅ | `login`/`auth`, como no controller do plugin |
| `LogoutController` do plugin (`/logout`, `/logout/index` → `filterProcessesUrl`; 405 a GET com `postOnly`) | ✅ | Embutido quando a app não tem o seu |
| LoginController próprio: `SecurityContextHolder`, `HttpSessionRequestCache`, `WebAttributes`, exceções de conta, `securityConfig`, `authenticationTrustResolver`, `isAjax` | ✅ | |
| User/Role/UserRole (GORM), `userLookup.*`, `authority.*`, bcrypt | ✅ | Hierarquia de roles, grupos (`useRoleGroups`), ACL ❌ |
| `password.algorithm` (`bcrypt`, `SHA-256`, `SHA-512`, `SHA-1`, `MD5`), `password.hash.iterations`, `encodeHashAsBase64`, prefixos `{bcrypt}`/`{SHA-256}`/`{noop}` | ✅ | Formato do `DelegatingPasswordEncoder`: Grails e Grox leem o mesmo banco. pbkdf2, scrypt, argon2 ❌ |
| `PasswordEncoder` do Spring Security (`DelegatingPasswordEncoder`, `BCryptPasswordEncoder`, `MessageDigestPasswordEncoder`), `UserDetailsPasswordService`, `DaoAuthenticationProvider`/`ProviderManager` como configuração | ✅ | pbkdf2, scrypt, argon2 ❌ |
| `userDetailsService` no estilo Spring (`UserDetailsService`, `GrailsUserDetailsService`, `GrailsUser`, `User`, `SimpleGrantedAuthority`, `UsernameNotFoundException`, `NoStackUsernameNotFoundException`), inclusive do `resources.groovy` | ✅ | |
| `springSecurityService` (`currentUser`, `principal`, `loggedIn`, `encodePassword`, `reauthenticate`); `principal` sem login é `'anonymousUser'` | ✅ | `authentication` sem login é `null` (no Spring, um `AnonymousAuthenticationToken`) |
| `principal as GrailsUser` (ou `as UserDetails`) | ✅ | Convertido a partir do `GroxUser`, com o id; sem a senha |
| Tags `sec:` (`ifLoggedIn`, `ifAnyGranted`, `access`, `username`, `loggedInUserInfo`...) | ✅ | `sec:link` ❌ |
| HTTP Basic | ✅ | Digest ❌ |
| spring-security-rest: `/api/login`, JWT HS256, `Bearer`, `refresh_token` + `/oauth/access_token`, `/api/logout` com revogação (memória/Redis) | ✅ | `/api/validate`, JWE/RSA ❌ |
| spring-security-rest: tokens em GORM (`useGorm`, `tokenDomainClassName`) e cabeçalho próprio (`useBearerToken: false`, `headerName`) | ✅ | Refresh só com JWT. Tokens gravados sem hash, como no plugin. Parâmetro `access_token` no corpo ❌ |
| `RestTokenCreationEvent` com `accessToken` (`accessToken`, `refreshToken`, `expiration`, `principal`) | ✅ | Publicado no `POST /api/login` (JWT e GORM), já com o usuário no contexto |
| `filterChain.chainMap` com `filters: 'none'` | ✅ | Assets sempre sem segurança, como no Grails |
| `filterChain.chainMap` com listas de filtros (`JOINED_FILTERS,-x`), primeira entrada que casa | ✅ | Sessão, token REST, Basic, anônimo, autorização e tradução de exceção. `rememberMe`, `logoutFilter` e `authenticationProcessingFilter` não mudam nada |
| `SessionRegistryImpl` (`sessionRegistry`), `ConcurrentSessionControlAuthenticationStrategy` | 🟡 | O registry lê as sessões ativas; a estratégia é só configuração (limite de sessões ❌) |
| `SessionInformation.expireNow()` | ✅ | Apaga a sessão na hora |
| `org.springframework.security.web.http.SecurityHeaders` | 🟡 | `bearerToken(token)` preenche um mapa de cabeçalhos |
| Limite de tentativas de login, cabeçalhos de segurança, `Secure` no cookie, allowlist na sessão do Redis | ✅ | Extensões Grox |
| CSRF (token de sessão, `<g:form>` automático) | 🟡 | Extensão Grox ativa por padrão; o spring-security-core não tem. Use `<g:form>` ou `csrf.enabled: false` |
| Remember-me, switch user, OAuth2/OIDC, LDAP | ❌ | LDAP é possível via `UserDetailsService` próprio |

## Multi-tenancy

| Recurso | | Observação |
|---|---|---|
| `MultiTenant<T>`, modo DISCRIMINATOR, `mapping { tenantId name: 'x' }` | ✅ | Filtro nativo do Hibernate em todas as consultas |
| `Tenants.currentId/withId/withCurrent/withoutId`, `withTenant` | ✅ | `eachTenant` ❌ |
| `@CurrentTenant`, `@WithoutTenant`, `@Tenant` em services | ✅ | Via proxy (chamadas externas) |
| `TenantResolver`, `FixedTenantResolver`, `SystemPropertyTenantResolver`, `SessionTenantResolver`, `AllTenantsResolver` | ✅ | `SubDomainTenantResolver`, `HttpHeaderTenantResolver`, `CookieTenantResolver` ❌ (fáceis de implementar) |
| Tenant do usuário logado (`UserTenantResolver`) | ✅ | Extensão Grox |
| Resolver que consulta o banco ou devolve `null` | ✅ | Roda numa sessão isolada |
| Modos DATABASE e SCHEMA | ❌ | |

## Plugins do Grails

| Plugin | | Observação |
|---|---|---|
| console (`/console`) | ✅ | Editor simples (sem CodeMirror), histórico no navegador, rollback opcional. Arquivos salvos no servidor ❌ |
| quartz (jobs em `grails-app/jobs`) | ✅ | `static triggers` com `cron` (sem `L`/`W`/`#`) e `simple`, `static concurrent`, `job.triggers()`, jobs como beans e `GrailsJobClass` em `getArtefacts(JobArtefactHandler.TYPE)`. Trigger `custom`, `JobExecutionContext` e clustering ❌ |
| audit-logging (`Auditable`, `grails.plugin.auditLog.*`, `auditRequestResolver`, `AuditLogContext`, gravação depois do commit, `asyncWrite`) | ✅ | Alterações só por flush automático não são auditadas. `Stampable` compila, mas não grava `createdBy`/`lastUpdatedBy`. Vários datasources ❌ |
| redis (`redisService`: `withRedis`, `memoize*`, `memoizeObject`, `withPipeline`, `withTransaction`, conexões e sentinel) | ✅ | A app declara o jedis. Sem `grails.redis`, usa `localhost:6379`. `memoizeDomainList`/`memoizeDomainObject` e `@Memoize` ❌ |
| rabbitmq-native (`rabbitMessagePublisher.send`, `rabbitmq.connections/exchanges/queues`) | 🟡 | A app declara o amqp-client. Consumers, RPC e confirms ❌ |
| rendering (`pdfRenderingService.render`, `renderPdf`, `rendering:inlinePng/Jpeg/Gif`) | ✅ | Sobre Flying Saucer. Saída em GIF/PNG/JPEG ❌ |
| excel-export (`XlsxExporter`, `WebXlsxExporter`, `fillHeader`, `fillRow`, `add(lista, propriedades)`) | ✅ | Sobre POI 5 (a app declara `poi-ooxml`). Estilos `bold`/`centre`/`thickBorder`/`left`/`right` |
| hibernate-filter | ✅ | Ver Dados |
| asset-pipeline | ✅ | Ver Views |
| spring-security-core e rest | ✅ | Ver Segurança |
| cookie-session, spring-session-redis | ❌ | Use `grox.session.store: redis` |

## Diferenças intencionais

- **Concorrência por atores:** actions rodam dentro de atores (virtual threads). Em modo `pool`, cada ator tem sua instância do controller, então campos de instância não são compartilhados entre atores (no Grails o controller é singleton por padrão).
- **Services-ator:** `static actor = true` não existe no Grails.
- **Sessão Hibernate:** uma por action, incluindo a view, e propagada aos services-ator. Alterações em entidades sem `save()` e fora de transação não são gravadas no fim da requisição.
- **CSRF ligado por padrão.**
- **Sem container de Servlet:** veja a seção Servlet API.

## Veja também

- [O que muda em relação ao Grails](../migracao/diferencas.md)
- [Plugins do Grails](../migracao/plugins.md)
- [Armadilhas da migração](../migracao/armadilhas.md)
