# Configuração

Esta é a referência de todas as chaves de configuração que o Grox lê. As chaves dos plugins do Grails (`grails.plugin.springsecurity.*`, `grails.gorm.multiTenancy.*`, `grails.plugin.auditLog.*`, `grails.redis.*`, `rabbitmq.*`) têm o mesmo nome e o mesmo padrão dos plugins originais, para que o `application.yml` de uma app Grails funcione sem mudanças. As chaves próprias do framework ficam em `grox.*`.

## Fontes e precedência

A configuração vem destes lugares, do que vence para o que perde:

| Ordem | Fonte | Exemplo |
|---:|---|---|
| 1 | Propriedade de sistema | `-Dserver.port=9000` |
| 2 | Variável de ambiente (pontos viram `_`, maiúsculas) | `SERVER_PORT=9000` |
| 3 | `runtime.groovy` | `grox.console.enabled = true` |
| 4 | `application.groovy` | `grails.plugin.springsecurity.controllerAnnotations.staticRules = [...]` |
| 5 | `application.yml` | `server: { port: 8080 }` |
| 6 | `config { }` da DSL de projeto (só nas apps `grox-dsl`) | `config { grox.routes.poolSize = 4 }` |
| 7 | Padrão do framework | `8080` |

- **Onde ficam os arquivos:** em `grails-app/conf/` nas apps com o layout do Grails; no classpath (`src/main/resources/`) nas apps sem `grails-app/`.
- **Groovy antes do YAML:** a ordem segue a do Grails, que registra o carregador do `application.groovy` antes do do YAML. Os arquivos `.groovy` usam a sintaxe do `ConfigSlurper`, com `environments { }`, e o `runtime.groovy` pode referenciar classes da aplicação.
- **Arquivos importados (`spring.config.import`):** como no Spring Boot e no Grails 8, um arquivo de configuração importa outros, por exemplo a configuração compartilhada por várias apps que fica numa biblioteca (`org.grox.lib`, com o `grails-app/conf` no classpath). O valor é uma string separada por vírgula ou uma lista; os prefixos são `classpath:` (o padrão), `file:` e `optional:` (pode faltar; sem ele, um arquivo ausente impede a subida). O importado vence o arquivo que o importou e é lido no formato da extensão (`.yml` com `environments:`, `.groovy` pelo `ConfigSlurper`); um importado pode importar outros. Na tabela acima, os importados pelo `application.yml` ficam entre ele e o `application.groovy`, e os importados pelos arquivos `.groovy`, logo acima de quem os importou.

  ```yaml
  # grails-app/conf/application.yml de cada app
  spring:
    config:
      import: classpath:shared.yml,classpath:rabbitmq.groovy,optional:file:/etc/app/extra.yml
  ```

- **Variáveis de ambiente só valem para valores-folha.** Um bloco do YAML (`mail:`) nunca é escondido por uma variável homônima (`MAIL` do shell).
- **Vários documentos e ambientes:** o YAML aceita documentos separados por `---`, o bloco `environments:` e chaves com ponto (`grox.session.store: redis`). Veja [Ambientes](ambientes.md).
- **Recarga:** mudar qualquer um dos arquivos de configuração em desenvolvimento (inclusive os importados: qualquer `.yml` ou `.groovy` na raiz dos recursos) faz a recarga completa da aplicação.

### Placeholders

Valores podem usar `${NOME}` ou `${NOME:padrão}`, resolvidos na carga. `NOME` é procurado como propriedade de sistema, variável de ambiente ou outra chave da própria configuração. Funciona também dentro de listas de mapas (`rabbitmq.connections`).

```yaml
dataSource:
    url: "${DB_URL:jdbc:h2:mem:dev}"
    password: "${DB_PASSWORD:}"
api:
    base: "https://exemplo.com"
    pagamentos: "${api.base}/pagamentos"
```

### Lendo a configuração na aplicação

```groovy
grailsApplication.config.getProperty('api.base')            // String ou null
grailsApplication.config.getProperty('api.timeout', Integer, 10)
config.api.base                                              // navegação
```

- `config.chaveInexistente.outra` não lança `NullPointerException`: devolve um navegador vazio que vale `false` (`config.x ?: padrao` funciona e `for (p in config.x.lista)` não itera), como no Grails.
- `getProperty('a.b')` de uma chave inexistente devolve `null`.
- Nas rotas e handlers do `grox-dsl`, `config` é a configuração da aplicação. Fora do código Grails, use o bean `groxApplication` (`groxApplication.config`).

