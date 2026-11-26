# Casos reais: apps A, B e C

Este capítulo descreve os três projetos Grails reais migrados para o Grox: o tamanho, o que cada um exigiu do framework, o que mudou no código e o resultado. Eles servem de referência para estimar o esforço de uma migração e de modelo para organizar a sua.

## Organização no repositório

Os candidatos ficam em `candidates/`, um subprojeto Gradle cada:

| Arquivo | Papel |
|---|---|
| `build.gradle` | Build sobre o Grox (plugin `org.grox.app` ou `org.grox.lib`) |
| `import.sh` | Copia `grails-app/` e `src/` do repositório real (não versionados aqui) e aplica `overrides/` e `patches/` |
| `overrides/` | Arquivos que substituem ou se somam aos importados: `application.yml` sem credenciais, BootStrap de teste, views simplificadas |
| `patches/` | Alterações mínimas no código, um patch por arquivo; são as que iriam para o projeto real |
| `make-patches.sh` | Regera os patches depois de editar o código importado |
| `run-h2.sh` | Development com H2 em memória (modo MySQL), jobs e integrações desligados |
| `run-mysql.sh` | Development contra o banco local, com credenciais só no ambiente |

O `application.yml` de `overrides/` é gerado por `candidates/sanitize-yml.py PREFIXO origem destino` e revisado à mão: os valores sensíveis viram `${APPA_...}`, `${APPB_...}` e assim por diante. **Nada de credencial no repositório.**

## app A (piloto)

Projeto Grails 8.0.0-M5 com Hibernate 7 e MySQL.

| Área | Uso |
|---|---|
| Tamanho | 57 controllers, 32 services, 61 domain classes (43 multi-tenant), 12 jobs, 9 TagLibs, 193 GSPs, 200 assets |
| Plugins | spring-security-core e rest (token em GORM, `X-Auth-Token`, `chainMap`), quartz, excel-export, cache (só configurado), web-console, asset-pipeline, cookie-session (desligado), spring-session-redis |
| Bibliotecas | HTTPBuilder 0.8, commons-email, POI 3 (HSSF), commonmark, nimbus-jose-jwt, biblioteca interna Java, groovy-sql |
| GORM | `countBy*` com vários `And`, `createCriteria` com projeções, `groupProperty` fora de `projections`, `createAlias`, `listDistinct`, HQL, `executeUpdate`, `merge`, `attach`, `Tenants.withId`, Data Service, `@Listener(User)` com `isDirty`, `formula`, `sqlType`, `type: 'text'`, `static indexes`, `tenantId` primitivo, many-to-many |
| Web | `respond` com `domainSuffix: Instance`, `render(status:) { json }`, `render(404)`, `request.withFormat`, `request.JSON` como `JSONObject`, `RestfulController`, `groovyPageRenderer`, `assetResourceLocator`, `sessionFactory`, `dataSource` |
| GSP | 70 tags distintas, inclusive `g:datePicker`, `g:actionSubmit`, `g:formatBoolean`, `g:renderException`, `asset:script`/`deferredScripts`, `${assetPath(...)}` sem prefixo, `g:if env=` |

**Resultado:**
- compila 100% (Groovy, 193 GSPs, 200 assets);
- sobe em 10 a 15 s com H2 (eram 21 a 35 s antes das otimizações de subida) e em cerca de 8 s contra o MySQL de desenvolvimento, com DDL pendente vazio;
- numa varredura GET de 162 actions logado como admin, 100 respondem 200, as demais 3xx/4xx esperados, e os 22 erros 500 analisados são da aplicação no ambiente de teste (parâmetros ausentes, integrações sem configuração, banco vazio, `session.createSQLQuery` e um método comentado);
- sessão no Redis (cookie `APPSESSION`, 60 min, chaves `app:grox:session:*`) validada com login e reinício.

**Patches:** 8 arquivos, todos para trocar o HTTPBuilder pelo `groovy.http.HttpBuilder` (services de pagamento, NFS-e, SMS, arquivos).

**Overrides:** credenciais por `${APPA_...}`, `csrf.enabled: false` (os formulários não enviam o token), `PilotBootStrap` com o usuário `pilot` (todos os papéis, só development com H2).

**O que entrou no Grox por causa dele:** os plugins console, quartz e excel-export; many-to-many; listeners `@Listener`; dirty checking; Data Services; `DetachedCriteria` com `distinct`; `createAlias`; placeholders no config; `resources.groovy`; ~40 classes de compatibilidade (`grails.boot.GrailsApp`, `Holders`, `JSONObject`, `HttpStatus`, `@Autowired`, `MessageSource`, `RequestContextHolder`, `DataIntegrityViolationException`...); naming com palavras reservadas; chave composta (`UserRole`); `dbCreate: update` seguro.

**Lição:** antes das correções, um `dbCreate: update` contra o banco de desenvolvimento criou tabelas e colunas novas e alterou colunas para `decimal(38,2)`. Por isso o modo safe é o padrão e o `grox.schema.updateScript` existe: rode-o antes de qualquer `update` num banco real.

## app B

Aplicação multi-tenant com MySQL e Solr.

