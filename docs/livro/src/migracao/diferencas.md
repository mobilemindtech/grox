# O que muda em relação ao Grails

Este capítulo lista as diferenças de comportamento entre o Grox e o Grails: as intencionais (decisões do projeto) e as que vêm da ausência do Spring e do container Servlet. Leia antes de migrar: a maioria das surpresas está aqui.

## Execução e concorrência

| Assunto | Grails | Grox |
|---|---|---|
| Onde a action roda | Thread do Tomcat | Ator do Groovy 6, em virtual thread |
| Instância do controller | Singleton (padrão) | Modo `pool` (padrão): **uma instância por ator**, N atores por controller. Modo `request`: uma instância por requisição |
| Campos de instância no controller | Compartilhados entre requisições | Não compartilhados entre atores; não guarde estado neles |
| Requisições simultâneas por controller | Limitadas pelo pool do Tomcat | Limitadas por `poolSize` (padrão 2 × CPUs) no modo `pool`. Uma action que espera muito segura o ator: use actions assíncronas |
| Services | Singletons | Singletons, ou atores com `static actor = true` *(extensão Grox)*: chamadas serializadas, estado sem sincronização |

```groovy
class RelatorioController {
    static concurrency = 'request'   // ou 'pool' (padrão da app: grox.controllers.concurrency)
    static poolSize = 4              // atores no modo pool
}
```

## Sessão Hibernate e transações

- **Uma sessão por action**, aberta no primeiro acesso ao banco e mantida até o fim da renderização da view (open session in view). Ela vai junto para os services-ator.
- **Alterações sem `save()`** em entidades carregadas, fora de uma transação, **não são gravadas no fim da requisição**. Chame `save()` ou altere dentro de `withTransaction`/service `@Transactional`.
- **Validação que falhou:** a entidade inválida fica somente leitura e não é gravada por um commit posterior. Chame `discard()` antes de continuar se outra transação da mesma requisição vier depois.
- **Fora de actions** (BootStrap, jobs, threads próprias, testes), cada chamada do GORM abre e fecha a sua sessão; agrupe com `withTransaction` ou `withNewSession`.
- **Tarefas assíncronas** (`task { }`) têm sessão de dados própria: entidades carregadas na action ficam desanexadas dentro delas.
- **Eventos e auditoria** valem também para o que só o flush grava (alteração por dirty checking, filho salvo por cascata), como no GORM: `beforeUpdate`, os listeners `Pre*` e o audit log rodam no flush, e o que já passou pelo `save()` não dispara de novo.
- **`embedded`:** as propriedades do componente são opcionais por padrão (declare `nullable: false` nas constraints do componente para exigir); os erros ficam no campo `endereco.rua` do dono. Componente dentro de componente e associações no componente não são suportados.

## Sem container Servlet

`request`, `response` e `session` são objetos próprios que implementam as interfaces completas do `jakarta.servlet-api` 6.1, então código e bibliotecas que recebem `HttpServletRequest` funcionam:

| Funciona | Lança `UnsupportedOperationException` | Comportamento próprio |
|---|---|---|
| Parâmetros, cabeçalhos (`getDateHeader`, `getIntHeader`), locale pelo `Accept-Language`, corpo (`inputStream`, `reader`), cookies, atributos, sessão | `getServletContext`, `startAsync`, `getRequestDispatcher`, `getParts`, `login`/`logout`/`authenticate`, `upgrade` | `getUserPrincipal`/`getRemoteUser` devolvem o usuário da segurança do Grox; `isUserInRole` é sempre `false` (use `springSecurityService` ou `SpringSecurityUtils.ifAllGranted`) |

- O `servletContext` do `BootStrap.init` é `null`.
- Não há filtros Servlet, `web.xml`, `doWithWebDescriptor` nem `FilterRegistrationBean`.
- O context path é sempre a raiz.

## Sem Spring

Não há contêiner Spring. A injeção é **por nome de propriedade**, como no Grails, sem qualifiers, sem injeção por construtor e sem escopos (tudo é singleton, exceto controllers no modo `request`). Existem equivalentes dos contratos simples que as aplicações usam:

- `@Autowired`, `@Lazy`, `MessageSource`, `LocaleContextHolder`, `RequestContextHolder`, `HttpStatus`;
- `PlatformTransactionManager`, `@Transactional` do Spring (com `propagation`), `TransactionAspectSupport.currentTransactionStatus()`;
- `ApplicationListener`, `ApplicationEvent`, `applicationContext.publishEvent` (síncrono);
- do Spring Security: `PasswordEncoder` (e os encoders usuais), `UserDetailsService`, `UserDetails`, `SecurityContextHolder`, exceções de conta.

