# Visão geral da migração

Este capítulo abre a trilha de migração: o que significa migrar uma aplicação Grails para o Grox, o que costuma funcionar sem alterações, o que exige trabalho e como organizar o processo.

## A ideia

O Grox emula a API pública do Grails com **as mesmas classes nos mesmos pacotes** (`grails.converters.JSON`, `grails.gorm.transactions.Transactional`, `grails.plugin.springsecurity.SpringSecurityService`, `org.springframework.http.HttpStatus`...) e lê **as mesmas chaves de configuração** (`dataSource`, `grails.plugin.springsecurity.*`, `grails.gorm.multiTenancy.*`, `grails.sitemesh.default.layout`...). Por isso a migração não é uma reescrita: o objetivo é **compilar e rodar o código existente**, mudando o build e, se preciso, poucos arquivos.

Nos projetos já migrados, o código de controllers, services, domain classes, GSPs, TagLibs e assets compilou sem mudanças. As alterações necessárias foram:

- o `build.gradle` (plugin `org.grox.app` no lugar do plugin Grails);
- bibliotecas incompatíveis com o Groovy 6 trocadas (por exemplo o HTTPBuilder 0.8);
- ajustes de configuração (CSRF, sessão no Redis, credenciais por variáveis de ambiente);
- código que já estava quebrado no Grails moderno (APIs removidas no Hibernate 6+).

## O que costuma funcionar direto

| Área | Situação |
|---|---|
| Layout `grails-app/`, `Application`, `BootStrap`, `UrlMappings`, `application.yml`/`.groovy` | ✅ |
| Controllers: `params`, `render`, `respond`, `redirect`, `withFormat`, command objects, `allowedMethods`, interceptors | ✅ |
| Services, injeção por nome, `@Transactional`, Data Services | ✅ |
| GORM: domain classes, finders, `where`, `createCriteria`, HQL, constraints, associações, eventos, `isDirty` | ✅ |
| GSP, TagLibs, layouts, `g:` usuais, i18n | ✅ |
| spring-security-core e rest: `interceptUrlMap`, `@Secured`, form login, JWT, tokens em GORM, `chainMap` | ✅ |
| Multi-tenancy DISCRIMINATOR | ✅ |
| Asset pipeline (sem pré-processadores) | ✅ |
| Plugins console, quartz, excel-export, redis, rabbitmq-native (publisher), rendering, audit-logging, hibernate-filter | ✅ no que foi usado |

## O que exige atenção

| Área | Por quê |
|---|---|
| Bibliotecas Groovy compiladas com Groovy ≤ 5 | Falham com `NoClassDefFoundError: .../callsite/CallSite`; recompilar ou trocar |
| Plugins não reimplementados | Precisam de substituto ou de reimplementação no Grox |
| CSRF | Ligado por padrão no Grox; desligado no spring-security-core |
| `dbCreate: update` contra banco existente | O Grox só adiciona (modo safe); confira o DDL antes com `grox.schema.updateScript` |
| Sessão do spring-session | Não é lida: troque por `grox.session` (memória ou Redis) |
| Estado em campos de controller | No modo `pool` cada ator tem a sua instância |
| Recursos do Spring além dos contratos simples | Sem Spring: AOP, `@Configuration`, filtros Servlet não existem |
| Testes do projeto (Spock + grails-testing) | Não rodam sobre o Grox; os testes de integração passam a ser HTTP de ponta a ponta |

## Como organizar

A forma usada nos candidatos (e recomendada) mantém o repositório da aplicação como **fonte** e deixa as mudanças explícitas:

```
candidates/minha-app/
  build.gradle        build sobre o Grox
  import.sh           copia grails-app/ e src/ do repositório real e aplica overrides e patches
  overrides/          arquivos que substituem ou se somam aos importados (application.yml sem credenciais...)
  patches/            alterações mínimas no código, um patch por arquivo
  make-patches.sh     regera os patches depois de editar o código importado
  run-h2.sh           sobe em development com H2 em memória
  run-mysql.sh        sobe contra o banco real (credenciais só no ambiente)
```

O código importado não é versionado no repositório do Grox. Os patches mostram exatamente o que teria de mudar no projeto real; a meta é zerá-los, levando a compatibilidade para o framework.

## As etapas

1. **Levantamento:** tamanho, plugins, bibliotecas, recursos do GORM e da web usados.
2. **Build:** `build.gradle` com o plugin `org.grox.app`, dependências da aplicação.
3. **Importar e compilar:** Groovy, GSPs (`compileGsp`) e assets (`assetCompile`).
4. **Subir com H2:** banco em memória, dados mínimos de teste, jobs desligados.
5. **Navegar:** varredura das páginas logado e anônimo; separar erros do Grox de erros da aplicação.
6. **Banco real:** `dbCreate: none`, conferir o DDL pendente, comparar páginas.
7. **Infra:** sessão no Redis, tokens, jobs, integrações externas.
8. **Produção:** `installDist`, AOT cache, variáveis de ambiente.

O [Passo a passo](passo-a-passo.md) detalha cada etapa.

## Veja também

- [Passo a passo](passo-a-passo.md)
- [O que muda em relação ao Grails](diferencas.md)
- [Plugins do Grails](plugins.md)
- [Casos reais: apps A, B e C](casos-reais.md)
- [Compatibilidade com o Grails](../referencia/compatibilidade.md)
