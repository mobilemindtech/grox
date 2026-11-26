# Visão geral dos módulos

O Grox não precisa do layout `grails-app/` nem da camada de compatibilidade com o Grails. Uma aplicação pode usar só o núcleo (Groovy, atores e Netty), mais os módulos de dados, views e segurança de que precisar, e declarar as rotas com o `grox-dsl`. Este capítulo mostra o que cada módulo traz, o que fica de fora sem o módulo Grails e como escolher.

O `sample-plain/` do repositório é a referência executável desta trilha: ele sobe sem o módulo Grails no classpath, e o seu teste (`SamplePlainTest`) confere isso.

## Os módulos

O framework é dividido em subprojetos `framework/grox-*`. Cada um é um jar, e cada jar registra o seu módulo (`GroxModule`) por `ServiceLoader`: basta estar no classpath para ser carregado na subida.

| Módulo | O que traz | Depende de |
|---|---|---|
| `grox-core` | Netty, atores, o pipeline HTTP (sessão em memória ou Redis, estáticos em `/static/**`, `/actuator`, access log, cabeçalhos de segurança, páginas de erro), `GroxRequest`/`GroxResponse`/`GroxParams`, configuração (`application.yml`, `application.groovy`, `runtime.groovy`), ambientes, i18n, services e services-ator, `grails.async` (`Promise`, `Promises`), injeção por nome | |
| `grox-gorm` | A API do GORM sobre o Hibernate 7.4 e o HikariCP: domain classes, finders, `where { }`, `createCriteria`, HQL, validação e mensagens, binding, transações (`@Transactional`), multi-tenancy DISCRIMINATOR, `@grails.gorm.annotation.Entity` | core |
| `grox-gsp` | Motor GSP, layouts SiteMesh 3, as TagLibs nativas (`g:`), inclusive as de erros de validação, e a DSL de HTML (`render { }`) | core |
| `grox-security` | O motor de segurança: regras de URL, expressões de acesso, login por formulário, HTTP Basic, JWT, CSRF, bcrypt, limite de tentativas de login, usuários em memória | core |
| `grox-security-gorm` | Usuários e roles em domain classes (`userLookup.*`), tokens REST em domain class (`rest.token.storage.useGorm`) e o tenant do usuário logado como resolver padrão de multi-tenancy | gorm, security |
| `grox-assets` | O servidor de assets (`/assets/**`): processado na hora em desenvolvimento, pré-compilado em produção | core |
| `grox-dsl` | Rotas por closure (classes `Routes`) e por anotação (`@Handler`, `@Get`...), respostas no estilo dos controllers do Grails, a DSL de projeto (`static app = { }`) e o script `GroxScript` | core, gorm, gsp, security |

O `grox-dsl` declara `grox-core`, `grox-gorm`, `grox-gsp` e `grox-security` como dependências `api`. Por isso, depender só dele já traz os quatro.

A configuração continua com as mesmas chaves das apps Grails. A segurança, por exemplo, fica em `grails.plugin.springsecurity.*` mesmo sem o plugin: são só chaves, sem dependência de código. A API do GORM também continua nos pacotes `grails.gorm.*`, porque é a API pública do GORM, que roda fora de uma app Grails. "Sem Grails" vale para a web e para a estrutura do projeto, não para o nome do pacote do GORM.

## O que fica de fora sem o módulo Grails

Os módulos `grox-grails` e `grox-grails-plugins` trazem o que é do Grails e dos seus plugins. Sem eles, estes recursos não existem:

| Recurso | Sem o módulo Grails |
|---|---|
| Controllers (`*Controller`), `ControllerApi`, `allowedMethods`, `withFormat` | Use rotas do `grox-dsl` (`Routes` ou `@Handler`) |
| `UrlMappings` | As rotas declaram o caminho (`get('/livros/{id}')`) |
| Interceptors (`*Interceptor`) | `before`, `after` e `error` na DSL de rotas e `@Before`/`@After`/`@OnError` nos `@Handler` (ver [Interceptores](interceptores.md)) |
| AST pelo diretório `grails-app/...` | Domain classes com `@Entity`; classes `@Handler` ganham a API pela anotação |
| `grailsApplication`, `Holders`, `springSecurityService`, `SpringSecurityUtils` | `config` e os beans pelo nome nos handlers; `request.userPrincipal` para o usuário |
| Tags `sec:` (plugin spring-security) e `asset:` (plugin asset-pipeline) | Use `request.userPrincipal` nas GSPs; os assets continuam servidos em `/assets/**`, mas sem as tags |
| `resources.groovy`, descritores `*GrailsPlugin` | Bloco `beans { }` da DSL de projeto |
| Plugins reimplementados (console, jobs, redis, rabbitmq, PDF, Excel, audit-logging) | Não disponíveis |
| Recarga de classes no `run` | Ainda não há nas apps sem `grails-app/` |
| Plugin Gradle `org.grox.app` (`compileGsp`, `assetCompile`, recarga) | O build é configurado à mão (ver [Projeto do zero](projeto-do-zero.md)) |

O pipeline HTTP é o mesmo das apps Grails: segurança, sessão, estáticos, `/actuator`, páginas de erro, cabeçalhos e métricas vêm do `grox-core`.

## Qual combinação escolher

| Quero | Módulos |
|---|---|
| Uma API JSON sem banco | `grox-dsl` (traz core, gorm, gsp e security; o GORM só sobe se houver domain classes ou `dataSource`) |
| Uma app com banco e páginas HTML | `grox-dsl`, mais o driver JDBC (`runtimeOnly`) |
| Usuários e roles no banco | acrescente `grox-security-gorm` |
| Multi-tenancy com o tenant do usuário logado | acrescente `grox-security-gorm` |
| CSS e JS servidos com digest | acrescente `grox-assets` |
| Migrar uma app Grails existente | o agregador `framework` (com `grox-grails` e os plugins) e o layout `grails-app/`: veja a [trilha de migração](../migracao/visao-geral.md) |

Os controllers e as rotas do `grox-dsl` também podem conviver numa mesma app Grails: os controllers são consultados primeiro.

## Veja também

- [Projeto do zero](projeto-do-zero.md)
- [A arquitetura em uma página](../introducao/arquitetura.md)
- [Quando usar (e quando não usar)](../introducao/quando-usar.md)
- [Compatibilidade com o Grails](../referencia/compatibilidade.md)