## Servidor

| Chave | Padrão | Descrição |
|---|---|---|
| `server.port` | `8080` | Porta HTTP. `0` escolhe uma porta livre (útil em testes; leia em `app.port`) |
| `server.address` | `0.0.0.0` | Interface de escuta |

## HTTP (`grox.http`)

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.http.maxContentLength` | `10485760` (10 MB) | Corpo máximo em bytes. Acima disso, `413`. Também limita uploads, que ficam em memória |
| `grox.http.workerThreads` | `0` | Event loops do Netty. `0` deixa o Netty decidir |
| `grox.http.requestTimeout` | `30` | Segundos para receber a requisição inteira a partir do primeiro byte (contra slowloris). `0` desliga |
| `grox.http.idleTimeout` | `60` | Segundos sem tráfego até fechar uma conexão keep-alive sem resposta pendente. `0` desliga |
| `grox.http.maxConnections` | `10000` | Conexões simultâneas; as excedentes são fechadas. `0` sem limite |
| `grox.http.maxPipelined` | `16` | Requisições em voo por conexão (HTTP pipelining) antes de pausar a leitura |
| `grox.http.trustedProxies` | loopback, `10.x`, `172.16-31.x`, `192.168.x`, `169.254.x`, `::1` | Regex dos endereços cujos `X-Forwarded-For`, `X-Forwarded-Proto` e `X-Forwarded-Host` são aceitos, como o `internalProxies` do Tomcat. Vazio (`''`): nenhum *(extensão Grox)* |
| `grox.http.securityHeaders` | ver abaixo | Cabeçalhos acrescentados às respostas que não os definem *(extensão Grox)* |

`grox.http.securityHeaders` envia por padrão `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN` e `Referrer-Policy: strict-origin-when-cross-origin`. Um mapa troca ou acrescenta cabeçalhos, valor vazio remove um, e `false` desliga todos:

```yaml
grox:
  http:
    securityHeaders:
      Strict-Transport-Security: "max-age=31536000; includeSubDomains"
      X-Frame-Options: DENY
      Referrer-Policy: ''          # remove
```

## Controllers (`grox.controllers`)

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.controllers.concurrency` | `pool` | `pool`: N atores por controller, cada um com sua instância. `request`: um ator e uma instância novos por requisição. Cada controller pode trocar com `static concurrency` |
| `grox.controllers.poolSize` | 2 × CPUs | Atores por controller no modo `pool`. Cada controller pode trocar com `static poolSize` |

Veja [Concorrência](../recursos/concorrencia.md).

## Rotas do `grox-dsl` (`grox.routes`)

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.routes.execution` | `virtual` | `virtual`: uma virtual thread por requisição. `actor`: um pool de atores. Cada rota ou grupo troca com `execution:` |
| `grox.routes.poolSize` | 2 × CPUs | Atores por grupo no modo `actor`. Cada rota ou grupo troca com `poolSize:` |
| `grox.serverURL` | | Base dos links absolutos nas rotas do `grox-dsl` (nas apps Grails, `grails.serverURL`) |

## Sessão HTTP (`grox.session`)

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.session.store` | `memory` | `memory` (um nó) ou `redis` (compartilhada e persistente) |
| `grox.session.timeout` | `1800` | Segundos de inatividade até a sessão expirar |
| `grox.session.cookieName` | `GROXSESSIONID` | Nome do cookie |
| `grox.session.cookie.secure` | `auto` | Atributo `Secure` do cookie: `auto` (em HTTPS, inclusive atrás de proxy confiável), `true` ou `false` |
| `grox.session.redis.url` | `redis://localhost:6379` | URL do Redis. Com senha: `redis://:senha@host:6379` |
| `grox.session.redis.keyPrefix` | `grox:session:` | Prefixo das chaves (`<prefixo><id>`) |
| `grox.session.serialization.allow` | | Lista de pacotes extras aceitos na leitura da sessão do Redis. Os pacotes da app (o da classe `Application` e o `grails.codegen.defaultPackage`), o JDK comum e o framework já são aceitos |

```yaml
grox:
  session:
    store: redis
    timeout: 3600
    cookieName: MINHAAPP
    redis:
      url: "${REDIS_URL:redis://localhost:6379}"
      keyPrefix: 'minhaapp:session:'
    serialization:
      allow: [br.com.empresa.comum]
```