O `resources.groovy` aceita o subconjunto do BeanBuilder usado na prática (`nome(Classe, args) { prop = ref('x') }`); o resto do DSL do Spring não existe.

## Segurança

| Assunto | spring-security-core | Grox |
|---|---|---|
| CSRF | Não existe | **Ligado por padrão** (`csrf.enabled: true`). `<g:form>` inclui o token |
| `@Secured` e `interceptUrlMap` | Modos exclusivos (`securityConfigType`) | Os dois juntos: `@Secured` primeiro, depois a primeira regra do `interceptUrlMap`, depois `staticRules` |
| `/admin.json`, `/admin/` | Podem escapar de `/admin/**` | Avaliados também pelo caminho canônico da action |
| Caminhos com `.` ou `..` | | 400 |
| Limite de tentativas de login | Não | Sim *(extensão Grox)* |
| Cabeçalhos de segurança | Do Spring Security | `nosniff`, `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy` *(extensão Grox)* |
| `authentication` sem login | `AnonymousAuthenticationToken` | `null` (`principal` é `'anonymousUser'`, como no Spring) |
| Expressão de acesso inválida | Erro na requisição | Impede a aplicação de subir |
| `LoginController`/`LogoutController` | Do plugin | Embutidos, se a aplicação não tiver os seus |

## Ambiente

- **Padrão `production`**, como no Grails: o artefato empacotado (`installDist`, jar) sobe em production. O plugin Gradle define `development` no `run` e `test` nos testes.
- O ambiente vem de `-Dgrails.env`, `-Dgrox.env`, `GRAILS_ENV` ou `GROX_ENV` (aceita `dev` e `prod`); sem eles, `-Dgrox.env.default` e por fim `production`.
- Fora de development, páginas e JSON de erro não mostram a mensagem da exceção, e o `/health` omite a mensagem de falha do banco ou do Redis (ficam no log).

## Banco

- **`dbCreate: update` seguro:** só cria tabelas, colunas, FKs e índices; nunca altera colunas existentes (`grox.schema.update: safe`). O Hibernate 7 puro faz `alter table ... modify column` quando o mapeamento difere (`full`).
- **Proxies lazy só onde usados** (`grox.data.proxies: auto`): `load(id)` devolve proxy só para entidades alvo de many-to-one; as demais carregam na hora. `all` restaura o comportamento do GORM.
- **`hibernate.default_batch_fetch_size: 16`** por padrão (contra N+1 em listas).
- **SQL nativo não é filtrado por tenant** (`createNativeQuery`, `groovy.sql.Sql`), como no GORM: inclua o `tenant_id` na consulta.
- **HQL legado:** `?0` (base zero) e `?` sem número são renumerados para `?1, ?2...`; `GString` vira `String` nos parâmetros.

## Web e views

- **Views adiadas:** `render(view:)`, `respond` em HTML e a view padrão são renderizados depois dos `after()` dos interceptors, que podem trocar `model` e `view`. Templates e `render text:` saem na hora.
- **`render { }` sem `contentType`** gera HTML pelo `MarkupBuilder` (como no Grails).
- **`render x as JSON` fecha o writer**, como no Grails: um segundo render na mesma resposta é descartado.
- **Parâmetro simples inválido** (`show(Long id)` com `id=abc`) vira `null` sem erro.
- **Transients e propriedades `def`** ainda são vinculados no binding (o Grails não vincula por padrão): proteja campos com `bindable: false`.
- **SiteMesh 3** no lugar do 2: a API das tags de layout é a mesma, e `<sitemesh:write>` também funciona.
- **Asset pipeline sem pré-processadores** (SASS, LESS, Babel) nem minificação.
- **Sessão:** cookie `GROXSESSIONID` (configurável), gravação no fim da requisição, só do que mudou.

## Desenvolvimento

- **Recarga de classes** própria (sem spring-loaded nem devtools): leve para controllers, services, TagLibs; completa para domain classes e `src/`. O BootStrap não roda de novo na leve.
- **Testes:** os testes Spock com `grails-testing` não rodam. Os testes de integração sobem a aplicação numa porta aleatória e usam HTTP; o GORM pode ser usado direto no teste.
- **`@GrailsCompileStatic` e `@GrailsTypeChecked`** são aceitos, mas a classe compila de forma dinâmica.

## Veja também

- [Concorrência: atores e virtual threads](../recursos/concorrencia.md)
- [Sessões, transações e eventos](../recursos/transacoes.md)
- [Segurança: CSRF e proteções](../recursos/seguranca-protecoes.md)
- [Ambientes](../referencia/ambientes.md)
- [Compatibilidade com o Grails](../referencia/compatibilidade.md)
