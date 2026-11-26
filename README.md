# Grox

[![CI](https://github.com/mobilemindtech/grox/actions/workflows/ci.yml/badge.svg)](https://github.com/mobilemindtech/grox/actions/workflows/ci.yml)
[![Documentação](https://github.com/mobilemindtech/grox/actions/workflows/pages.yml/badge.svg)](https://mobilemindtech.github.io/grox/)

Framework web leve sobre os atores do Groovy 6 (`groovy.concurrent.Actor`) que **emula a API do Grails** sem Spring: controllers, services, domain classes (GORM), GSP, TagLibs, UrlMappings, interceptors e os plugins mais usados funcionam com o código de uma app Grails, sem os jars do Grails.

- **HTTP:** Netty 4.2, sem servlet container; nada bloqueante no event loop.
- **Atores:** cada controller e service roda num ator; estado de requisição, sessão de dados, autenticação e tenant em `ScopedValue`.
- **Dados:** GORM próprio sobre Hibernate 7.4 e HikariCP (domain classes, dynamic finders, `where { }`, criteria, HQL, Data Services, multi-tenancy).
- **Views:** motor GSP próprio, layouts SiteMesh 3, asset pipeline; também Pug e Markdown.
- **Segurança:** no estilo spring-security-core e spring-security-rest (form login, JWT, `@Secured`, CSRF).
- **Sem Grails:** os módulos `grox-core`, `grox-gorm`, `grox-gsp` e `grox-security` também funcionam sem o layout `grails-app/`, com rotas por DSL ou anotação (`grox-dsl`).

> **Status:** protótipo. Já roda aplicações Grails 8 reais, mas a API pode mudar e há lacunas conhecidas (ver a [matriz de compatibilidade](https://mobilemindtech.github.io/grox/referencia/compatibilidade.html)).

## Documentação

O livro do Grox, em português, está em **https://mobilemindtech.github.io/grox/**. Ele cobre a migração de apps Grails, o uso sem Grails e a referência. A fonte está em [`docs/livro/`](docs/livro/src/SUMMARY.md).

As notas internas de arquitetura e desenvolvimento estão em [`docs/`](docs/README.md).

## Início rápido

Requisitos: um JDK recente para o Gradle (o JDK 27 do build é baixado pelo toolchain). Redis é opcional.

```bash
./gradlew build                  # compila e roda todos os testes
./gradlew :sample-app:run        # exemplo no layout Grails: http://localhost:8080 (admin/admin)
./gradlew :sample-plain:run      # exemplo sem Grails: http://localhost:8080
docs/livro/build.sh serve        # livro em http://localhost:3000
```

## Estrutura

| Diretório | Conteúdo |
|---|---|
| `framework/` | Os módulos `grox-*` (core, gorm, gsp, assets, security, grails, plugins, dsl, pug, markdown) e os testes |
| `gradle-plugin/` | Plugins Gradle `org.grox.app` e `org.grox.lib` |
| `sample-app/` | App de exemplo no layout Grails, também a suíte de integração |
| `sample-plain/` | App de exemplo sem Grails |
| `sample-lib/` | Biblioteca de artefatos usada pelo `sample-app` |
| `bench/` | Gerador de carga e a app de referência Grails x Grox |
| `docs/` | Livro (mdBook) e notas internas |