Veja [Sessão HTTP](../recursos/sessao.md).

## Segurança própria do Grox (`grox.security`)

Estas chaves complementam as do spring-security (mais abaixo).

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.security.loginThrottle.enabled` | `true` | Limite de tentativas de login (formulário, REST e Basic). Acima dele, o formulário mostra `springSecurity.errors.login.throttled`, e REST e Basic respondem `429` com `Retry-After` |
| `grox.security.loginThrottle.maxAttempts` | `5` | Falhas por IP + usuário na janela |
| `grox.security.loginThrottle.maxAttemptsPerUser` | `20` | Falhas por usuário, de qualquer IP, na janela. Não há limite só por IP: atrás de proxy, todos teriam o mesmo |
| `grox.security.loginThrottle.windowSeconds` | `300` | Duração da janela |
| `grox.security.tokenRevocation.store` | igual a `grox.session.store` | Onde ficam os JWT revogados no logout: `memory` ou `redis` |
| `grox.security.users`, `grox.security.rules` | | Preenchidas pelo bloco `security { }` da DSL de projeto (`user` e `rule`). Prefira a DSL a escrevê-las à mão |

## Internacionalização (`grox.i18n`)

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.i18n.defaultLocale` | locale da JVM | Locale usado quando a requisição não define outro. Ex.: `pt-BR` |

