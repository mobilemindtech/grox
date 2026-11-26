# Plugins do Grails

Este capítulo diz, plugin por plugin, o que o Grox oferece no lugar dos plugins do Grails, o que a aplicação precisa declarar no build e o que ainda falta. Os jars dos plugins não rodam no Groovy 6 (dependem do Spring e do `CallSite` do Groovy ≤ 5), então o Grox os **reimplementa só no que os projetos migrados usam**, mantendo a API pública no pacote original.

Os plugins vêm no módulo `grox-grails-plugins`, incluído pelo `framework`. Dependências pesadas ficam `compileOnly` no framework: a aplicação declara a biblioteca e o plugin passa a funcionar.

## Resumo

| Plugin do Grails | No Grox | A app declara | Situação |
|---|---|---|---|
| spring-security-core | Motor de segurança + API do plugin | | ✅ |
| spring-security-rest | JWT, tokens em GORM, refresh, logout | | ✅ |
| asset-pipeline | Pipeline próprio, tags `asset:` | | ✅ sem pré-processadores |
| hibernate5/7 (GORM) | GORM próprio sobre Hibernate 7.4 | o driver JDBC | ✅ |
| quartz | Agendador próprio (cron do Quartz) | | ✅ |
| console | Console web em `/console` | | ✅ |
| excel-export | `pl.touk.excel.export` sobre POI 5 | `org.apache.poi:poi-ooxml` | ✅ subconjunto |
| redis | `redisService` | `jedis` | ✅ |
| rabbitmq-native | `rabbitMessagePublisher` | `amqp-client` | 🟡 só publicação |
| rendering | `pdfRenderingService`, `renderPdf` | `flying-saucer-pdf` | ✅ só PDF |
| audit-logging | `Auditable`, `AuditLogContext` | | ✅ |
| hibernate-filter | `static hibernateFilters` | | ✅ |
| spring-session / cookie-session | `grox.session` (memória/Redis) | | troque a configuração |
| cache | | | ❌ (o cache de 2º nível do Hibernate funciona via JCache) |
| JSON Views (`.gson`) | | | ❌ não planejado |
| scaffolding, fields | `static scaffold = X`, tags `f:all`, `f:field`, `f:display`, `f:table` | | 🟡 só o scaffold dinâmico ([Controllers](../recursos/controllers.md#scaffold-dinâmico)) |

## spring-security-core

Configuração em `grails.plugin.springsecurity.*`, com as mesmas chaves. Funcionam: `interceptUrlMap`, `controllerAnnotations.staticRules`, `rejectIfNoRule`, `@Secured` (String, lista, `httpMethod`), expressões de acesso, form login, logout POST, URL salva, `login/auth.gsp`, mensagens `springSecurity.*`, User/Role/UserRole em GORM, `userLookup.*`, `authority.*`, algoritmos de senha (`bcrypt`, `SHA-256`, `SHA-512`, `SHA-1`, `MD5`) no formato do `DelegatingPasswordEncoder`, `springSecurityService`, `SpringSecurityUtils`, tags `sec:`, HTTP Basic, `filterChain.chainMap`, `passwordEncoder` e `userDetailsService` da aplicação, `LoginController` próprio (com `SecurityContextHolder`, `HttpSessionRequestCache`, `WebAttributes`), `sessionRegistry`.

Faltam: `@Secured(closure = {...})`, hierarquia de roles, grupos (`useRoleGroups`), ACL, remember-me, switch user, LDAP (possível por `UserDetailsService` próprio), OAuth2/OIDC, Digest, `sec:link`, pbkdf2/scrypt/argon2, limite de sessões por usuário.

Diferenças: CSRF ligado por padrão; `@Secured` e regras de URL juntas. Veja [Segurança: regras de acesso](../recursos/seguranca-regras.md).

## spring-security-rest

`POST /api/login` (JSON), JWT HS256 (`Authorization: Bearer`), `refresh_token` e `/oauth/access_token`, `/api/logout` com revogação (memória ou Redis), tokens opacos em domain class (`useGorm`), cabeçalho próprio (`useBearerToken: false`, `headerName: X-Auth-Token`), `RestTokenCreationEvent`, `chainMap` com listas de filtros.

Faltam: `/api/validate`, JWE/RSA, parâmetro `access_token` no corpo do formulário. Veja [Segurança: REST, JWT e tokens](../recursos/seguranca-rest.md).

## asset-pipeline

Mesmas pastas (`grails-app/assets/{javascripts,stylesheets,images}`), diretivas `require`, `require_tree`, `require_self`, reescrita de `url()` no CSS, digest e manifest no build (`assetCompile`), tags `asset:javascript`, `stylesheet`, `image`, `link`, `script`, `deferredScripts`, `assetPath`, `assetPathExists` (também no namespace `g`), bean `assetResourceLocator`.

Faltam: transpilação e pré-processadores (ES6/Babel, SASS, LESS, CoffeeScript), minificação, gzip e o modo `bundle=false`. Motivo: o asset-pipeline-core estável é incompatível com o Groovy 6 e a versão 5.2.0 (ainda RC) exige o GraalVM. Se o projeto usa SASS ou LESS, compile-os fora do Grox (por uma tarefa do build) e coloque o CSS gerado em `grails-app/assets`. Veja [Assets e estáticos](../recursos/assets.md).

## quartz

Classes `*Job` em `grails-app/jobs` com `static triggers` (`cron` no formato do Quartz e `simple`), `static concurrent`, `execute()` com sessão de dados, `log` e injeção. Cada job é um bean com o nome completo da classe, e `grailsApplication.getArtefacts(JobArtefactHandler.TYPE)` devolve `GrailsJobClass` (telas de status que listam e executam os jobs funcionam como no plugin); `job.triggers()` devolve o último trigger declarado.

Faltam: trigger `custom`, `JobExecutionContext`, clustering e os caracteres `L`, `W` e `#` no cron. Desligue com `grox.jobs.enabled: false` (ou `quartz.autoStartup: false`). Veja [Jobs agendados](../recursos/jobs.md).

## console

`/console` executa scripts Groovy dentro da aplicação, com rollback opcional, histórico no navegador, `ctx` com todos os beans e os pacotes da aplicação importados. Ativado por `grox.console.enabled` ou `grails.plugin.console.enabled` (padrão: só em development). Faltam o editor CodeMirror e arquivos salvos no servidor. Veja [Plugins reimplementados](../recursos/plugins.md).

## excel-export

`XlsxExporter` e `WebXlsxExporter` (pacote `pl.touk.excel.export`) sobre o POI 5: `putCellValue`, estilos `bold`/`centre`/`thickBorder`/`left`/`right`, `setWorksheetName`, `setResponseHeaders`, `save`, `fillHeader`, `fillRow`, `add(lista, propriedades)`. Declare:

```groovy
implementation 'org.apache.poi:poi-ooxml:<versão>'
```

O POI 5 renomeou classes do POI 3 (por exemplo `TextSegement` → `TextSegment`): código que usa o POI direto pode precisar de ajuste.

## redis

Com o `jedis` declarado, o bean `redisService` tem a API do plugin: `withRedis`, `memoize`, `memoizeObject`, `memoizeHash`, `memoizeHashField`, `memoizeScore`, `withPipeline`, `withTransaction`, `withOptionalRedis`, `deleteKeysWithPattern`, comandos do Jedis direto (`redisService.del('x')`), conexões nomeadas (`redisService<Nome>`, `withConnection('cache')`), sentinel. Configuração em `grails.redis`; sem ela, `localhost:6379`. Faltam `memoizeDomainList`, `memoizeDomainObject` e `@Memoize`.

## rabbitmq-native

Com `rabbitmq.connections` e o `amqp-client` declarado: conexão, declaração de `rabbitmq.exchanges` e `rabbitmq.queues`, bean `rabbitMessagePublisher` (`send` com `RabbitMessageProperties`, closure ou `send(fila, corpo)`; Map/List viram JSON). `ssl: true` valida o certificado e o host (o plugin original confiava em qualquer um). Faltam consumers (`grails-app/rabbit-consumers`), RPC e confirms. `static rabbitQueue` em services (do plugin rabbitmq antigo) é ignorado com aviso, como no rabbitmq-native.

## rendering

Com o `flying-saucer-pdf` declarado: `pdfRenderingService.render(...)` para a resposta, um `OutputStream` ou um `ByteArrayOutputStream`, e `renderPdf(...)` nos controllers. Imagens embutidas com `<rendering:inlinePng>`, `inlineJpeg`, `inlineGif`. O template precisa ser XHTML bem formado. Falta renderizar GIF, PNG e JPEG como saída.

## audit-logging

Domain classes `Auditable`, configuração em `grails.plugin.auditLog.*` com as chaves e padrões do plugin (`auditDomainClassName` obrigatório), gravação depois do commit, `asyncWrite`, `auditRequestResolver`, `AuditLogContext.withoutAuditLog`/`withConfig`. Faltam: `Stampable` persistir `createdBy`/`lastUpdatedBy` (compila, mas não grava) e vários datasources.

## hibernate-filter

`static hibernateFilters = { nome(condition: '...', default: true) }`, `withoutHibernateFilters`, `withoutHibernateFilter`, `withHibernateFilters` (religa os padrões), `enableHibernateFilter`, `disableHibernateFilter`. Filtros com parâmetros, `collection` e `aliasDomain` são ignorados com aviso.

## spring-session e cookie-session

Não são lidos. Configure `grox.session.store: redis` (ou `memory`). As sessões existentes não são migradas. Veja [Sessão HTTP](../recursos/sessao.md).

## GPars

O jar do GPars não roda no Groovy 6. O Grox oferece o mínimo usado nos projetos: `GParsPool.withPool { collectParallel / eachParallel / findAllParallel }` e `DefaultPGroup.task`, sobre o `groovy.concurrent` do Groovy 6. Atores e dataflow do GPars não existem: use os do `groovy.concurrent`.

## Plugin que não existe no Grox

1. Veja se o uso é pequeno o bastante para trocar por código da aplicação ou por uma biblioteca Java.
2. Se a biblioteca do plugin for Java puro (sem Groovy ≤ 5 e sem Spring), declare-a e escreva o equivalente do bean num `resources.groovy`.
3. Se for preciso reimplementar a API, o caminho é o mesmo dos plugins existentes: um módulo em `org.grox.plugins.*` com a API no pacote original (veja `docs/desenvolvimento.md` no repositório).

## Veja também

- [Plugins reimplementados](../recursos/plugins.md)
- [Armadilhas da migração](armadilhas.md)
- [Beans e resources.groovy](../recursos/beans.md)
- [Compatibilidade com o Grails](../referencia/compatibilidade.md)
