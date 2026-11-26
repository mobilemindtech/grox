# Quando usar (e quando não usar)

Este capítulo ajuda a decidir se o Grox serve para o seu projeto, e qual das duas trilhas seguir.

## Bons casos

| Situação | Por quê |
|---|---|
| Aplicação Grails que precisa ir para o Groovy 6 ou para um JDK novo | Os jars do Grails não rodam no Groovy 6; o Grox roda o mesmo código |
| Aplicação Grails "clássica": controllers + GSP + GORM/Hibernate + spring-security | É o perfil dos projetos já migrados, e a cobertura da API é a maior nessa área |
| API REST com spring-security-rest (JWT ou token em GORM) | `/api/login`, refresh, logout com revogação, `X-Auth-Token` e `chainMap` estão implementados |
| SaaS multi-tenant com discriminador (`MultiTenant`, tenant do usuário logado) | O filtro nativo do Hibernate cobre todas as consultas; o tenant vem do usuário sem anotações |
| Equipe que quer subir e recarregar rápido em desenvolvimento | Recarga leve de artefatos em até 1 s, sem reiniciar |
| Projeto novo pequeno ou médio, em Groovy, sem Spring | Trilha "sem Grails": só os módulos necessários, rotas por closure/anotação, mesmo GORM e GSP |
| Serviço com estado concorrente | Services-ator (`static actor = true`) serializam o acesso ao estado sem `synchronized` |

## Casos que pedem cuidado

| Situação | O que avaliar |
|---|---|
| Uso intenso de plugins do Grails | Só parte dos plugins foi reimplementada, e só no que os projetos usavam (veja [Plugins do Grails](../migracao/plugins.md)). Um plugin ausente precisa ser reimplementado ou substituído |
| Recursos do Spring além da API comum (AOP, perfis, `@Configuration`, Spring Boot starters) | Não há Spring. Só contratos simples têm equivalente (`@Autowired`, `MessageSource`, `PlatformTransactionManager`, `PasswordEncoder`...) |
| Bibliotecas Groovy de terceiros compiladas com Groovy 5 ou anterior | Costumam falhar com `NoClassDefFoundError: .../callsite/CallSite`. É preciso recompilar os fontes ou trocar a biblioteca |
| Filtros Servlet, `web.xml`, `startAsync`, WebSocket, SSE | Sem container de Servlet; streaming e SSE estão no roadmap |
| Herança de domain classes, `embedded`, named queries, vários datasources, MongoDB | Ainda não suportados |
| Multi-tenancy DATABASE ou SCHEMA | Só DISCRIMINATOR |
| Asset pipeline com SASS, LESS, Babel, minificação | O pipeline do Grox não tem pré-processadores nem minificação |
| Produção crítica imediata | O Grox é um protótipo: valide com carga e com o seu banco antes |

## Quando não usar

- A aplicação depende de algo do Spring que não tem equivalente e não pode ser trocado.
- O projeto não usa Groovy (o Grox é Groovy dinâmico por design).
- Você precisa de um ecossistema grande de plugins prontos.

## Qual trilha seguir

```
Tenho uma aplicação Grails?
├─ sim → Trilha 1: migrar um projeto Grails
│        (layout grails-app/, plugin Gradle org.grox.app, dependência do framework inteiro)
└─ não → Quero as convenções do Grails (controllers, UrlMappings, grails-app/)?
         ├─ sim → crie no layout Grails, como o sample-app (guias por recurso)
         └─ não → Trilha 2: usar sem Grails
                  (core + gorm + gsp + security + grox-dsl, como o sample-plain)
```

Uma aplicação Grails também pode ter rotas do `grox-dsl` ao lado dos controllers: os controllers são consultados primeiro.

## Veja também

- [O que é o Grox](o-que-e.md)
- [Visão geral da migração](../migracao/visao-geral.md)
- [Visão geral dos módulos](../sem-grails/visao-geral.md)
- [Compatibilidade com o Grails](../referencia/compatibilidade.md)