## Views e estáticos

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.gsp.reload` | `true` em development | Views, estáticos e assets lidos dos diretórios de fontes e recompilados quando mudam. Fora de development, vêm do classpath |
| `grox.gsp.viewsDir` | `grails-app/views` | Diretório das views em desenvolvimento (também das `.pug` e `.md`) |
| `grox.pug.reload` | o de `grox.gsp.reload` | Relê os templates Pug quando mudam; desligado, templates e expressões ficam em cache |
| `grox.pug.prettyPrint` | `false` | HTML do Pug indentado |
| `grox.markdown.reload` | o de `grox.gsp.reload` | Relê as views `.md` quando mudam |
| `grox.static.prefix` | `/static` | Prefixo da URL dos estáticos |
| `grox.static.dir` | `src/main/resources/public` | Diretório dos estáticos em desenvolvimento |
| `grails.sitemesh.default.layout` | | Layout usado quando a página não escolhe outro |
| `grails.assets.mapping` | `assets` | Prefixo da URL dos assets (`/assets/**`) |
| `grox.assets.dir` | `grails-app/assets` | Diretório dos assets em desenvolvimento |

Os diretórios de desenvolvimento só são usados com `grox.gsp.reload` ligado e quando o diretório existe (por isso o `run` roda no diretório do projeto).

## Recarga de classes (`grox.reload`)

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.reload.enabled` | `true` | Recarga de classes no `run` em development. Também `-Dgrox.reload.enabled=false` |
| `-Dgrox.reload.compile` | `true` | `false`: não inicia o Gradle contínuo; a IDE compila e a recarga dispara quando os diretórios de classes param de mudar |
| `-Dgrox.reload.interval` | `500` | Intervalo do monitoramento, em ms |
| `-Dgrox.reload.classes`, `-Dgrox.reload.resources`, `-Dgrox.reload.gradlew`, `-Dgrox.reload.tasks`, `-Dgrox.reload.stamp` | | Definidas pelo plugin Gradle no `run`. Não as defina à mão |

A extensão `grox { reload = false }` do `build.gradle` desliga a recarga sem passar as propriedades. Veja [Recarga em desenvolvimento](../recursos/recarga.md).

## Gestão e logs

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.management.enabled` | `true` | Liga os endpoints de gestão |
| `grox.management.basePath` | `/actuator` | Prefixo de `/health`, `/info`, `/metrics` e `/prometheus` |
| `grox.management.metricsAccess` | `[ROLE_ADMIN]` com segurança ativa | Regras de acesso de `/metrics` e `/prometheus`. `/health` e `/info` são públicos |
| `grox.accessLog.enabled` | `false` | Uma linha por requisição no logger `grox.access` |
| `grox.accessLog.format` | `text` | `text` (`127.0.0.1 admin "GET /x" 200 12ms usuario.index`) ou `json` |

Veja [Operação](../recursos/operacao.md).

## Execução e ciclo de vida

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.async.timeout` | `60` | Segundos para uma action assíncrona completar. Depois disso, `503` |
| `grox.shutdown.timeout` | `30` | Segundos de espera pelas requisições em andamento no desligamento |
| `grox.startup.parallel` | `true` | Hibernate e metadados das domain classes sobem em paralelo com o resto |
| `grox.jobs.enabled` | `true` | Liga os jobs de `grails-app/jobs` |
| `quartz.autoStartup`, `grails.plugin.quartz.autoStartup` | `true` | Chaves do plugin quartz; `false` também desliga os jobs |

## Banco de dados (`dataSource`)

| Chave | Padrão | Descrição |
|---|---|---|
| `dataSource.url` | `jdbc:h2:mem:groxDb;DB_CLOSE_DELAY=-1` | URL JDBC. O driver precisa estar no classpath (`runtimeOnly`) |
| `dataSource.driverClassName` | | Classe do driver |
| `dataSource.username` | `sa` | |
| `dataSource.password` | `''` | |
| `dataSource.dbCreate` | `none` | `create-drop`, `create`, `update`, `validate` ou `none` |
| `dataSource.logSql` | `false` | Mostra o SQL gerado |
| `dataSource.formatSql` | `false` | Formata o SQL mostrado |
| `dataSource.properties.*` | | Repassado ao HikariCP (`maximumPoolSize`, `connectionTimeout`, `idleTimeout`...) |

```yaml
dataSource:
  driverClassName: com.mysql.cj.jdbc.Driver
  url: "${DB_URL}"
  username: "${DB_USER}"
  password: "${DB_PASSWORD}"
  dbCreate: none
  properties:
    maximumPoolSize: 20
    connectionTimeout: 5000
```

## Esquema e mapeamento (`grox.schema`, `grox.data`)

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.schema.update` | `safe` | Com `dbCreate: update`, `safe` só cria tabelas, colunas, chaves estrangeiras e índices, e nunca altera colunas existentes, como o GORM sobre Hibernate 5. `full` é o comportamento do Hibernate 7 (faz `modify column`). Vale também com `hibernate.dialect` explícito de MySQL, MariaDB, H2 ou PostgreSQL *(extensão Grox)* |
| `grox.schema.updateScript` | | Arquivo onde gravar o DDL que o `update` aplicaria, **sem executar nada** *(extensão Grox)* |
| `grox.schema.scriptOnly` | `false` | Com `updateScript`: encerra depois de gerar o script, antes do BootStrap *(extensão Grox)* |
| `grox.data.proxies` | `auto` | `auto`: proxy lazy só para entidades alvo de many-to-one; nas demais, `load(id)` carrega na hora. `all`: proxy em todas, como o GORM *(extensão Grox)* |
| `grox.data.ormXmlFile` | | Diagnóstico: arquivo onde gravar o `orm.xml` gerado a partir das domain classes |

Para ver o que mudaria num banco existente, sem tocar nele:

```bash
JAVA_OPTS="-Dgrox.schema.updateScript=/tmp/ddl.sql -Dgrox.schema.scriptOnly=true" bin/minha-app
```

## Hibernate (`hibernate.*`)

Tudo sob `hibernate.*` é repassado ao Hibernate. Chaves que o Grox define ou trata:

| Chave | Padrão no Grox | Descrição |
|---|---|---|
| `hibernate.default_batch_fetch_size` | `16` | Associações lazy pendentes carregadas juntas (`IN`), contra N+1. `1` volta ao comportamento do Hibernate |
| `hibernate.dialect` | detectado | Com MySQL, MariaDB, H2 ou PostgreSQL, trocado pela versão "safe" da mesma classe (ver `grox.schema.update`) |
| `hibernate.physical_naming_strategy` | snake_case do GORM | Tabelas e colunas como no GORM (`dateCreated` → `date_created`, `valor10Meses` → `valor10meses_id`) |
| `hibernate.auto_quote_keyword` | `true` | Palavras reservadas citadas (`User` → `` `user` ``), mantendo o snake_case |
| `hibernate.show_sql`, `hibernate.format_sql` | de `dataSource.logSql`/`formatSql` | |
| `hibernate.cache.*`, `hibernate.javax.cache.*` | | Cache de segundo nível (ex.: JCache + Ehcache), se a app declarar as dependências |

## Configurações gerais do Grails

| Chave | Padrão | Descrição |
|---|---|---|
| `grails.serverURL` | | Base dos links com `absolute: true` e das URLs relativas do PDF (plugin rendering) |
| `grails.databinding.dateFormats` | | Lista de formatos de `Date` aceitos no binding, tentados na ordem e sem leniência. Os formatos ISO do Grox continuam valendo depois deles |
| `grails.scaffolding.templates.domainSuffix` | `''` | Sufixo dos nomes do modelo do `respond` (`Instance` gera `usuarioInstance`, `usuarioInstanceList`) |
| `grails.codegen.defaultPackage` | | Pacote da app, aceito na leitura da sessão do Redis |
| `grails.env` | | Ambiente (como propriedade de sistema `-Dgrails.env`). Veja [Ambientes](ambientes.md) |

## Console web (`grox.console`)

| Chave | Padrão | Descrição |
|---|---|---|
| `grox.console.enabled` | `true` só em development | Também aceita `grails.plugin.console.enabled`, a chave do plugin. Fora de development, a subida avisa no log |
| `grox.console.path` | `/console` | |
| `grox.console.access` | `[ROLE_ADMIN]` | Regras de acesso com segurança ativa |
| `grox.console.localOnly` | `true` | **Sem** segurança ativa, só aceita requisições de localhost |

## Spring Security (`grails.plugin.springsecurity`)

A segurança fica ativa quando existe o bloco `grails.plugin.springsecurity` (e `active` não é `false`). As chaves e os padrões são os do spring-security-core e do spring-security-rest; as marcadas *(extensão Grox)* não existem nos plugins.

### Geral e regras

| Chave | Padrão | Descrição |
|---|---|---|
| `active` | `true` | `false` desliga a segurança mesmo com o bloco presente |
| `rejectIfNoRule` | `true` | Sem regra que case, nega |
| `interceptUrlMap` | | Lista de `{pattern, access, httpMethod}`. Vale a primeira que casar |
| `controllerAnnotations.staticRules` | | Mesmo formato (ou mapa `'/caminho': ['regra']`), avaliadas depois do `interceptUrlMap` |
| `stateless` | | Lista de padrões sem sessão nem CSRF; só Bearer ou Basic *(extensão Grox)* |
| `filterChain.chainMap` | | Lista de `{pattern, filters}`. `filters: 'none'` tira a cadeia de segurança; `'JOINED_FILTERS,-x,-y'` remove etapas. Vale a primeira entrada que casar |
| `users` | | Usuários em memória: `{username, password, roles}` *(extensão Grox)* |
| `csrf.enabled` | `true` | CSRF nas requisições com sessão *(extensão Grox: o plugin não tem CSRF)* |

Filtros que o `chainMap` distingue: `securityContextPersistenceFilter` (sessão), `restTokenValidationFilter` (token REST), `basicAuthenticationFilter`, `anonymousAuthenticationFilter`, `filterInvocationInterceptor` (autorização) e `exceptionTranslationFilter` (redirecionar para o login). Os demais nomes são aceitos e não mudam nada. Veja [Segurança: regras de acesso](../recursos/seguranca-regras.md).

### Usuários e papéis (`userLookup`, `authority`)

| Chave | Padrão | Descrição |
|---|---|---|
| `userLookup.userDomainClassName` | | Domain class do usuário (ex.: `app.auth.User`) |
| `userLookup.authorityJoinClassName` | | Domain class de junção usuário-papel (ex.: `app.auth.UserRole`). Sem ela, usa a propriedade `authorities` do usuário |
| `userLookup.usernamePropertyName` | `username` | |
| `userLookup.passwordPropertyName` | `password` | |
| `userLookup.enabledPropertyName` | `enabled` | |
| `userLookup.accountExpiredPropertyName` | `accountExpired` | |
| `userLookup.accountLockedPropertyName` | `accountLocked` | |
| `userLookup.passwordExpiredPropertyName` | `passwordExpired` | |
| `userLookup.authoritiesPropertyName` | `authorities` | |
| `userLookup.tenantPropertyName` | `tenantId` | Propriedade com o tenant do usuário *(extensão Grox)* |
| `authority.className` | | Domain class do papel (ex.: `app.auth.Role`) |
| `authority.nameField` | `authority` | |

### Login por formulário e logout

| Chave | Padrão | Descrição |
|---|---|---|
| `auth.loginFormUrl` | `/login/auth` | Página de login |
| `apf.filterProcessesUrl` | `/login/authenticate` | URL que recebe o POST do login |
| `apf.usernameParameter` | `username` | |
| `apf.passwordParameter` | `password` | |
| `successHandler.defaultTargetUrl` | `/` | Destino depois do login, sem URL salva |
| `successHandler.alwaysUseDefault` | `false` | Ignora a URL salva |
| `failureHandler.defaultFailureUrl` | `<loginFormUrl>?login_error=1` | |
| `logout.filterProcessesUrl` | `/logoff` | URL do logout |
| `logout.afterLogoutUrl` | `/` | |
| `logout.postOnly` | `true` | Logout só por POST (GET dá 405) |

### Senhas e HTTP Basic

| Chave | Padrão | Descrição |
|---|---|---|
| `password.algorithm` | `bcrypt` | `bcrypt`, `SHA-256`, `SHA-512`, `SHA-1` ou `MD5`. Grava no formato do `DelegatingPasswordEncoder` (`{bcrypt}...`, `{SHA-256}...`) |
| `password.bcrypt.logrounds` | `10` | Custo do bcrypt |
| `password.hash.iterations` | `10000` | Repetições dos algoritmos de digest |
| `password.encodeHashAsBase64` | `false` | Digest em base64 em vez de hex |
| `useBasicAuth` | `false` | Aceita HTTP Basic |
| `basic.realmName` | `Grox` | Realm do `WWW-Authenticate` |

### REST e tokens (spring-security-rest)

| Chave | Padrão | Descrição |
|---|---|---|
| `rest.active` | `true` se houver o bloco `rest` | Liga o login REST |
| `rest.login.endpointUrl` | `/api/login` | POST com `{"username", "password"}` |
| `rest.logout.endpointUrl` | `/api/logout` | Revoga o token |
| `rest.token.generation.refreshEndpointUrl` | `/oauth/access_token` | Renovação com `refresh_token` (só JWT) |
| `rest.token.storage.jwt.secret` | aleatório por execução | Segredo HS256, com **ao menos 32 bytes**. Sem ele, os tokens não sobrevivem a reinícios nem valem entre nós |
| `rest.token.storage.jwt.expiration` | `3600` | Validade do access token, em segundos |
| `rest.token.storage.jwt.refreshExpiration` | `2592000` (30 dias) | Validade do refresh token |
| `rest.token.storage.useGorm` | `false` | Token opaco gravado numa domain class, no lugar do JWT |
| `rest.token.storage.gorm.tokenDomainClassName` | | Obrigatória com `useGorm` |
| `rest.token.storage.gorm.tokenValuePropertyName` | `tokenValue` | |
| `rest.token.storage.gorm.usernamePropertyName` | `username` | |
| `rest.token.storage.gorm.cacheSeconds` | `30` | Cache do usuário por token. `0` desliga *(extensão Grox)* |
| `rest.token.validation.useBearerToken` | `true` | `Authorization: Bearer`. Com `false`, o token vem do cabeçalho `headerName` |
| `rest.token.validation.headerName` | `X-Auth-Token` | |

Exemplo completo:

```yaml
grails:
  plugin:
    springsecurity:
      userLookup:
        userDomainClassName: app.auth.User
        authorityJoinClassName: app.auth.UserRole
      authority:
        className: app.auth.Role
      password:
        algorithm: bcrypt
      rest:
        token:
          storage:
            jwt:
              secret: "${JWT_SECRET}"
      filterChain:
        chainMap:
          - {pattern: '/assets/**', filters: 'none'}
          - {pattern: '/api/**', filters: 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-securityContextPersistenceFilter'}
          - {pattern: '/**', filters: 'JOINED_FILTERS,-restTokenValidationFilter'}
      interceptUrlMap:
        - {pattern: '/', access: [permitAll]}
        - {pattern: '/assets/**', access: [permitAll]}
        - {pattern: '/api/**', access: ['isAuthenticated()']}
        - {pattern: '/**', access: ['isAuthenticated()']}
```

## Multi-tenancy (`grails.gorm.multiTenancy`)

| Chave | Padrão | Descrição |
|---|---|---|
| `grails.gorm.multiTenancy.mode` | `DISCRIMINATOR` se houver classes `MultiTenant`, senão `NONE` | Só `DISCRIMINATOR` é suportado |
| `grails.gorm.multiTenancy.tenantResolverClass` | `org.grox.security.gorm.UserTenantResolver` com segurança ativa | Classe do resolver. Sem segurança, é obrigatória quando há classes `MultiTenant` |

Resolvers prontos: `UserTenantResolver` (usuário logado ou claim `tenant` do JWT), `FixedTenantResolver`, `SystemPropertyTenantResolver` (`-Dgorm.tenantId`), `SessionTenantResolver` (atributo `gorm.tenantId` da sessão) e `AllTenantsResolver`. Veja [Multi-tenancy](../recursos/multi-tenancy.md).

## Auditoria (`grails.plugin.auditLog`)

O plugin é ativado por `auditDomainClassName`. Padrões do plugin audit-logging:

| Chave | Padrão | Descrição |
|---|---|---|
| `auditDomainClassName` | | Domain class de auditoria. Obrigatória: ativa o plugin |
| `disabled` | `false` | Desliga a auditoria |
| `verbose` | `true` | Grava todas as propriedades no INSERT e no DELETE |
| `logIds` | `true` | Grava ids das associações |
| `logFullClassName` | `true` | `className` com o pacote |
| `excluded` | `[version, lastUpdated, lastUpdatedBy]` | Propriedades não auditadas |
| `included` | | Se definida, só estas |
| `mask` | `[password]` | Propriedades gravadas como `propertyMask` |
| `propertyMask` | `**********` | |
| `defaultActor` | `SYS` | Autor quando não há usuário |
| `failOnError` | `false` | Erro ao gravar a auditoria propaga |
| `truncateLength` | `maxSize` da domain | Tamanho máximo de `oldValue`/`newValue` |
| `ignoreEvents` | | Eventos não auditados (`INSERT`, `UPDATE`, `DELETE`) |
| `asyncWrite` | `false` | Grava num executor depois do commit |
| `asyncWriteThreads` | `2` | |
| `asyncWriteQueueCapacity` | `10000` | |

## Redis (`grails.redis`)

Exige o `jedis` declarado pela app. Sem o bloco, como no plugin, conecta em `localhost:6379` no primeiro uso.

| Chave | Padrão | Descrição |
|---|---|---|
| `host` | `localhost` | |
| `port` | `6379` | |
| `timeout` | `2000` | ms |
| `password` | | |
| `database` | `0` | |
| `useSSL` | `false` | TLS com verificação do nome do host |
| `poolConfig` | | Propriedades do `JedisPoolConfig` (`maxTotal`, `maxIdle`...). Nome desconhecido gera aviso |
| `sentinels`, `masterName` | | Usa o `JedisSentinelPool` |
| `connections` | | Lista de conexões extras `{name, ...}`: bean `redisService<Nome>` e `redisService.withConnection('nome')` |

## RabbitMQ (`rabbitmq`)

Exige o `amqp-client` declarado pela app.

| Chave | Padrão | Descrição |
|---|---|---|
| `rabbitmq.enabled` | `true` | `false` desliga o plugin |
| `rabbitmq.connections` (ou `rabbitmq.connection`) | | Lista de `{name, host, port, username, password, virtualHost, requestedHeartbeat, automaticReconnect, ssl}`. Padrões: `localhost`, `5672` (`5671` com `ssl`), `/`, reconexão automática |
| `rabbitmq.exchanges` | | `{name, type, durable, autoDelete, arguments}`. `type` padrão `direct` |
| `rabbitmq.queues` | | `{name, durable, autoDelete, exclusive, arguments, exchange, binding, match, connection}` |

Com `ssl: true`, o certificado é validado pelo truststore da JVM (`-Djavax.net.ssl.trustStore=...`) e pelo nome do host.

## Ambiente

| Propriedade | Descrição |
|---|---|
| `-Dgrails.env`, `-Dgrox.env`, `GRAILS_ENV`, `GROX_ENV` | Ambiente explícito (nessa ordem). Aceita `dev` e `prod` |
| `-Dgrox.env.default` | Ambiente quando nenhum foi definido. Sem ele, `production` |

## Veja também

- [Ambientes](ambientes.md)
- [Plugins e tarefas Gradle](gradle.md)
- [Implantação em produção](../recomendacoes/implantacao.md)
- [Segurança: checklist](../recomendacoes/seguranca.md)
