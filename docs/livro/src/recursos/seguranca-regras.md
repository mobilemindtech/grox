# Segurança: regras de acesso

Este capítulo cobre a autorização: como ativar a segurança, as regras por caminho (`interceptUrlMap`, `staticRules`), a anotação `@Secured`, as expressões de acesso, os padrões de caminho, a cadeia de filtros (`filterChain.chainMap`) e o que acontece quando o acesso é negado.

A segurança do Grox usa **as mesmas chaves e a mesma API** do spring-security-core e do spring-security-rest do Grails, sem o Spring. Roda como uma etapa assíncrona do atendimento, fora do event loop do Netty.

## Ativação

A segurança fica ativa quando existe o bloco `grails.plugin.springsecurity` na configuração (yml, `application.groovy` ou o bloco `security { }` da [DSL de projeto](../sem-grails/dsl-projeto.md)). `grails.plugin.springsecurity.active: false` desliga.

```yaml
grails:
  plugin:
    springsecurity:
      rejectIfNoRule: true                    # sem regra = negado (padrão)
      userLookup:
        userDomainClassName: sample.auth.User
        authorityJoinClassName: sample.auth.UserRole
      authority:
        className: sample.auth.Role
      interceptUrlMap:
        - {pattern: '/', access: [permitAll]}
        - {pattern: '/static/**', access: [permitAll]}
        - {pattern: '/assets/**', access: [permitAll]}
        - {pattern: '/api/**', httpMethod: GET, access: ['isAuthenticated()']}
        - {pattern: '/api/**', access: [ROLE_ADMIN]}
        - {pattern: '/admin/**', access: ["hasRole('ADMIN') and isFullyAuthenticated()"]}
        - {pattern: '/**', access: ['isAuthenticated()']}
```

## Ordem de avaliação

1. `@Secured` da **action**; depois `@Secured` do **controller** (nas rotas do `grox-dsl`, o `access:` da rota, do grupo ou do `@Handler`).
2. Senão, a **primeira** regra de `interceptUrlMap` que casar com o caminho e o método.
3. Depois, as de `controllerAnnotations.staticRules`.
4. Depois, as regras do `security { rule ... }` da DSL de projeto.
5. Nenhuma casou: vale `rejectIfNoRule` (padrão `true`, negado).

A anotação vem antes das regras de URL por ser mais específica. No plugin do Grails, os modos (`Annotation`, `InterceptUrlMap`) são exclusivos; no Grox, valem juntos nessa ordem, e `securityConfigType` é ignorado.

As URLs do próprio login (`/login/auth`, `/login/authenticate`, `/logoff`, `/api/login` e, sem controller próprio, `/logout`) são sempre liberadas.

## `@Secured`

```groovy
import grails.plugin.springsecurity.annotation.Secured

@Secured('isAuthenticated()')
class PedidoController {
    def index() { ... }

    @Secured('ROLE_ADMIN')
    def delete(Long id) { ... }

    @Secured(value = ['ROLE_A', 'ROLE_B'], httpMethod = 'POST')
    def aprovar(Long id) { ... }
}
```

`@Secured(closure = { ... })` não é suportado.

## `staticRules`

No formato que o `s2-quickstart` gera no `application.groovy`:

```groovy
// grails-app/conf/application.groovy
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    [pattern: '/', access: ['permitAll']],
    [pattern: '/api/**', access: ['ROLE_ADMIN']],
    [pattern: '/relatorios/**', httpMethod: 'GET', access: ['isAuthenticated()']]
]
```

Também é aceita a forma antiga de mapa: `['/caminho': ['regra']]`.

## Expressões de acesso

`access` é uma lista que concede se **qualquer** item conceder.

| Item | Concede quando |
|---|---|
| `permitAll` | sempre |
| `denyAll` | nunca |
| `isAuthenticated()` | há usuário autenticado |
| `isFullyAuthenticated()` | autenticado (não há remember-me no Grox) |
| `isAnonymous()` | não autenticado |
| `ROLE_X` | o usuário tem a role |
| `hasRole('X')` | com ou sem o prefixo `ROLE_` |
| `hasAnyRole('A', 'B')`, `hasAuthority('X')`, `hasAnyAuthority(...)` | |
| `IS_AUTHENTICATED_ANONYMOUSLY`, `IS_AUTHENTICATED_REMEMBERED`, `IS_AUTHENTICATED_FULLY` | os legados |
| `and`, `or`, `!`, parênteses | combinações (`"hasRole('ADMIN') and isFullyAuthenticated()"`) |

Uma expressão inválida **impede a aplicação de subir**, com a regra no erro.

## Padrões de caminho

