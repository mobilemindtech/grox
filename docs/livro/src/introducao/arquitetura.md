# A arquitetura em uma página

Este capítulo mostra como o Grox atende uma requisição, onde o seu código roda, como o contexto (requisição, sessão de dados, usuário, tenant) chega até ele e como a aplicação sobe e desce. Não é preciso saber isso para escrever uma aplicação, mas ajuda a entender o comportamento dos guias seguintes.

## Camadas

```
            sua aplicação (controllers, services, domains, GSPs, rotas)
 ─────────────────────────────────────────────────────────────────────────
  grox-grails (controllers, UrlMappings)   │  grox-dsl (rotas por closure/anotação)
  grox-grails-plugins (spring-security, asset-pipeline, quartz, console...)
 ─────────────────────────────────────────────────────────────────────────
  grox-gorm      grox-gsp      grox-assets      grox-security(-gorm)
 ─────────────────────────────────────────────────────────────────────────
  grox-core: Netty, pipeline HTTP, sessão, config, atores, services, i18n
 ─────────────────────────────────────────────────────────────────────────
              Groovy 6 (atores, virtual threads)  ·  JDK 27
```

Cada módulo se registra no runtime por `ServiceLoader`. Uma aplicação escolhe os módulos pelas dependências do build: o `framework` traz todos (aplicações Grails), e uma aplicação sem Grails declara só os que usa.

## Fluxo de uma requisição

```
 Netty event loop                  Atores e virtual threads
 ────────────────                  ──────────────────────────────────────────────
 decodifica o HTTP
 HttpPipeline (só CPU) ─────────►  _method / X-HTTP-Method-Override, caminho seguro
                                   segurança (virtual thread): Bearer/Basic/sessão,
                                     login/logout/JWT, CSRF, @Secured, interceptUrlMap
                                   estáticos e assets (virtual thread)
                                   /actuator, /console
                                   rota: UrlMappings → controller   ou   rota do grox-dsl
                                        │
                                        ▼
                                   ator do controller (pool ou um por requisição)
                                     contexto: requisição, usuário, sessão de dados
                                     allowedMethods → interceptors.before → binding → action
                                       service-ator? a chamada vira mensagem e espera
                                     action devolveu Promise? o ator fica livre
                                     view → interceptors.after → GSP + layout SiteMesh
                                     → interceptors.afterView
                                        │
 escreve na ordem de chegada  ◄─────────┘  erro → mapeamento "404"/"500" ou página padrão
 (pipelining, keep-alive)          sessão gravada no fim (memória ou Redis) + cookie
```

O que isso significa para você:

- **O event loop do Netty nunca roda o seu código.** Actions, GSPs, banco e IO de arquivo rodam em atores ou virtual threads. Bloquear dentro de uma action (JDBC, HTTP externo, chamada a um service-ator) é aceitável.
- **O roteador não é um ator:** resolver a rota é uma função pura, sem fila única.
- **As respostas saem na ordem das requisições** de cada conexão, mesmo com pipelining.
- **Erros viram um novo despacho:** `response.sendError(404)` ou uma exceção procuram o mapeamento `"404"`/`"500"` do `UrlMappings`. Sem mapeamento, sai a página de erro padrão (HTML ou JSON, conforme o `Accept`), com stack trace só em development.
- **A sessão HTTP é gravada uma vez, no fim**, e só o que mudou.

## Onde o seu código roda

| Peça | Modelo |
|---|---|
| Controller, modo `pool` (padrão) | N atores por controller, cada um com **a sua instância**. O despacho vai para o ator com menos mensagens pendentes |
| Controller, modo `request` | Um ator e uma instância novos por requisição |
| Rotas do `grox-dsl` | Virtual thread por requisição (padrão) ou pool de atores (`execution: 'actor'`) |
| Service comum | Singleton compartilhado: precisa ser thread-safe |
| Service com `static actor = true` *(extensão Grox)* | Atrás de um ator. Quem injeta recebe um proxy-subclasse; cada chamada espera a resposta. O estado interno dispensa sincronização |
| Service com `@Transactional` | Proxy-subclasse; métodos em transação |
| TagLibs | Singletons; o `out` vem do contexto de renderização |
| Jobs | Uma virtual thread por execução |
| Tarefas `grails.async` | Virtual threads com o contexto de quem as criou |

**Risco de deadlock:** um service-ator A que chama um service-ator B, que chama A de volta, trava. Evite ciclos entre services-ator.

## Contexto da requisição

Em vez de `ThreadLocal`, o Grox guarda o contexto em `ScopedValue` (Java 25+). Isso é transparente para a aplicação: `params`, `springSecurityService.currentUser` e `Tenants.currentId()` funcionam como no Grails. O que muda é o alcance:

| Contexto | Chega a services-ator | Chega a tarefas `grails.async` |
|---|---|---|
| Requisição (`params`, `request`, `session`) | não | sim |
| Renderização GSP | não | não |
| Sessão Hibernate | sim | não: cada tarefa abre a sua |
| Autenticação | sim | sim |
| Tenant forçado (`Tenants.withId`) | sim | sim |