**Exigiu do Grox:** `@GrailsCompileStatic`/`@GrailsTypeChecked` (compilam dinâmicos), `SessionTenantResolver`, `GrailsWebRequest`, Spring Security no estilo Grails (`GrailsUser`, `GrailsUserDetailsService`, `UserDetailsService` do `resources.groovy`, `UsernameNotFoundException`), senhas `{SHA-256}` sem sal com 1 iteração, `org.springframework.validation.Errors`, interfaces Servlet, listeners `AbstractPersistenceEventListener`, `hasMany` de valores simples (`hasMany = [tags: String]`), namespace de TagLib como propriedade (`auth.isAdmin()`), `principal` anônimo `'anonymousUser'`, finders booleanos (`findAllEnabled()`), open session in view na página de login (layout com coleção lazy), `LogoutController` embutido, `params.controller`/`action` na página de login embutida.

**Patches:** dois services trocam o HTTPBuilder (`java.net.http.HttpClient` e `groovy.http.HttpBuilder`).

**Overrides:** yml sanitizado (`APPB_*`; o sanitizador também limpou senhas que já estavam em placeholders), CSRF desligado, `CandidateBootStrap` que cria os tenants e os dados padrão.

**Resultado:** sobe com H2 e navega: anônimo, 10 páginas sem erro; logado como admin, 90 páginas e 3 erros, todos bugs da aplicação (também no Grails). Falta rodar contra o MySQL e o Solr.

## app C

O maior: vários módulos com um núcleo compartilhado.

| Subprojeto | Conteúdo |
|---|---|
| `app-c-core` | Domains + services (+ AST própria): biblioteca `org.grox.lib`, 4.424 classes, 404 domains |
| `app-c-web` | Aplicação web + TagLibs: 198 controllers, 1.914 GSPs, 134 services |
| `app-c-api` | API REST (spring-security-rest, Swagger): 13 controllers |
| `app-c-jobs` | Só jobs: 37 jobs, tela de status em `/jobExecution` |
| Utilitários | Seis bibliotecas internas: fontes recompilados com Groovy 6 |

**Exigiu do Grox:** bibliotecas `org.grox.lib` (índice de pacotes, mensagens mescladas), audit-logging, redis, rabbitmq-native, rendering, hibernate-filter; descritores `*GrailsPlugin` e `Application.doWithSpring()`; `@PostConstruct`; API de transação do Spring; encoders do Spring Security com upgrade de hash; modelo de mapeamento do GORM; `PropertyEditorRegistrar`; GPars mínimo; actions closure; config null-safe; HQL com `?0`; `hasOne` (inclusive com filho multi-tenant ausente); artefatos no pacote padrão; `LoginController` próprio com `SecurityContextHolder` e `HttpSessionRequestCache`; cookies da Servlet API; `modelAndView` no interceptor; tags `g:` nas actions; `chainMap` do spring-security-rest; `RestTokenCreationEvent`; `principal as GrailsUser`; `grails.databinding.dateFormats`; naming do GORM com dígitos e siglas; codecs de digest; one-to-one sem `hasOne`.

**Patches:** poucos e pontuais: `@DelegatesTo` com tipo parametrizado numa biblioteca interna, nome de classe do POI 5, HQL com nomes de coluna (3 lugares), tamanhos de coluna em duas domains, mapeamentos de erro para views `.gson`.

**Overrides:** ymls sanitizados por aplicação, CSRF desligado no app-c-web (146 views com POST simples e 137 com AJAX POST não enviam o token), Flyway como no-op (`APPC_SKIP_FLYWAY`), `CandidateBootStrap` por aplicação, views simplificadas no app-c-jobs (Bootstrap 5 pelo asset pipeline), descritor próprio do plugin swagger.

**Resultado:**
- `app-c-jobs` sobe no H2 com os 37 jobs e a tela de execução;
- `app-c-web` sobe no H2, faz login e navega o menu inteiro (900 páginas) sem erros do Grox; contra o MySQL, o dashboard funciona;
- `app-c-api` sobe no H2; login REST e endpoints com token respondem 200. Contra o MySQL, o `update` do Hibernate 7 com `MySQLDialect` explícito segurava a subida (corrigido: o modo safe vale também com dialect explícito).

**Pendências:** swagger sem paths, consulta nativa com `SUBTIME` (só MySQL), jobs contra o MySQL.

## O que esses casos ensinam

- **O código da aplicação quase não muda.** Os patches são de bibliotecas incompatíveis e de bugs que também existem no Grails moderno.
- **CSRF** é a decisão de configuração mais comum: desligar mantém a compatibilidade; ligar exige tocar nas views.
- **Banco real com cuidado:** `dbCreate: none` e `grox.schema.updateScript` primeiro.
- **H2 primeiro:** subir e navegar com banco em memória separa rápido os problemas do Grox dos de dados.
- **Separe os erros:** muitos 500 numa varredura são da aplicação no ambiente de teste.
- **Projetos com vários módulos** usam `org.grox.lib` para o núcleo compartilhado.

## Veja também

- [Passo a passo](passo-a-passo.md)
- [Armadilhas da migração](armadilhas.md)
- [Plugins do Grails](plugins.md)
- [Estrutura de uma aplicação](../recursos/estrutura.md)