- Padrões Ant: `/admin/**` casa com `/admin` e tudo abaixo; `*` casa com um segmento; `?` com um caractere.
- `httpMethod` restringe a regra a um método. No `@Secured` da action, nos outros métodos vale o `@Secured` do controller.
- **`<g:actionSubmit>`:** o `_action_X` do formulário escolhe a action antes da segurança, e o `@Secured` avaliado é o da action que vai rodar. O `interceptUrlMap` continua casando pelo caminho da URL, como no plugin. No `@Secured` da action, nos outros métodos vale o `@Secured` do controller.
- **`<g:actionSubmit>`:** o `_action_X` do formulário escolhe a action antes da segurança, e o `@Secured` avaliado é o da action que vai rodar. O `interceptUrlMap` continua casando pelo caminho da URL, como no plugin.
- **Caminho canônico das actions** *(extensão Grox)*: `/admin.json` e `/admin/` roteiam como `/admin`. Numa action, vale a regra que vier primeiro entre a do caminho pedido e a do caminho sem formato e sem barra final. Assim `/admin.json` não escapa de `/admin/**` caindo no `/**` (no Grails, o `AntPathMatcher` deixa essa brecha), e uma regra explícita para `/x.json` continua valendo.
- Caminhos com segmentos `.` ou `..` recebem **400**, para não contornar as regras.

## Caminhos sem estado

```yaml
grails.plugin.springsecurity.stateless: ['/api/**']   # (extensão Grox)
```

Num caminho stateless não há sessão nem CSRF: só Bearer ou Basic autenticam.

## Cadeia de filtros (`filterChain.chainMap`)

### Sem segurança nenhuma

Para não pagar o custo da cadeia (nem a leitura da sessão) em arquivos:

```yaml
grails:
  plugin:
    springsecurity:
      filterChain:
        chainMap:
          - {pattern: '/public/**', filters: 'none'}
```

Ficam sempre fora da cadeia, sem configuração:

- `/assets/**`, como no Grails, em que o filtro do asset-pipeline roda antes do Spring Security;
- estáticos (`/static/**`) em GET/HEAD **cuja regra libera anônimos** (`permitAll`): o arquivo não depende de quem está logado. Um estático protegido por regra continua passando pela segurança.

### Listas de filtros

Como no spring-security-core/rest, cada entrada pode listar filtros (`'JOINED_FILTERS,-x,-y'` ou nomes explícitos). Vale a **primeira** entrada que casar com o caminho; sem `chainMap`, ou sem entrada que case, a cadeia é a completa. O Grox não tem os filtros em si, mas cada um corresponde a uma etapa:

| Filtro | Sem ele |
|---|---|
| `securityContextPersistenceFilter` | sem sessão (como `stateless`: sem login pela sessão nem CSRF) |
| `restTokenValidationFilter` | o token REST é ignorado (a requisição segue como anônima) |
| `basicAuthenticationFilter` | o HTTP Basic é ignorado |
| `anonymousAuthenticationFilter` | quem não se autenticou recebe 401 (ou vai para o login), mesmo em regra `permitAll` |
| `filterInvocationInterceptor` | sem autorização (nem `interceptUrlMap`, nem `@Secured`) |
| `exceptionTranslationFilter` | sem redirecionar para o login: 401 |

`rememberMeAuthenticationFilter`, `logoutFilter` e `authenticationProcessingFilter` são aceitos e não mudam nada.

Exemplo típico do spring-security-rest:

```yaml
filterChain:
  chainMap:
    - {pattern: '/api/**', filters: 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter'}
    - {pattern: '/**', filters: 'JOINED_FILTERS,-restTokenValidationFilter,-restExceptionTranslationFilter'}
```

`/api/**` só aceita token (401 sem ele, antes da action) e não usa sessão; o resto do site usa a sessão e ignora tokens. As URLs de login funcionam em qualquer cadeia.

O console (`/console`) e o actuator (`/actuator/**`) conferem o próprio acesso (`grox.console.access`, `grox.management.metricsAccess`) mesmo numa cadeia sem `filterInvocationInterceptor` ou com `filters: 'none'`.

## Quando o acesso é negado

| Situação | Resposta |
|---|---|
| Não autenticado, vindo de navegador | Redirect para o login; a URL pedida é salva e retomada depois do login |
| Não autenticado em API, XHR, JSON ou caminho stateless | `401` com `WWW-Authenticate` |
| Autenticado sem a role | `403`, pelo mapeamento `"403"` do UrlMappings se existir |
| Classe `MultiTenant` sem tenant | `403` |
| Caminho com `.` ou `..` | `400` |

A URL salva não aceita destinos externos (`//evil.com`): não há open redirect pelo login.

## Com rotas do `grox-dsl`

`access:` na rota, no `group` ou em `@Handler(access = ...)`/`@Get(access = ...)` vale como um `@Secured`, antes das regras de URL:

```groovy
get('/app/admin', access: 'ROLE_ADMIN') { render text: 'só admin' }
```

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| `@Secured` e `interceptUrlMap` juntos | Sim, nessa ordem (no plugin são exclusivos) |
| `@Secured(closure = ...)` | ❌ |
| Hierarquia de roles, grupos (`useRoleGroups`), ACL | ❌ |
| `/admin.json` e `/admin/` | Avaliados também pelo caminho canônico |
| `stateless` | *(extensão Grox)* |
| Remember-me, switch user | ❌ |

## Veja também

- [Segurança: autenticação e usuários](seguranca-autenticacao.md)
- [Segurança: REST, JWT e tokens](seguranca-rest.md)
- [Segurança: CSRF e proteções](seguranca-protecoes.md)
- [Segurança sem o plugin](../sem-grails/seguranca.md)
- [Segurança: checklist](../recomendacoes/seguranca.md)
