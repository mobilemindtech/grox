# O que é o Grox

Este capítulo apresenta o Grox: o problema que ele resolve, o que ele oferece e como ele se relaciona com o Grails.

## Em uma frase

O Grox é um framework web para **Groovy 6** que roda código escrito para o Grails (controllers, services, domain classes, GSPs, TagLibs, `UrlMappings`, `application.yml`) **sem Spring**, com o tráfego atendido pelo **Netty** e as actions executadas por **atores** em virtual threads.

## Por que ele existe

O Grails 8 (e todos os anteriores) é compilado com o Groovy 5 ou anterior. Esses jars usam `org.codehaus.groovy.runtime.callsite.CallSite`, que **não existe no Groovy 6**: o GORM, o GSP e os plugins falham com `NoClassDefFoundError` assim que são carregados. Além disso, o Grails traz o Spring inteiro junto.

O Grox reimplementa a **API pública** do Grails com as mesmas classes, os mesmos nomes de pacote e as mesmas chaves de configuração. O código da aplicação compila e roda com poucas ou nenhuma alteração, e o que fica por baixo é bem menor:

| Peça | No Grails | No Grox |
|---|---|---|
| Servidor HTTP | Tomcat (Servlet) | Netty 4.2, sem container de Servlet |
| Contêiner de beans | Spring | Injeção por nome própria (`Autowirer`) |
| Execução das actions | Thread do Tomcat | Atores do Groovy 6 em virtual threads |
| GORM | GORM for Hibernate | GORM próprio sobre Hibernate 7.4, mapeado por um `orm.xml` gerado |
| GSP | grails-gsp | Motor GSP próprio, com pré-compilação no build |
| Layouts | SiteMesh 2 | SiteMesh 3 |
| Segurança | spring-security-core e rest | Motor próprio que lê as mesmas chaves (`grails.plugin.springsecurity.*`) |
| Sessão | Servlet / spring-session | Memória ou Redis (Lettuce), gravação diferencial |
| Assets | asset-pipeline | Asset pipeline próprio com as mesmas convenções |

## O que você ganha

- **Código Grails no Groovy 6**, com as novidades da linguagem e do JDK 27.
- **Subida rápida:** um projeto real com 61 domain classes sobe em 10 a 15 s (o mesmo projeto levava 21 a 35 s antes das otimizações), e a recarga de classes em desenvolvimento troca controllers e services em 100 ms a 1 s, sem reiniciar a JVM.
- **Concorrência explícita:** cada controller roda num pool de atores; services podem ser atores (`static actor = true`) e então dispensam sincronização.
- **Segurança mais rígida por padrão:** CSRF ligado, cabeçalhos de segurança, limite de tentativas de login, cookie `Secure` automático em HTTPS, allowlist na desserialização da sessão.
- **Operação embutida:** `/actuator/health`, `info`, `metrics` e `prometheus`, access log, desligamento gracioso.
- **Módulos:** um núcleo sem nada de Grails (`grox-core`) e módulos opcionais (GORM, GSP, assets, segurança, compatibilidade Grails, plugins). Uma aplicação nova pode usar só o que precisa, com rotas por closure ou anotação (`grox-dsl`).

## O que ele não é

- **Não é um fork do Grails** nem usa os jars dele. Recursos do Grails que nenhum projeto migrado usou ainda não existem (por exemplo namespaces de controller, `chain`/`forward`, JSON Views, modos DATABASE e SCHEMA de multi-tenancy). A lista completa está em [Compatibilidade com o Grails](../referencia/compatibilidade.md).
- **Não tem CLI, profiles nem scaffolding.** O build é Gradle puro, com os plugins `org.grox.app` (aplicação) e `org.grox.lib` (biblioteca de artefatos, no papel de um plugin do Grails).
- **Não é um container Servlet.** `request`, `response` e `session` implementam as interfaces do `jakarta.servlet-api` 6.1 para o código tipado continuar compilando, mas o que depende de container (`startAsync`, `getRequestDispatcher`, filtros Servlet) não existe.
- **Ainda não é estável:** é um protótipo. Veja o [Status](../README.md#status).

## Os módulos

| Módulo | Conteúdo |
|---|---|
| `grox-core` | Netty, pipeline HTTP, sessão (memória/Redis), configuração, roteamento genérico, i18n, services e services-ator, management, tarefas assíncronas, recarga |
| `grox-gorm` | Domain classes, API do GORM (`grails.gorm.*`), validação, binding, consultas, multi-tenancy |
| `grox-gsp` | Motor GSP, TagLibs, tags `g:` nativas, layouts SiteMesh 3 |
| `grox-assets` | Asset pipeline (`grails-app/assets`, digest, manifest) |
| `grox-security` | Filtro de segurança, regras de acesso, form login, Basic, JWT, CSRF, bcrypt |
| `grox-security-gorm` | Usuários e tokens em domain classes, tenant do usuário logado |
| `grox-grails` | Controllers, `UrlMappings`, interceptors, layout `grails-app/`, classes `grails.*` da web, `GrailsApplication` |
| `grox-grails-plugins` | Reimplementações dos plugins (spring-security, asset-pipeline, console, quartz, excel-export, redis, rabbitmq-native, rendering, audit-logging) |
| `grox-dsl` | Rotas por closure e anotação, DSL de projeto, para apps sem `grails-app/` |
| `framework` | Agregador de todos os módulos: é dele que uma aplicação Grails depende |

## Veja também

- [Quando usar (e quando não usar)](quando-usar.md)
- [A arquitetura em uma página](arquitetura.md)
- [Visão geral dos módulos](../sem-grails/visao-geral.md)