A **sessão Hibernate** é uma por invocação de action, **incluindo a renderização da view** (lazy loading funciona na GSP). Ela é aberta só no primeiro acesso ao banco: actions sem banco não pagam a abertura. Fora de uma action (BootStrap, threads próprias, testes), cada chamada do GORM abre e fecha a própria sessão.

## Descoberta dos artefatos

- **Em runtime:** o pacote da classe passada a `GroxApp.run` (e os listados em `META-INF/grox/packages`, gerado pelos plugins Gradle) é varrido. A classe vira artefato pelo sufixo do nome ou por um marcador: `*Controller`, `*Service`, `*TagLib`, `*Interceptor`, `UrlMappings`, `BootStrap`, `*Job`, domain classes, Data Services, classes `Routes` e `@Handler`.
- **Na compilação:** uma transformação AST global atua pelo **diretório do fonte**. Em `grails-app/controllers`, a classe ganha a API de controller; em `grails-app/domain`, a API do GORM, `id`, `version` e as associações; em `grails-app/services`, `log`; e assim por diante. Uma segunda transformação reescreve os closures de `where { }` em critérios de consulta, em qualquer código.

O seu código não estende classes do framework nem precisa de anotações, como no Grails.

## Ciclo de vida

A subida é feita por fases, cada uma chamada em todos os módulos na ordem deles (HTTP, dados, segurança, views, assets, Grails, plugins):

1. **Ambiente e configuração:** `application.yml`, `application.groovy`, `runtime.groovy`; varredura dos artefatos.
2. **Inicialização:** o Hibernate começa a subir **em paralelo** (só se houver domain classes ou `dataSource`); a segurança é configurada.
3. **Beans:** services (com proxy se forem ator ou transacionais), Data Services, `doWithSpring`, `resources.groovy`; injeção por nome; `@PostConstruct`.
4. **Web:** pipeline HTTP, filtro de segurança, assets, TagLibs e GSP, `UrlMappings`, interceptors, controllers, rotas.
5. **Pronto:** espera o Hibernate, roda o `BootStrap.init` (numa sessão de dados, com `environments { }`), inicia os jobs.
6. **Atende:** o Netty começa a aceitar conexões. O log mostra o tempo de cada fase (`Subida por fase`).

No desligamento (`close()`, também no `SIGTERM`): o `/health` vira `OUT_OF_SERVICE`, o servidor para de aceitar conexões, espera as requisições em andamento terminarem de ser escritas (até `grox.shutdown.timeout`) e, na ordem inversa, para jobs, roda o `BootStrap.destroy`, encerra atores, services, sessões e banco.

A recarga em desenvolvimento usa as mesmas fases para subir uma nova *geração* da aplicação, sem reiniciar a JVM.

## Camada de dados

```
 Livro.groovy (grails-app/domain ou @Entity)
   compilação: API do GORM, Long id/version, coleções do hasMany, construtores () e (Map)
   subida:     metadados (hasMany, belongsTo, mappedBy, mapping, constraints)
               → orm.xml gerado (Jakarta Persistence 3.2) → Hibernate 7.4 + HikariCP
```

As entidades são POGOs sem anotações JPA. Os nomes físicos seguem o GORM (snake_case). `save()` valida, preenche timestamps, chama `beforeInsert`/`beforeUpdate` e participa da transação aberta (ou abre uma).

## Decisões que afetam o seu código

| Decisão | Efeito para a aplicação |
|---|---|
| API do Grails emulada, sem os jars | O código compila igual; recursos não usados por nenhum projeto ainda podem faltar |
| Actions em atores | No modo `pool`, cada ator tem a sua instância do controller: campos de instância não são compartilhados (no Grails o controller é singleton) |
| Só services marcados viram atores | Um service comum é singleton, como no Grails |
| Sessão Hibernate por action, propagada aos services-ator | Lazy loading na view, sem `ThreadLocal`; alterações sem `save()` fora de transação não são gravadas no fim |
| Views adiadas até depois dos interceptors | `after()` pode trocar `model` e `view`, como no Grails |
| CSRF ligado por padrão | Formulários sem `<g:form>` e AJAX precisam do token, ou desligue com `csrf.enabled: false` |
| Multi-tenancy só DISCRIMINATOR, filtro nativo do Hibernate | Vale para HQL, Criteria, finders e carga por id; SQL nativo não é filtrado |
| `dbCreate: update` seguro por padrão | Cria tabelas e colunas, mas nunca altera colunas existentes |
| GSPs pré-compiladas no build | Erro de GSP falha o build; produção não precisa das fontes |
| Ambiente padrão `production` | O artefato empacotado sobe em production; o `run` usa development |

## Veja também

- [Concorrência: atores e virtual threads](../recursos/concorrencia.md)
- [Sessões, transações e eventos](../recursos/transacoes.md)
- [O que muda em relação ao Grails](../migracao/diferencas.md)
- [Visão geral dos módulos](../sem-grails/visao-geral.md)
